/*
 * JP AdShield
 * Criado por Joaquim Pedro de Morais Filho - j360074@hotmail.com
 * (C) 2026 - Todos os direitos reservados.
 *
 * Bloqueador de anuncios e rastreadores para todo o Android, via VPN
 * local + filtro de DNS, sem root. Nenhum dado sai do aparelho (o DNS
 * vai direto para o Cloudflare 1.1.1.1).
 */
package ai.elevbit.jpadshield;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final int REQ_VPN = 1001;
    private static final String HOSTS_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";

    private Button btToggle, btUpdate, btYoutube, btPlayer;
    private TextView tvStatus, tvStats, tvListInfo;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() { atualizar(); ui.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btToggle = findViewById(R.id.btToggle);
        btUpdate = findViewById(R.id.btUpdate);
        btYoutube = findViewById(R.id.btYoutube);
        btPlayer = findViewById(R.id.btPlayer);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        tvListInfo = findViewById(R.id.tvListInfo);

        Blocklist.load(this);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        btToggle.setOnClickListener(v -> {
            if (AdVpnService.running) {
                pararVpn();
            } else {
                Intent prep = VpnService.prepare(this);
                if (prep != null) startActivityForResult(prep, REQ_VPN);
                else iniciarVpn();
            }
        });

        btUpdate.setOnClickListener(v -> atualizarListas());
        btYoutube.setOnClickListener(v -> explicarYoutube());
        btPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresher);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) iniciarVpn();
        else if (requestCode == REQ_VPN) {
            Toast.makeText(this, R.string.msg_permissao_vpn, Toast.LENGTH_LONG).show();
        }
    }

    private void iniciarVpn() {
        Intent i = new Intent(this, AdVpnService.class).setAction(AdVpnService.ACTION_CONNECT);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, R.string.msg_ativado, Toast.LENGTH_SHORT).show();
    }

    private void pararVpn() {
        Intent i = new Intent(this, AdVpnService.class).setAction(AdVpnService.ACTION_DISCONNECT);
        startService(i);
        Toast.makeText(this, R.string.msg_desativado, Toast.LENGTH_SHORT).show();
    }

    private void atualizar() {
        boolean on = AdVpnService.running;
        tvStatus.setText(on ? R.string.status_on : R.string.status_off);
        tvStatus.setTextColor(on ? 0xFF1FBF66 : 0xFF9AA6C0);
        btToggle.setText(on ? R.string.bt_desativar : R.string.bt_ativar);
        long bl = AdVpnService.blockedCount.get();
        long al = AdVpnService.allowedCount.get();
        tvStats.setText(getString(R.string.stats_fmt, bl, al));
        tvListInfo.setText(getString(R.string.lista_fmt, Blocklist.size));
    }

    private void atualizarListas() {
        btUpdate.setEnabled(false);
        Toast.makeText(this, R.string.msg_baixando, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String resultado;
            try {
                URL url = new URL(HOSTS_URL);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                InputStream in = new BufferedInputStream(c.getInputStream());
                File dest = new File(getFilesDir(), "hosts.txt");
                FileOutputStream fos = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close(); in.close(); c.disconnect();
                Blocklist.load(MainActivity.this);
                resultado = getString(R.string.msg_atualizado, Blocklist.size);
            } catch (Exception e) {
                resultado = getString(R.string.msg_erro_download);
            }
            final String r = resultado;
            ui.post(() -> {
                btUpdate.setEnabled(true);
                Toast.makeText(MainActivity.this, r, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void explicarYoutube() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.yt_titulo)
                .setMessage(R.string.yt_texto)
                .setPositiveButton(R.string.yt_navegador, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://m.youtube.com")));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton(R.string.fechar, null)
                .show();
    }
}
