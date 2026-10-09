/*
 * JP AdShield - PlaybackService
 * Criado por Joaquim Pedro de Morais Filho - j360074@hotmail.com
 *
 * Mantem o processo vivo enquanto o video toca em 2o plano (wake lock),
 * segura o foco de audio e mostra uma notificacao com Pausar/Parar.
 */
package ai.elevbit.jpadshield;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class PlaybackService extends Service {

    public static final String ACTION_START = "ai.elevbit.jpadshield.PLAY_START";
    public static final String ACTION_STOP = "ai.elevbit.jpadshield.PLAY_STOP";
    public static final String ACTION_TOGGLE = "ai.elevbit.jpadshield.PLAY_TOGGLE";

    private static final String CH_ID = "jpadshield_player";
    private static final int NOTIF_ID = 8;

    private PowerManager.WakeLock wakeLock;
    private AudioFocusRequest focusReq;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            PlayerActivity.stopPlayer();
            liberar();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            PlayerActivity.playPause();
            return START_STICKY;
        }
        // ACTION_START
        startForeground(NOTIF_ID, build());
        segurarRecursos();
        return START_STICKY;
    }

    private Notification build() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "JP Player",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, PlayerActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CH_ID)
                .setContentTitle("JP Player — tocando em 2º plano")
                .setContentText("O vídeo continua mesmo com a tela apagada.")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(0, "Pausar/Retomar", toggle)
                .addAction(0, "Parar", stop)
                .build();
    }

    private void segurarRecursos() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jpadshield:player");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(4L * 60 * 60 * 1000); // ate 4h
        } catch (Exception ignored) {}
        try {
            AudioManager am = getSystemService(AudioManager.class);
            if (Build.VERSION.SDK_INT >= 26) {
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        .build();
                am.requestAudioFocus(focusReq);
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {}
    }

    private void liberar() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try {
            AudioManager am = getSystemService(AudioManager.class);
            if (Build.VERSION.SDK_INT >= 26 && focusReq != null) am.abandonAudioFocusRequest(focusReq);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        liberar();
        super.onDestroy();
    }
}
