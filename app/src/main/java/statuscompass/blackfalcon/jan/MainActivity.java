package statuscompass.blackfalcon.jan;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny control screen. The real compass lives in the status bar / lock screen. */
public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1;
    private static final int GOLD = 0xFFC9A227;

    private TextView statusView;
    private Button toggleButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101216);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = label("Compass", 30, 0xFFFFFFFF, true);
        TextView hint = label("Shows your heading in the status bar and on the lock screen:\n"
                + "degrees + direction (e.g. 245\u00B0 SW)", 15, 0xFF9AA0A6, false);
        statusView = label("", 16, GOLD, false);

        toggleButton = new Button(this);
        toggleButton.setOnClickListener(v -> {
            if (CompassService.running) {
                CompassService.stop(this);
                refreshUi();
            } else {
                startCompass();
            }
        });

        column.addView(title);
        column.addView(hint);
        column.addView(statusView);
        column.addView(toggleButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16+: make sure "Live updates" is allowed so the compass chip shows in the status bar
            Button live = new Button(this);
            live.setText("Live updates settings");
            live.setOnClickListener(v -> openLiveUpdateSettings());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(12);
            column.addView(live, lp);
        }

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = label("By: Black Falcon", 14, GOLD, true);
        credit.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(16), dp(20), 0);
        root.addView(credit, lp);

        setContentView(root);

        if (HeadingProvider.isSupported(this)) {
            startCompass();
        } else {
            statusView.setText("This device has no compass sensor");
            toggleButton.setEnabled(false);
        }
    }

    private void startCompass() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else {
            CompassService.start(this);
            refreshUi();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            CompassService.start(this); // runs either way; notification only shows if allowed
            refreshUi();
        }
    }

    private void openLiveUpdateSettings() {
        try {
            Intent i = new Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS");
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }
    }

    private void refreshUi() {
        if (!HeadingProvider.isSupported(this)) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean notificationsOk = nm == null || nm.areNotificationsEnabled();
        if (CompassService.running) {
            statusView.setText(notificationsOk
                    ? "Status bar compass: ON"
                    : "Notifications are blocked \u2014 allow them in app settings to see the compass in the status bar");
            toggleButton.setText("Stop");
        } else {
            statusView.setText("Status bar compass: OFF");
            toggleButton.setText("Start");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, dp(16));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
