package dev.andersonjones.littlelight.guard;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Base64;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bridge between Little Light's screens and the faith-first helper. */
@CapacitorPlugin(name = "Guard")
public class GuardPlugin extends Plugin {

    public static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    private GuardPrefs prefs() { return new GuardPrefs(getContext()); }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        GuardPrefs p = prefs();
        JSObject r = new JSObject();
        r.put("available", true);
        r.put("usageAccess", hasUsageAccess(c));
        r.put("overlay", Settings.canDrawOverlays(c));
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        r.put("enabled", p.enabled());
        r.put("unlocked", p.unlockedToday());
        r.put("usedSec", p.usedMs() / 1000L);
        r.put("allowanceSec", p.allowanceMs() / 1000L);
        r.put("remainingSec", p.remainingMs() / 1000L);
        r.put("pausedUntil", p.pausedUntil());
        r.put("paused", p.paused());
        JSArray b = new JSArray();
        for (String s : p.blocked()) b.put(s);
        r.put("blocked", b);
        r.put("gateMode", p.gateMode());
        r.put("gateLabel", p.gateLabel());
        r.put("gateAt", p.gateAt());
        call.resolve(r);
    }

    @PluginMethod
    public void listApps(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<>();
        List<JSObject> out = new ArrayList<>();
        String self = getContext().getPackageName();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || !seen.add(pkg)) continue;
            JSObject o = new JSObject();
            o.put("pkg", pkg);
            o.put("label", ri.loadLabel(pm).toString());
            o.put("icon", iconData(ri.loadIcon(pm)));
            out.add(o);
        }
        Collections.sort(out, (a, b) -> a.getString("label", "").compareToIgnoreCase(b.getString("label", "")));
        JSArray arr = new JSArray();
        for (JSObject o : out) arr.put(o);
        JSObject r = new JSObject();
        r.put("apps", arr);
        call.resolve(r);
    }

    private String iconData(Drawable d) {
        try {
            int s = 72;
            Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            d.setBounds(0, 0, s, s);
            d.draw(cv);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    /** Saves the parent's choices and starts or stops the helper. */
    @PluginMethod
    public void configure(PluginCall call) {
        GuardPrefs p = prefs();
        if (call.hasOption("enabled")) p.setEnabled(Boolean.TRUE.equals(call.getBoolean("enabled")));
        if (call.hasOption("allowanceSec")) p.setAllowanceMs(call.getInt("allowanceSec", 1800) * 1000L);
        JSArray b = call.getArray("blocked");
        if (b != null) {
            Set<String> s = new HashSet<>();
            try { for (int i = 0; i < b.length(); i++) s.add(b.getString(i)); } catch (Exception ignored) { }
            p.setBlocked(s);
        }
        GuardService.sync(getContext());
        status(call);
    }

    @PluginMethod
    public void setUnlocked(PluginCall call) {
        prefs().setUnlocked(Boolean.TRUE.equals(call.getBoolean("unlocked", false)));
        status(call);
    }

    /** Marks today's fun time as used up (child tapped "I'm finished"). */
    @PluginMethod
    public void finishFun(PluginCall call) {
        GuardPrefs p = prefs();
        p.setUsedMs(p.allowanceMs());
        status(call);
    }

    @PluginMethod
    public void resetToday(PluginCall call) {
        GuardPrefs p = prefs();
        p.setUnlocked(false);
        p.setUsedMs(0L);
        status(call);
    }

    @PluginMethod
    public void pause(PluginCall call) {
        int minutes = call.getInt("minutes", 60);
        prefs().setPausedUntil(minutes <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L);
        status(call);
    }

    @PluginMethod
    public void clearGate(PluginCall call) {
        prefs().clearGate();
        call.resolve();
    }

    @PluginMethod
    public void openUsageSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        if (Build.VERSION.SDK_INT >= 29) i.setData(Uri.parse("package:" + getContext().getPackageName()));
        try { getActivity().startActivity(i); }
        catch (Exception e) { getActivity().startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
        call.resolve();
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
        try { getActivity().startActivity(i); } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4711);
        } else {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            try { getActivity().startActivity(i); } catch (Exception ignored) { }
        }
        call.resolve();
    }

    @Override
    protected void handleOnResume() {
        GuardService.sync(getContext());
        notifyListeners("resume", new JSObject());
    }
}
