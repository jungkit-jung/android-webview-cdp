package app.so7o.webview;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the process out of the frozen cgroup while the app is backgrounded.
 *
 * Without this, vendor power managers and Android's cached-app freezer
 * put the process in /frozen once the app is in the background.
 * The DevTools socket stays listed in /proc/net/unix but is never accepted.
 *
 * A foreground service makes the process freeze-exempt.
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL = "so7o-webview-keepalive";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "WebView keep-alive", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setDescription("Keeps the DevTools socket reachable");

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification n = builder
                .setContentTitle("AGY Browser Shell")
                .setContentText("CDP reachable · pid " + android.os.Process.myPid())
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIFICATION_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } catch (Throwable t) {
                startForeground(NOTIFICATION_ID, n);
            }
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        return START_STICKY;
    }
}
