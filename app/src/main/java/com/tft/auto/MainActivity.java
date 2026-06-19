package com.tft.auto;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TFT-Main";

    private TextView tvStatus;
    private TextView tvLog;
    private Button btnToggle;
    private ScrollView scrollLog;

    public static boolean isRunning = false;
    public static StringBuilder logBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        btnToggle = findViewById(R.id.btn_toggle);
        scrollLog = findViewById(R.id.scroll_log);
        Button btnPerm = findViewById(R.id.btn_permissions);

        btnToggle.setOnClickListener(v -> toggleService());
        btnPerm.setOnClickListener(v -> openPermissions());

        log("应用启动");
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        refreshLog();
    }

    private void toggleService() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }

        isRunning = !isRunning;
        if (isRunning) {
            log("▶️ 自动化已启动");
            log("请切换到金铲铲之战");
            startService(new Intent(this, FloatingButtonService.class));
        } else {
            log("⏸ 自动化已停止");
            stopService(new Intent(this, FloatingButtonService.class));
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean accEnabled = isAccessibilityEnabled();
        boolean overlayEnabled = Settings.canDrawOverlays(this);

        StringBuilder sb = new StringBuilder();
        sb.append("无障碍服务: ").append(accEnabled ? "✅ 已开启" : "❌ 未开启").append("\n");
        sb.append("悬浮窗权限: ").append(overlayEnabled ? "✅ 已授予" : "❌ 未授予").append("\n");
        sb.append("自动化: ").append(isRunning ? "🟢 运行中" : "🔴 已停止").append("\n");
        sb.append("目标: 金铲铲之战 (com.tencent.jkchess)");
        tvStatus.setText(sb.toString());

        btnToggle.setText(isRunning ? "⏸ 停止" : "▶️ 启动");

        // 每次回到界面刷新日志
        refreshLog();
    }

    private void refreshLog() {
        tvLog.setText(logBuffer.toString());
        scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void openPermissions() {
        if (!isAccessibilityEnabled()) {
            openAccessibilitySettings();
        } else if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            Toast.makeText(this, "所有权限已授予 ✅", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    public static void log(String msg) {
        String time = java.text.SimpleDateFormat.getTimeInstance(java.text.SimpleDateFormat.SHORT)
                .format(new java.util.Date());
        String line = "[" + time + "] " + msg + "\n";
        logBuffer.append(line);
        Log.i(TAG, msg);
        if (logBuffer.length() > 5000) {
            logBuffer.delete(0, logBuffer.length() - 5000);
        }
    }
}
