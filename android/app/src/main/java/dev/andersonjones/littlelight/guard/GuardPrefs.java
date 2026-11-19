package dev.andersonjones.littlelight.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Everything the app guard remembers, stored privately on this phone.
 * The daily "unlocked" state and minutes used reset automatically at midnight.
 */
public class GuardPrefs {
    private static final String FILE = "little_light_guard";
    private final SharedPreferences p;

    public GuardPrefs(Context c) {
        p = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void rollDay() {
        String t = today();
        if (!t.equals(p.getString("day", ""))) {
            p.edit().putString("day", t).putLong("usedMs", 0L).apply();
        }
    }

    public boolean enabled() { return p.getBoolean("enabled", false); }
    public void setEnabled(boolean v) { p.edit().putBoolean("enabled", v).apply(); }

    public Set<String> blocked() { return new HashSet<>(p.getStringSet("blocked", new HashSet<>())); }
    public void setBlocked(Set<String> s) { p.edit().putStringSet("blocked", new HashSet<>(s)).apply(); }

    public long allowanceMs() { return p.getLong("allowanceMs", 30L * 60_000L); }
    public void setAllowanceMs(long v) { p.edit().putLong("allowanceMs", Math.max(60_000L, v)).apply(); }

    public boolean unlockedToday() { return today().equals(p.getString("unlockedDay", "")); }
    public void setUnlocked(boolean v) { p.edit().putString("unlockedDay", v ? today() : "").apply(); }

    public long usedMs() { rollDay(); return p.getLong("usedMs", 0L); }
    public void addUsedMs(long ms) { rollDay(); p.edit().putLong("usedMs", p.getLong("usedMs", 0L) + ms).apply(); }
    public void setUsedMs(long ms) { rollDay(); p.edit().putLong("usedMs", ms).apply(); }
    public long remainingMs() { return Math.max(0L, allowanceMs() - usedMs()); }

    public long pausedUntil() { return p.getLong("pausedUntil", 0L); }
    public void setPausedUntil(long t) { p.edit().putLong("pausedUntil", t).apply(); }
    public boolean paused() { return System.currentTimeMillis() < pausedUntil(); }

    /** Set when a child taps a button on the guard screen, so the app can greet them. */
    public void setGate(String mode, String label) {
        p.edit().putString("gateMode", mode).putString("gateLabel", label).putLong("gateAt", System.currentTimeMillis()).apply();
    }
    public String gateMode() { return p.getString("gateMode", ""); }
    public String gateLabel() { return p.getString("gateLabel", ""); }
    public long gateAt() { return p.getLong("gateAt", 0L); }
    public void clearGate() { p.edit().remove("gateMode").remove("gateLabel").remove("gateAt").apply(); }
}
