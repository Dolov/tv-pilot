package com.dsh.tvhud.hud;

import com.dsh.tvhud.pet.Budget;
import com.dsh.tvhud.pet.PetActivity;
import com.dsh.tvhud.pet.PetEngine;
import com.dsh.tvhud.pet.PetGateActivity;
import com.dsh.tvhud.pet.PetSettingsActivity;
import com.dsh.tvhud.pet.PetState;
import com.dsh.tvhud.pet.PetStore;
import com.dsh.tvhud.pet.Shop;

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
            // --ez window true 模拟「不在允许时段」的拦截页
            b.gateReason = intent.getBooleanExtra("window", false)
                    ? Budget.GATE_WINDOW : Budget.GATE_NORMAL;
            PetStore.saveBudget(context, b);
            Intent gate = new Intent(context, PetGateActivity.class);
            gate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(gate);
            return;
        }
        if (action.endsWith(".PET_INTERVAL")) {
            // 调试：模拟「两场之间的休息间隔」闸门，--ei seconds 30 表示 30 秒后放行
            Budget b = PetStore.loadBudget(context);
            long seconds = Math.max(1, intent.getIntExtra("seconds", 30));
            b.restUntil = System.currentTimeMillis() + seconds * 1000L;
            b.gateOpen = true;
            b.gateReason = Budget.GATE_INTERVAL;
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
        if (action.endsWith(".PET_SHOP")) {
            String item = intent.getStringExtra("item");
            PetState s = PetStore.loadState(context);
            if (item != null && item.startsWith("food_")) {
                Shop.feed(s, item, System.currentTimeMillis());
            } else if (item != null) {
                Shop.buy(s, item);
            }
            PetStore.saveState(context, s);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_BRANCH")) {
            // 调试专用：直接改分支外观（游戏内正常入口是花 30 星换线）
            PetState s = PetStore.loadState(context);
            int branch = intent.getIntExtra("branch", 1);
            if (branch >= PetState.BRANCH_WISDOM && branch <= PetState.BRANCH_GOURMET) {
                s.branch = branch;
                s.celebrateEvolution = true;
            }
            PetStore.saveState(context, s);
            return;
        }
        if (action.endsWith(".PET_MUTATE")) {
            PetState s = PetStore.loadState(context);
            PetEngine.forceMutation(s, System.currentTimeMillis());
            PetStore.saveState(context, s);
            return;
        }
        if (action.endsWith(".PET_DEGRADE")) {
            PetState s = PetStore.loadState(context);
            s.degradeUntil = System.currentTimeMillis() + PetState.DEGRADE_MS;
            s.degradeStage = Math.max(0, s.stage() - 1);
            s.celebrateEvolution = true;
            PetStore.saveState(context, s);
            return;
        }
        if (action.endsWith(".PET_GIVE_STARS")) {
            PetState s = PetStore.loadState(context);
            s.stars += Math.max(0, intent.getIntExtra("stars", 50));
            PetStore.saveState(context, s);
            return;
        }
        if (action.endsWith(".PET_WINDOW_ON") || action.endsWith(".PET_WINDOW_OFF")) {
            PetStore.setWindowEnabled(context, action.endsWith(".PET_WINDOW_ON"));
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_FREE_PASS_ON") || action.endsWith(".PET_FREE_PASS_OFF")) {
            boolean on = action.endsWith(".PET_FREE_PASS_ON");
            if (on) {
                PetStore.grantFreePass(context);
            } else {
                PetStore.revokeFreePass(context);
            }
            Budget b = PetStore.loadBudget(context);
            // 收掉挂着的闸门：卡刚发下来时孩子可能正卡在拦截页上
            b.gateOpen = false;
            b.gateReason = Budget.GATE_NORMAL;
            b.sessionMs = 0L;
            b.extensionMs = 0L;
            PetStore.saveBudget(context, b);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_WEEKEND_ON") || action.endsWith(".PET_WEEKEND_OFF")) {
            PetStore.setWeekendExtra(context, action.endsWith(".PET_WEEKEND_ON")
                    ? Budget.DEFAULT_WEEKEND_EXTRA : 0);
            context.startService(new Intent(context, HudService.class));
            return;
        }
        if (action.endsWith(".PET_GENTLE_ON") || action.endsWith(".PET_GENTLE_OFF")) {
            PetStore.setGentleMode(context, action.endsWith(".PET_GENTLE_ON"));
            return;
        }
        if (action.endsWith(".PET_STATE")) {
            PetState s = PetStore.loadState(context);
            Budget b = PetStore.loadBudget(context);
            android.util.Log.i("TVPILOT", "pet state exp=" + s.exp + " stars=" + s.stars
                    + " stage=" + s.stage() + " branch=" + s.branch + " mut=" + s.mutationCount
                    + " seen=" + s.mutationsSeen + " owned=" + s.owned + " decor=" + s.decor
                    + " degrade=" + s.degradeUntil + " degradeStage=" + s.degradeStage
                    + " sessions=" + b.sessionsUsed + "/" + b.maxSessions
                    + " gate=" + b.gateOpen + " reason=" + b.gateReason
                    + " window=" + b.windowEnabled
                    + " freePass=" + b.freePassDay
                    + " journal=" + PetStore.journal(context));
            return;
        }
        if (action.endsWith(".PET_REPORT")) {
            Intent report = new Intent(context, com.dsh.tvhud.pet.PetReportActivity.class);
            report.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                context.startActivity(report);
            } catch (Throwable ignored) {
            }
            return;
        }
        if (action.endsWith(".PET_ROOM")) {
            Intent room = new Intent(context, PetActivity.class);
            room.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(room);
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
