package dev.andersonjones.littlelight.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

import dev.andersonjones.littlelight.MainActivity;
import dev.andersonjones.littlelight.R;

/**
 * The "faith-first helper". Once a second it checks which app is on screen (Usage access).
 * If a chosen fun app is opened before faith time, or after today's fun time is used up,
 * it shows a friendly Little Light screen over it (Display over other apps).
 * It never reads what is on screen and nothing leaves the phone.
 */
public class GuardService extends Service {
    private static final String CHANNEL = "guard";
    private static final int NOTE_ID = 7;
    private static final int MODE_NONE = 0, MODE_GATE = 1, MODE_DONE = 2;

    private final Handler h = new Handler(Looper.getMainLooper());
    private GuardPrefs prefs;
    private UsageStatsManager usm;
    private WindowManager wm;
    private View overlay;
    private int overlayMode = MODE_NONE;
    private String overlayPkg = "";
    private String fg = "";
    private long lastQuery = 0L, lastTick = 0L;
    private String lastNote = "";

    public static void sync(Context c) {
        GuardPrefs p = new GuardPrefs(c);
        Intent i = new Intent(c, GuardService.class);
        if (p.enabled() && GuardPlugin.hasUsageAccess(c) && Settings.canDrawOverlays(c)) {
            try { ContextCompat.startForegroundService(c, i); } catch (Exception ignored) { }
        } else {
            c.stopService(i);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = new GuardPrefs(this);
        usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Faith-first helper", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows that Little Light's helper is on");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
        ServiceCompat.startForeground(this, NOTE_ID, note("Faith-first helper is on"), type);
        lastNote = "";
        lastTick = System.currentTimeMillis();
        lastQuery = lastTick - 60_000L;
        h.removeCallbacks(loop);
        h.post(loop);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        h.removeCallbacks(loop);
        hideOverlay();
        super.onDestroy();
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            long delay = 1000L;
            try {
                if (!prefs.enabled() || !GuardPlugin.hasUsageAccess(GuardService.this)) { hideOverlay(); stopSelf(); return; }
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isInteractive()) { delay = 4000L; lastTick = System.currentTimeMillis(); }
                else tick();
            } catch (Exception ignored) { }
            h.postDelayed(this, delay);
        }
    };

    private void tick() {
        long now = System.currentTimeMillis();
        long elapsed = Math.min(5000L, Math.max(0L, now - lastTick));
        lastTick = now;
        updateForeground(now);

        Set<String> blocked = prefs.blocked();
        boolean isFun = fg.length() > 0 && blocked.contains(fg);
        if (!isFun || prefs.paused()) {
            hideOverlay();
            updateNote(prefs.paused() ? "Paused for grown-up time" : status());
            return;
        }
        if (!prefs.unlockedToday()) {
            showOverlay(MODE_GATE, fg);
        } else if (prefs.remainingMs() <= 0) {
            showOverlay(MODE_DONE, fg);
        } else {
            hideOverlay();
            prefs.addUsedMs(elapsed);
        }
        updateNote(status());
    }

    private String status() {
        if (!prefs.unlockedToday()) return "Fun apps open after faith time";
        long left = prefs.remainingMs();
        if (left <= 0) return "Fun time is done for today";
        return "Fun time: " + (left + 59_999L) / 60_000L + " min left today";
    }

    /** Finds the app most recently brought to the front. */
    private void updateForeground(long now) {
        if (usm == null) return;
        UsageEvents ev = usm.queryEvents(Math.max(lastQuery - 2000L, now - 3_600_000L), now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == 1 /* ACTIVITY_RESUMED */) fg = e.getPackageName();
        }
        lastQuery = now;
    }

    private Notification note(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_light)
            .setContentTitle("Little Light")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void updateNote(String text) {
        if (text.equals(lastNote)) return;
        lastNote = text;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTE_ID, note(text));
    }

    // ---------------------------------------------------------------- overlay screen

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) { return "This app"; }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private Button button(String text, int bg, int fgColor, int shadow) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fgColor);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(30));
        d.setStroke(dp(2), shadow);
        b.setBackground(d);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private void showOverlay(int mode, String pkg) {
        if (overlay != null && overlayMode == mode && overlayPkg.equals(pkg)) return;
        hideOverlay();
        if (!Settings.canDrawOverlays(this)) return;
        String label = labelOf(pkg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#FFF8EA"));
        root.setPadding(dp(32), dp(48), dp(32), dp(48));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) { goHome(); return true; }
            return false;
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher_round);
        root.addView(icon, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView title = new TextView(this);
        title.setText(mode == MODE_GATE ? "Faith time first!" : "Fun time is done for today");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#2C3A4B"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(20), 0, dp(8));
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(mode == MODE_GATE
            ? label + " is resting for now. Spend a little time with God in Little Light, then your fun time can begin."
            : "Great job today! " + label + " is resting until tomorrow. Time to rest your eyes and go play.");
        body.setTextSize(18);
        body.setTextColor(Color.parseColor("#5D6A79"));
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        root.addView(body);

        Button open = button(mode == MODE_GATE ? "Open Little Light" : "See my garden",
            Color.parseColor("#FFC93C"), Color.parseColor("#3A2A00"), Color.parseColor("#C98A00"));
        open.setOnClickListener(v -> openApp(mode == MODE_GATE ? "gate" : "done", label));
        root.addView(open);

        Button home = button("Go to home screen", Color.WHITE, Color.parseColor("#2C3A4B"), Color.parseColor("#EFE4CC"));
        home.setOnClickListener(v -> goHome());
        root.addView(home);

        TextView grown = new TextView(this);
        grown.setText("Grown-up? Tap here to pause with your PIN");
        grown.setTextSize(15);
        grown.setTextColor(Color.parseColor("#5D6A79"));
        grown.setGravity(Gravity.CENTER);
        grown.setPadding(dp(8), dp(22), dp(8), dp(8));
        grown.setOnClickListener(v -> openApp("parent", label));
        root.addView(grown);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        lp.gravity = Gravity.CENTER;
        try {
            wm.addView(root, lp);
            overlay = root;
            overlayMode = mode;
            overlayPkg = pkg;
            root.requestFocus();
        } catch (Exception ignored) { overlay = null; }
    }

    private void hideOverlay() {
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) { }
        }
        overlay = null;
        overlayMode = MODE_NONE;
        overlayPkg = "";
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
        hideOverlay();
    }

    private void openApp(String mode, String label) {
        prefs.setGate(mode, label);
        Intent i = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(i); } catch (Exception ignored) { }
        hideOverlay();
        fg = getPackageName();
    }
}
