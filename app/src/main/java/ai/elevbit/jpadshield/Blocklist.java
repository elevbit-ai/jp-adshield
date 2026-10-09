/*
 * JP AdShield - Blocklist
 * Criado por Joaquim Pedro de Morais Filho - j360074@hotmail.com
 *
 * Mantem o conjunto de dominios bloqueados (anuncios/rastreadores).
 * Carrega a lista embutida (assets/blocklist.txt) e, se existir, a lista
 * atualizada baixada para o armazenamento interno (hosts.txt).
 * O casamento cobre subdominios: bloquear "doubleclick.net" tambem
 * bloqueia "ad.doubleclick.net".
 */
package ai.elevbit.jpadshield;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class Blocklist {

    private static volatile Set<String> BLOCKED = new HashSet<>();
    public static volatile int size = 0;

    /** Carrega a lista embutida + a baixada (se houver). */
    public static synchronized void load(Context ctx) {
        Set<String> set = new HashSet<>();
        // 1) lista embutida
        try {
            InputStream is = ctx.getAssets().open("blocklist.txt");
            parse(new BufferedReader(new InputStreamReader(is)), set);
            is.close();
        } catch (Exception ignored) {}
        // 2) lista atualizada baixada
        try {
            File f = new File(ctx.getFilesDir(), "hosts.txt");
            if (f.exists()) {
                parse(new BufferedReader(new FileReader(f)), set);
            }
        } catch (Exception ignored) {}
        BLOCKED = set;
        size = set.size();
    }

    private static void parse(BufferedReader br, Set<String> set) throws Exception {
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            // formatos aceitos: "0.0.0.0 dominio", "127.0.0.1 dominio" ou "dominio"
            String domain;
            int sp = line.indexOf(' ');
            int tab = line.indexOf('\t');
            int cut = sp;
            if (tab >= 0 && (cut < 0 || tab < cut)) cut = tab;
            if (cut >= 0) {
                domain = line.substring(cut + 1).trim();
            } else {
                domain = line;
            }
            // remove comentario no fim da linha
            int hash = domain.indexOf('#');
            if (hash >= 0) domain = domain.substring(0, hash).trim();
            int sp2 = domain.indexOf(' ');
            if (sp2 >= 0) domain = domain.substring(0, sp2).trim();
            domain = domain.toLowerCase();
            if (domain.isEmpty()) continue;
            if (domain.equals("localhost") || domain.equals("localhost.localdomain")
                    || domain.equals("local") || domain.equals("broadcasthost")
                    || domain.equals("0.0.0.0") || domain.equals("ip6-localhost")) continue;
            set.add(domain);
        }
    }

    /** True se o dominio (ou algum dominio-pai) estiver na lista. */
    public static boolean isBlocked(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        Set<String> set = BLOCKED;
        if (set.isEmpty()) return false;
        // verifica o dominio e cada sufixo: a.b.c -> a.b.c, b.c, c
        int idx = 0;
        String sub = host;
        while (true) {
            if (set.contains(sub)) return true;
            idx = sub.indexOf('.');
            if (idx < 0) break;
            sub = sub.substring(idx + 1);
            if (sub.indexOf('.') < 0) break; // nao bloquear TLD puro
        }
        return false;
    }
}
