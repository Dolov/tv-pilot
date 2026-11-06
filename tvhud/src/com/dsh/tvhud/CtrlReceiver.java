package com.dsh.tvhud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CtrlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (action.endsWith(".SHOW")) {
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".HIDE")) {
            context.stopService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PREVIEW_ON") || action.endsWith(".PREVIEW_OFF")) {
            boolean on = action.endsWith(".PREVIEW_ON");
            context.getSharedPreferences("hud", 0).edit().putBoolean("usage_preview", on).apply();
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".RESET_USAGE")) {
            // 清空当天分段，用 UsageEvents 重建（换口径/误记时用；详情页会立刻反映）
            Intent svc = new Intent(context, HudService.class);
            svc.putExtra("reset_usage", true);
            context.startService(svc);
            return;
        }
        if (action.endsWith(".ARCHIVE_NOW")) {
            Intent svc = new Intent(context, HudService.class);
            svc.putExtra("archive_now", true);
            context.startService(svc);
            return;
        }
        if (action.endsWith(".RELOAD_PLAYS")) {
            Intent svc = new Intent(context, HudService.class);
            svc.putExtra("reload_plays", true);
            context.startService(svc);
            return;
        }
        if (action.endsWith(".ADB_KEEP_ON") || action.endsWith(".ADB_KEEP_OFF")) {
            boolean on = action.endsWith(".ADB_KEEP_ON");
            context.getSharedPreferences("hud", 0).edit().putBoolean("adb_keep", on).apply();
            if (on) {
                context.startService(new Intent(context, HudService.class));
            }
        }
    }
}
