package com.tft.auto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.app.NotificationCompat;

/**
 * 悬浮控制按钮服务
 * 显示一个可拖拽的悬浮按钮用于控制自动化
 */
public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "tft_auto_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        createFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 创建简单的悬浮球视图
        floatingView = new View(this);
        floatingView.setBackgroundColor(0xFF4CAF50); // 绿色
        floatingView.setAlpha(0.8f);

        int size = dpToPx(48);

        params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dpToPx(200);

        // 拖拽 + 点击支持
        final float[] touchX = new float[1];
        final float[] touchY = new float[1];
        final int[] initialX = new int[1];
        final int[] initialY = new int[1];
        final boolean[] moved = new boolean[1];

        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchX[0] = event.getRawX();
                    touchY[0] = event.getRawY();
                    initialX[0] = params.x;
                    initialY[0] = params.y;
                    moved[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touchX[0];
                    float dy = event.getRawY() - touchY[0];
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        moved[0] = true;
                        params.x = initialX[0] + (int) dx;
                        params.y = initialY[0] + (int) dy;
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        // 点击 -> 切换状态
                        toggleAutomation();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(floatingView, params);
    }

    private void toggleAutomation() {
        MainActivity.isRunning = !MainActivity.isRunning;
        if (MainActivity.isRunning) {
            MainActivity.log("▶️ 已启动 (悬浮按钮)");
            floatingView.setBackgroundColor(0xFF4CAF50); // 绿色
        } else {
            MainActivity.log("⏸ 已暂停 (悬浮按钮)");
            floatingView.setBackgroundColor(0xFFF44336); // 红色
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "金铲铲自动服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("金铲铲之战自动化服务通知");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("金铲铲自动")
                .setContentText("自动化服务运行中...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
