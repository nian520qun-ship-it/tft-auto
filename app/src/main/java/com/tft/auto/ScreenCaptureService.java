package com.tft.auto;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * 截屏服务 - 预留扩展
 * 如需图像识别功能，可通过 MediaProjection 实现
 * 当前版本使用无障碍服务节点分析，暂不需要截屏
 */
public class ScreenCaptureService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
