package statuscompass.blackfalcon.jan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * Foreground service: runs in the background and shows the live heading.
 *
 * - All Android versions: status-bar icon (degrees on top, direction below) + lock-screen notification.
 * - Android 16+: also requests a "Live Update", which puts a chip like "245°SW" in the status bar
 *   (ahead of the normal notification icons) and at the top of the lock screen.
 */
public class CompassService extends Service implements HeadingProvider.Listener {

    public static final String ACTION_STOP = "statuscompass.blackfalcon.jan.STOP";
    private static final String CHANNEL_ID = "compass_status_v2";
    private static final int NOTIFICATION_ID = 1;
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final String[] DIRS_FULL = {
            "North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"};

    /** true while the status-bar compass is switched on */
    public static volatile boolean running = false;

    private NotificationManager notificationManager;
    private HeadingProvider provider;
    private boolean sensorsStarted;
    private int lastDegrees = -1;
    private long lastUpdate = 0;

    public static void start(Context context) {
        running = true;
        context.startForegroundService(new Intent(context, CompassService.class));
    }

    public static void stop(Context context) {
        running = false;
        context.stopService(new Intent(context, CompassService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);

        // DEFAULT importance (ranks higher than LOW so its icon is less likely to be hidden),
        // but made completely silent: no sound, no vibration, no pop-up.
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Compass", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setDescription("Live compass heading in the status bar and on the lock screen");
        notificationManager.createNotificationChannel(channel);

        provider = new HeadingProvider(this, this, 0.3);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        Notification notification = buildNotification(false, 0f, 0, 0);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (!sensorsStarted) {
            sensorsStarted = provider.start(SensorManager.SENSOR_DELAY_UI);
            if (!sensorsStarted) {
                stopSelf(); // no compass sensor on this phone
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onHeading(float degrees) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastUpdate < 400) return; // don't spam the notification
        int deg = Math.round(degrees) % 360;
        if (deg == lastDegrees) return;
        lastDegrees = deg;
        lastUpdate = now;

        int idx = ((int) ((degrees + 22.5f) / 45f)) % 8;
        notificationManager.notify(NOTIFICATION_ID, buildNotification(true, degrees, deg, idx));
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(boolean known, float heading, int deg, int idx) {
        String title = known ? deg + "\u00B0  " + DIRS[idx] : "Compass starting\u2026";
        String text = known ? DIRS_FULL[idx] + "  \u2022  By: Black Falcon" : "By: Black Falcon";
        String chip = known ? deg + "\u00B0" + DIRS[idx] : "";

        boolean liveUpdate = Build.VERSION.SDK_INT >= 36 && canPostPromoted();
        Icon icon = liveUpdate
                ? Icon.createWithBitmap(makeNeedleIcon(heading))
                : Icon.createWithBitmap(known
                ? makeStatusIcon(String.valueOf(deg), DIRS[idx])
                : makeStatusIcon("--", ""));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, CompassService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC) // readable on the lock screen
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop);

        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        if (Build.VERSION.SDK_INT >= 36) {
            b.setStyle(new Notification.BigTextStyle().bigText(text));
            requestLiveUpdate(b, chip);
        }
        return b.build();
    }

    /** Android 16 "Live Update" APIs, called by reflection so the project still builds with compileSdk 34. */
    private boolean canPostPromoted() {
        try {
            Object r = NotificationManager.class
                    .getMethod("canPostPromotedNotifications")
                    .invoke(notificationManager);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    private void requestLiveUpdate(Notification.Builder b, String chipText) {
        try {
            Notification.Builder.class
                    .getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(b, true);
            if (!chipText.isEmpty()) {
                Notification.Builder.class
                        .getMethod("setShortCriticalText", String.class)
                        .invoke(b, chipText);
            }
        } catch (Throwable ignored) {
            // older Android: plain notification is used instead
        }
    }

    /** White-on-transparent icon: degrees on the top line, direction letters on the bottom line. */
    private Bitmap makeStatusIcon(String top, String bottom) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);

        if (bottom.isEmpty()) {
            drawFitted(c, p, top, size / 2f, size / 2f + 18f, 72f, size - 4f);
        } else {
            drawFitted(c, p, top, size / 2f, 44f, 50f, size - 4f);
            drawFitted(c, p, bottom, size / 2f, 90f, 50f, size - 4f);
        }
        return bmp;
    }

    /** Small live compass: a ring and a needle whose north tip follows the heading ("up" = where you face). */
    private Bitmap makeNeedleIcon(float heading) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        c.drawCircle(48f, 48f, 42f, p);

        c.save();
        c.rotate(-heading, 48f, 48f);

        Path north = new Path();
        north.moveTo(48f, 12f);
        north.lineTo(62f, 50f);
        north.lineTo(34f, 50f);
        north.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(north, p);

        Path south = new Path();
        south.moveTo(48f, 84f);
        south.lineTo(62f, 50f);
        south.lineTo(34f, 50f);
        south.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        c.drawPath(south, p);

        c.restore();
        return bmp;
    }

    private static void drawFitted(Canvas c, Paint p, String text, float cx, float baseline,
                                   float maxSize, float maxWidth) {
        p.setTextSize(maxSize);
        float width = p.measureText(text);
        if (width > maxWidth) {
            p.setTextSize(maxSize * maxWidth / width);
        }
        c.drawText(text, cx, baseline, p);
    }

    @Override
    public void onDestroy() {
        provider.stop();
        sensorsStarted = false;
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
