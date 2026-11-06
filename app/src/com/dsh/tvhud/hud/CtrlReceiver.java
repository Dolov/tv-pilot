package com.dsh.tvhud.hud;

import com.dsh.tvhud.pet.Budget;
import com.dsh.tvhud.pet.PetGateActivity;
import com.dsh.tvhud.pet.PetSettingsActivity;
import com.dsh.tvhud.pet.PetState;
import com.dsh.tvhud.pet.PetStore;

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
        if (action.endsWith(".PET_RESET")) {
            PetStore.reset(context);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_RELOAD")) {
            Intent svc = new Intent(context, HudService.class);
            svc.putExtra("pet_reload", true);
            context.startService(svc);
            return;
        }
        if (action.endsWith(".PET_SETTINGS")) {
            Intent settings = new Intent(context, PetSettingsActivity.class);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(settings);
            return;
        }
        if (action.endsWith(".PET_NEAR_END")) {
            Budget b = PetStore.loadBudget(context);
            long nearMs = Math.max(1, intent.getIntExtra("seconds", 180)) * 1000L;
            b.sessionMs = Math.max(0L, b.limitMs() - nearMs);
            b.gateOpen = false;
            PetStore.saveBudget(context, b);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_DEBUG")) {
            PetState s = PetStore.loadState(context);
            Budget b = PetStore.loadBudget(context);
            if (intent.hasExtra("exp")) {
                s.exp = Math.max(0, intent.getIntExtra("exp", s.exp));
            }
            if (intent.hasExtra("stars")) {
                s.stars = Math.max(0, intent.getIntExtra("stars", s.stars));
            }
            if (intent.hasExtra("weight")) {
                s.weight = Math.max(0, Math.min(100, intent.getIntExtra("weight", s.weight)));
            }
            if (intent.hasExtra("teeth")) {
                s.teeth = Math.max(0, Math.min(100, intent.getIntExtra("teeth", s.teeth)));
            }
            if (intent.hasExtra("sessions")) {
                b.sessionsUsed = Math.max(0, Math.min(b.maxSessions, intent.getIntExtra("sessions", b.sessionsUsed)));
                b.sessionMs = 0L;
                b.gateOpen = false;
            }
            PetStore.save(context, s, b);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_GATE")) {
            Budget b = PetStore.loadBudget(context);
            b.gateOpen = true;
            PetStore.saveBudget(context, b);
            Intent gate = new Intent(context, PetGateActivity.class);
            gate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(gate);
            return;
        }
        if (action.endsWith(".PET_PARENT_ON") || action.endsWith(".PET_PARENT_OFF")) {
            boolean on = action.endsWith(".PET_PARENT_ON");
            PetStore.setParentMode(context, on);
            Budget b = PetStore.loadBudget(context);
            b.parentMode = on;
            PetStore.saveBudget(context, b);
            context.startService(new Intent(context, HudService.class));
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
