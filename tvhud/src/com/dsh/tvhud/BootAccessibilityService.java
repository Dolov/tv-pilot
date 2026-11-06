package com.dsh.tvhud;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class BootAccessibilityService extends AccessibilityService {
    private static final String TAG = "TVHUD";
    private final Handler handler = new Handler();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "accessibility service connected -> start HUD");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = 0;
        info.feedbackType = 16;
        info.notificationTimeout = 100L;
        try {
            setServiceInfo(info);
        } catch (Throwable ignored) {
        }
        startHud();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startHud();
            }
        }, 8000L);
    }

    private void startHud() {
        try {
            startService(new Intent(this, HudService.class));
        } catch (Throwable t) {
            Log.w(TAG, "start HUD failed: " + t);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
