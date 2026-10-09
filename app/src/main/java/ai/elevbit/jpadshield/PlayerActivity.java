/*
 * JP AdShield - PlayerActivity (reprodutor em 2o plano)
 * Criado por Joaquim Pedro de Morais Filho - j360074@hotmail.com
 *
 * Abre videos da web (inclusive YouTube) num WebView que CONTINUA
 * tocando em segundo plano - com a tela apagada ou o app minimizado -
 * e em janela flutuante (Picture-in-Picture), parecido com o Premium.
 * Como: um servico em primeiro plano + wake lock mantem o processo vivo,
 * e um script injetado impede o YouTube de pausar ao ir para 2o plano.
 */
package ai.elevbit.jpadshield;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;

public class PlayerActivity extends Activity {

    private static WeakReference<PlayerActivity> INSTANCE;

    private WebView web;
    private EditText edUrl;
    private View bar;
    private boolean desktop = false;

    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36";

    // Script que mantem a reproducao em 2o plano (engana a Page Visibility API)
    private static final String KEEP_PLAYING_JS =
        "(function(){try{" +
        "Object.defineProperty(document,'hidden',{get:function(){return false;},configurable:true});" +
        "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible';},configurable:true});" +
        "Object.defineProperty(document,'webkitHidden',{get:function(){return false;},configurable:true});" +
        "Object.defineProperty(document,'webkitVisibilityState',{get:function(){return 'visible';},configurable:true});" +
        "var block=function(e){e.stopImmediatePropagation();};" +
        "document.addEventListener('visibilitychange',block,true);" +
        "window.addEventListener('visibilitychange',block,true);" +
        "document.addEventListener('webkitvisibilitychange',block,true);" +
        "window.addEventListener('blur',block,true);" +
        "}catch(e){}})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        INSTANCE = new WeakReference<>(this);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Blocklist.load(this);

        bar = findViewById(R.id.bar);
        edUrl = findViewById(R.id.edUrl);
        web = findViewById(R.id.web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(MOBILE_UA);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(KEEP_PLAYING_JS, null);
            }
            // Bloqueia dominios de anuncio/rastreador tambem dentro do player
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                try {
                    String host = req.getUrl().getHost();
                    if (host != null && Blocklist.isBlocked(host)) {
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                } catch (Exception ignored) {}
                return null;
            }
        });

        ((Button) findViewById(R.id.btGo)).setOnClickListener(v -> irParaUrl());
        ((Button) findViewById(R.id.btPip)).setOnClickListener(v -> entrarPip());
        ((Button) findViewById(R.id.btDesktop)).setOnClickListener(v -> {
            desktop = !desktop;
            web.getSettings().setUserAgentString(desktop ? DESKTOP_UA : MOBILE_UA);
            web.reload();
            Toast.makeText(this, desktop ? "Modo computador" : "Modo celular", Toast.LENGTH_SHORT).show();
        });

        String start = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (start == null || start.isEmpty()) start = "https://m.youtube.com";
        edUrl.setText(start);
        web.loadUrl(start);

        // mantem vivo em 2o plano
        Intent svc = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
    }

    private void irParaUrl() {
        String u = edUrl.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            // se nao for URL, pesquisa no YouTube
            u = "https://m.youtube.com/results?search_query=" + Uri.encode(u);
        }
        web.loadUrl(u);
    }

    private void entrarPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PictureInPictureParams p = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9)).build();
                enterPictureInPictureMode(p);
            } catch (Exception e) {
                Toast.makeText(this, "Picture-in-Picture indisponivel neste aparelho.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Picture-in-Picture requer Android 8+.", Toast.LENGTH_SHORT).show();
        }
    }

    // Ao sair do app (botao Home), entra em Picture-in-Picture automaticamente
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9)).build());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (bar != null) bar.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }

    // IMPORTANTE: NAO pausar o WebView ao ir para 2o plano (manter tocando)
    @Override
    protected void onPause() {
        super.onPause();
        // de proposito: nada de web.onPause()
    }

    @Override
    protected void onDestroy() {
        try {
            Intent svc = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STOP);
            startService(svc);
        } catch (Exception ignored) {}
        try { if (web != null) { web.loadUrl("about:blank"); web.destroy(); } } catch (Exception ignored) {}
        if (INSTANCE != null && INSTANCE.get() == this) INSTANCE = null;
        super.onDestroy();
    }

    // ---- controles chamados pela notificacao (PlaybackService) ----
    public static void playPause() {
        PlayerActivity a = INSTANCE != null ? INSTANCE.get() : null;
        if (a != null && a.web != null) {
            a.runOnUiThread(() -> a.web.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})();",
                null));
        }
    }

    public static void stopPlayer() {
        PlayerActivity a = INSTANCE != null ? INSTANCE.get() : null;
        if (a != null) a.runOnUiThread(a::finish);
    }
}
