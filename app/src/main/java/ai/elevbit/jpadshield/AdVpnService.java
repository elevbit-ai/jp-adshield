/*
 * JP AdShield - AdVpnService
 * Criado por Joaquim Pedro de Morais Filho - j360074@hotmail.com
 *
 * VPN local (sem root) que filtra o DNS do aparelho inteiro.
 * O sistema envia todas as consultas DNS para um endereco interno
 * (10.111.222.3); este servico le cada consulta, descobre o dominio e:
 *   - se for de anuncio/rastreador -> responde NXDOMAIN (bloqueia);
 *   - senao -> encaminha ao DNS real (Cloudflare 1.1.1.1) e devolve a resposta.
 * Nenhum outro trafego passa pela VPN (so o DNS), entao e leve e nao
 * inspeciona o conteudo que voce acessa.
 */
package ai.elevbit.jpadshield;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class AdVpnService extends VpnService implements Runnable {

    public static final String ACTION_CONNECT = "ai.elevbit.jpadshield.CONNECT";
    public static final String ACTION_DISCONNECT = "ai.elevbit.jpadshield.DISCONNECT";

    public static volatile boolean running = false;
    public static final AtomicLong blockedCount = new AtomicLong(0);
    public static final AtomicLong allowedCount = new AtomicLong(0);

    private static final String VPN_ADDR = "10.111.222.2";
    private static final String DNS_PROXY = "10.111.222.3"; // DNS "falso" que o sistema enxerga
    private static final String UPSTREAM = "1.1.1.1";       // DNS real para onde encaminhamos
    private static final String UPSTREAM2 = "8.8.8.8";
    private static final int MTU = 1500;
    private static final String CH_ID = "jpadshield";
    private static final int NOTIF_ID = 7;

    private ParcelFileDescriptor vpn;
    private Thread worker;
    private FileOutputStream out;
    private final Object writeLock = new Object();
    private ExecutorService pool;
    private InetAddress upstreamAddr, upstream2Addr;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        Blocklist.load(this);
        try { upstreamAddr = InetAddress.getByName(UPSTREAM); } catch (Exception e) {}
        try { upstream2Addr = InetAddress.getByName(UPSTREAM2); } catch (Exception e) {}
        iniciarForeground();
        running = true;
        pool = Executors.newFixedThreadPool(8);
        worker = new Thread(this, "jpadshield-vpn");
        worker.start();
        return START_STICKY;
    }

    @Override
    public void run() {
        try {
            Builder b = new Builder();
            b.setSession("JP AdShield");
            b.addAddress(VPN_ADDR, 32);
            b.addDnsServer(DNS_PROXY);
            b.addRoute(DNS_PROXY, 32);
            b.setMtu(MTU);
            if (Build.VERSION.SDK_INT >= 21) {
                try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}
            }
            vpn = b.establish();
            if (vpn == null) { stopVpn(); return; }

            FileInputStream in = new FileInputStream(vpn.getFileDescriptor());
            out = new FileOutputStream(vpn.getFileDescriptor());
            byte[] packet = new byte[32767];

            while (running) {
                int len = in.read(packet);
                if (len <= 0) continue;
                try { handlePacket(packet, len); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // encerrado
        } finally {
            stopVpn();
        }
    }

    private void handlePacket(byte[] p, int len) {
        if (len < 28) return;
        int version = (p[0] & 0xF0) >> 4;
        if (version != 4) return;                 // so IPv4 nesta versao
        int ihl = (p[0] & 0x0F) * 4;
        if (ihl < 20 || len < ihl + 8) return;
        int proto = p[9] & 0xFF;
        if (proto != 17) return;                  // so UDP
        int udp = ihl;
        int dstPort = ((p[udp + 2] & 0xFF) << 8) | (p[udp + 3] & 0xFF);
        if (dstPort != 53) return;                // so DNS

        byte[] srcIp = {p[12], p[13], p[14], p[15]};
        byte[] dstIp = {p[16], p[17], p[18], p[19]};
        int srcPort = ((p[udp] & 0xFF) << 8) | (p[udp + 1] & 0xFF);
        int udpLen = ((p[udp + 4] & 0xFF) << 8) | (p[udp + 5] & 0xFF);
        int dnsOff = udp + 8;
        int dnsLen = udpLen - 8;
        if (dnsLen < 12 || dnsOff + dnsLen > len) return;

        String domain = parseQuestionName(p, dnsOff);

        if (domain != null && Blocklist.isBlocked(domain)) {
            blockedCount.incrementAndGet();
            byte[] resp = buildBlockedDns(p, dnsOff, dnsLen);
            if (resp != null) writePacket(buildIpv4Udp(dstIp, srcIp, 53, srcPort, resp));
        } else {
            allowedCount.incrementAndGet();
            final byte[] query = new byte[dnsLen];
            System.arraycopy(p, dnsOff, query, 0, dnsLen);
            final byte[] fSrcIp = srcIp, fDstIp = dstIp;
            final int fSrcPort = srcPort;
            pool.execute(() -> forward(query, fSrcIp, fDstIp, fSrcPort));
        }
    }

    private void forward(byte[] query, byte[] srcIp, byte[] dstIp, int srcPort) {
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            protect(sock);
            sock.setSoTimeout(5000);
            InetAddress up = (upstreamAddr != null) ? upstreamAddr : upstream2Addr;
            sock.send(new DatagramPacket(query, query.length, new InetSocketAddress(up, 53)));
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            byte[] answer = new byte[resp.getLength()];
            System.arraycopy(buf, 0, answer, 0, resp.getLength());
            // devolve ao app: origem = DNS consultado (dstIp), destino = app (srcIp)
            writePacket(buildIpv4Udp(dstIp, srcIp, 53, srcPort, answer));
        } catch (Exception ignored) {
        } finally {
            if (sock != null) sock.close();
        }
    }

    private void writePacket(byte[] pkt) {
        if (pkt == null || out == null) return;
        try { synchronized (writeLock) { out.write(pkt); } } catch (Exception ignored) {}
    }

    // ---------- DNS ----------

    /** Le o QNAME da pergunta e retorna o dominio (ex.: "ads.exemplo.com"). */
    private String parseQuestionName(byte[] p, int dnsOff) {
        try {
            int i = dnsOff + 12; // pula o cabecalho DNS (12 bytes)
            StringBuilder sb = new StringBuilder();
            int guard = 0;
            while (guard++ < 128) {
                int lenByte = p[i] & 0xFF;
                if (lenByte == 0) break;
                if ((lenByte & 0xC0) != 0) break; // sem ponteiros na pergunta
                i++;
                if (sb.length() > 0) sb.append('.');
                for (int k = 0; k < lenByte; k++) {
                    sb.append((char) (p[i + k] & 0xFF));
                }
                i += lenByte;
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Fim da secao de pergunta (apos QNAME + QTYPE + QCLASS). */
    private int questionEnd(byte[] p, int dnsOff) {
        int i = dnsOff + 12;
        int guard = 0;
        while (guard++ < 128) {
            int lenByte = p[i] & 0xFF;
            if (lenByte == 0) { i++; break; }
            i += 1 + lenByte;
        }
        return i + 4; // + QTYPE(2) + QCLASS(2)
    }

    /** Monta uma resposta DNS NXDOMAIN (dominio inexistente) para a consulta. */
    private byte[] buildBlockedDns(byte[] p, int dnsOff, int dnsLen) {
        try {
            int qEnd = questionEnd(p, dnsOff);
            int qSectionLen = qEnd - dnsOff;           // header(12) + pergunta
            byte[] dns = new byte[qSectionLen];
            System.arraycopy(p, dnsOff, dns, 0, qSectionLen);
            // flags: QR=1, Opcode=0, AA=0, TC=0, RD=copia, RA=1, RCODE=3 (NXDOMAIN)
            boolean rd = (dns[2] & 0x01) != 0;
            dns[2] = (byte) (0x80 | (rd ? 0x01 : 0x00));
            dns[3] = (byte) 0x83;
            // ANCOUNT=0, NSCOUNT=0, ARCOUNT=0 (QDCOUNT mantem 1)
            dns[6] = 0; dns[7] = 0; dns[8] = 0; dns[9] = 0; dns[10] = 0; dns[11] = 0;
            return dns;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Montagem de pacote IPv4/UDP ----------

    private byte[] buildIpv4Udp(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] payload) {
        int udpLen = 8 + payload.length;
        int total = 20 + udpLen;
        byte[] pkt = new byte[total];
        // IPv4
        pkt[0] = 0x45;                   // versao 4, IHL 5
        pkt[1] = 0;                      // DSCP/ECN
        pkt[2] = (byte) ((total >> 8) & 0xFF);
        pkt[3] = (byte) (total & 0xFF);
        pkt[4] = 0; pkt[5] = 0;          // id
        pkt[6] = 0; pkt[7] = 0;          // flags/frag
        pkt[8] = 64;                     // TTL
        pkt[9] = 17;                     // protocolo UDP
        pkt[10] = 0; pkt[11] = 0;        // checksum (calculado abaixo)
        System.arraycopy(srcIp, 0, pkt, 12, 4);
        System.arraycopy(dstIp, 0, pkt, 16, 4);
        int ipc = checksum(pkt, 0, 20);
        pkt[10] = (byte) ((ipc >> 8) & 0xFF);
        pkt[11] = (byte) (ipc & 0xFF);
        // UDP
        pkt[20] = (byte) ((srcPort >> 8) & 0xFF);
        pkt[21] = (byte) (srcPort & 0xFF);
        pkt[22] = (byte) ((dstPort >> 8) & 0xFF);
        pkt[23] = (byte) (dstPort & 0xFF);
        pkt[24] = (byte) ((udpLen >> 8) & 0xFF);
        pkt[25] = (byte) (udpLen & 0xFF);
        pkt[26] = 0; pkt[27] = 0;        // checksum UDP = 0 (permitido em IPv4)
        System.arraycopy(payload, 0, pkt, 28, payload.length);
        return pkt;
    }

    private int checksum(byte[] b, int off, int len) {
        long sum = 0;
        int i = off;
        while (len > 1) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            i += 2; len -= 2;
        }
        if (len == 1) sum += (b[i] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    // ---------- Servico ----------

    private void iniciarForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "JP AdShield",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Protecao contra anuncios ativa");
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_ID)
                .setContentTitle("JP AdShield ativo")
                .setContentText("Bloqueando anuncios e rastreadores no aparelho.")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
    }

    private void stopVpn() {
        running = false;
        try { if (pool != null) pool.shutdownNow(); } catch (Exception ignored) {}
        try { if (vpn != null) { vpn.close(); vpn = null; } } catch (Exception ignored) {}
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }
}
