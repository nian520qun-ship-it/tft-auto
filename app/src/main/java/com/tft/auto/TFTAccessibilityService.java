package com.tft.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class TFTAccessibilityService extends AccessibilityService {

    private static final String TAG = "TFTService";
    private static final String TFT_PACKAGE = "com.tencent.jkchess";

    private Handler handler;
    private int screenWidth, screenHeight;
    private GameAutomator automator;
    private boolean isActive = false;
    private int detectCount = 0;

    public enum GameState {
        UNKNOWN, LOBBY, MATCHMAKING, IN_GAME, SHOP_PHASE, GAME_END, POPUP
    }

    private GameState currentState = GameState.UNKNOWN;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        automator = new GameAutomator(this);
        Log.i(TAG, "Service onCreate");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        Log.i(TAG, "Service connected, screen: " + screenWidth + "x" + screenHeight);
        MainActivity.log("🔧 无障碍服务已连接");
        MainActivity.log("📱 屏幕: " + screenWidth + "x" + screenHeight);
        startDetectionLoop();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!MainActivity.isRunning) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        int type = event.getEventType();

        // 记录所有收到的事件
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Window changed: " + pkg);
            if (pkg.equals(TFT_PACKAGE)) {
                detectGameState();
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
        MainActivity.log("⚠️ 无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isActive = false;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Service destroyed");
    }

    private void startDetectionLoop() {
        isActive = true;
        Runnable detectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActive) return;
                if (MainActivity.isRunning) {
                    detectCount++;
                    detectGameState();
                    automator.execute(currentState, screenWidth, screenHeight);
                    if (detectCount % 10 == 1) { // 每8秒打一次日志
                        Log.d(TAG, "Detection #" + detectCount + ", state=" + currentState);
                    }
                }
                handler.postDelayed(this, 800);
            }
        };
        handler.post(detectRunnable);
        Log.i(TAG, "Detection loop started");
    }

    private void detectGameState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (detectCount % 10 == 1) {
                Log.w(TAG, "Root window is null");
                MainActivity.log("⚠️ 无法获取当前窗口");
            }
            currentState = GameState.UNKNOWN;
            return;
        }

        String rootPkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (!rootPkg.equals(TFT_PACKAGE)) {
            if (detectCount % 10 == 1) {
                Log.d(TAG, "Current app: " + rootPkg + " (not TFT)");
                MainActivity.log("📱 当前应用: " + rootPkg);
            }
            currentState = GameState.UNKNOWN;
            root.recycle();
            return;
        }

        // 收集所有文本
        List<String> texts = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        collectNodeTexts(root, texts, descriptions);

        String allText = String.join(" ", texts) + " " + String.join(" ", descriptions);
        Log.d(TAG, "Collected texts (" + texts.size() + "): " + allText.substring(0, Math.min(200, allText.length())));

        GameState newState = classifyState(allText);
        if (newState != currentState) {
            MainActivity.log("🎮 状态: " + currentState.name() + " → " + newState.name());
            currentState = newState;
        }

        root.recycle();
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, List<String> texts, List<String> descriptions) {
        if (node == null) return;

        if (node.getText() != null) {
            String t = node.getText().toString().trim();
            if (!t.isEmpty()) texts.add(t.toLowerCase());
        }
        if (node.getContentDescription() != null) {
            String d = node.getContentDescription().toString().trim();
            if (!d.isEmpty()) descriptions.add(d.toLowerCase());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeTexts(child, texts, descriptions);
                child.recycle();
            }
        }
    }

    private GameState classifyState(String allText) {
        // 对局结束
        if (containsAny(allText, "再来一局", "play again", "返回大厅", "对局结算",
                "第一名", "第二名", "第三名", "第四名")) {
            return GameState.GAME_END;
        }

        // 大厅
        if (containsAny(allText, "开始游戏", "开始匹配", "排位赛", "匹配对战", "经典模式")) {
            return GameState.LOBBY;
        }

        // 匹配中
        if (containsAny(allText, "匹配中", "正在匹配", "取消匹配")) {
            return GameState.MATCHMAKING;
        }

        // 对局中
        if (containsAny(allText, "刷新", "购买经验", "金币", "阶段", "回合", "锁定", "利息")) {
            return GameState.IN_GAME;
        }

        // 弹窗
        if (containsAny(allText, "确定", "取消", "确认", "知道了")) {
            return GameState.POPUP;
        }

        return currentState;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    public void performClick(float percentX, float percentY) {
        int x = (int) (screenWidth * percentX);
        int y = (int) (screenHeight * percentY);
        performClickAbsolute(x, y);
    }

    public void performClickAbsolute(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
            dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "Click at (" + x + ", " + y + ")");
        }
    }

    public void performLongClick(float fromX, float fromY, float toX, float toY, long durationMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int fx = (int) (screenWidth * fromX);
            int fy = (int) (screenHeight * fromY);
            int tx = (int) (screenWidth * toX);
            int ty = (int) (screenHeight * toY);

            Path path = new Path();
            path.moveTo(fx, fy);
            path.lineTo(tx, ty);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
            dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "Drag (" + fx + "," + fy + ") → (" + tx + "," + ty + ")");
        }
    }

    public boolean clickNodeByText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        boolean clicked = false;
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    clicked = true;
                    Log.d(TAG, "Clicked node with text: " + text);
                    break;
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        clicked = true;
                        Log.d(TAG, "Clicked parent of node with text: " + text);
                        parent.recycle();
                        break;
                    }
                }
            }
            for (AccessibilityNodeInfo n : nodes) n.recycle();
        }
        root.recycle();
        return clicked;
    }

    public GameState getCurrentState() { return currentState; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
}
