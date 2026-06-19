package com.tft.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 金铲铲之战 - 无障碍自动化服务
 * 通过无障碍服务监控屏幕变化，执行自动化操作
 */
public class TFTAccessibilityService extends AccessibilityService {

    private static final String TAG = "TFTService";
    private static final String TFT_PACKAGE = "com.tencent.jkchess";

    private Handler handler;
    private int screenWidth, screenHeight;
    private GameAutomator automator;
    private boolean isActive = false;

    // 当前检测到的游戏状态
    public enum GameState {
        UNKNOWN,
        LOBBY,          // 大厅 - 可以开始游戏
        MATCHMAKING,    // 匹配中
        IN_GAME,        // 对局中
        SHOP_PHASE,     // 买牌阶段
        GAME_END,       // 对局结束
        POPUP           // 弹窗
    }

    private GameState currentState = GameState.UNKNOWN;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        automator = new GameAutomator(this);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        Log.i(TAG, "屏幕尺寸: " + screenWidth + "x" + screenHeight);
        MainActivity.log("🔧 无障碍服务已连接，屏幕: " + screenWidth + "x" + screenHeight);

        // 启动游戏状态检测循环
        startDetectionLoop();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!MainActivity.isRunning) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!pkg.equals(TFT_PACKAGE)) return;

        // 检测窗口变化
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            detectGameState();
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isActive = false;
        handler.removeCallbacksAndMessages(null);
        stopService(new Intent(this, FloatingButtonService.class));
    }

    /**
     * 定时检测游戏状态
     */
    private void startDetectionLoop() {
        isActive = true;
        Runnable detectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActive) return;
                if (MainActivity.isRunning) {
                    detectGameState();
                    automator.execute(currentState, screenWidth, screenHeight);
                }
                handler.postDelayed(this, 800); // 每800ms检测一次
            }
        };
        handler.post(detectRunnable);
    }

    /**
     * 检测当前游戏状态
     * 通过分析无障碍节点树来判断
     */
    private void detectGameState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            currentState = GameState.UNKNOWN;
            return;
        }

        String rootPkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        if (!rootPkg.equals(TFT_PACKAGE)) {
            currentState = GameState.UNKNOWN;
            return;
        }

        // 收集所有文本节点用于状态判断
        List<String> texts = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        collectNodeTexts(root, texts, descriptions);

        GameState newState = classifyState(texts, descriptions);
        if (newState != currentState) {
            currentState = newState;
            MainActivity.log("🎮 状态变更: " + currentState.name());
        }

        root.recycle();
    }

    /**
     * 递归收集所有节点的文本和描述
     */
    private void collectNodeTexts(AccessibilityNodeInfo node, List<String> texts, List<String> descriptions) {
        if (node == null) return;

        if (node.getText() != null) {
            texts.add(node.getText().toString().toLowerCase());
        }
        if (node.getContentDescription() != null) {
            descriptions.add(node.getContentDescription().toString().toLowerCase());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeTexts(child, texts, descriptions);
                child.recycle();
            }
        }
    }

    /**
     * 根据收集到的文本判断游戏状态
     */
    private GameState classifyState(List<String> texts, List<String> descriptions) {
        String allText = String.join(" ", texts) + " " + String.join(" ", descriptions);

        // 对局结束
        if (containsAny(allText, "再来一局", "play again", "返回大厅", "对局结算", "排名",
                "第一名", "第二名", "第三名", "第四名", "第5", "第6", "第7", "第8")) {
            return GameState.GAME_END;
        }

        // 大厅
        if (containsAny(allText, "开始游戏", "开始匹配", "排位赛", "匹配对战", "经典模式",
                "双人作战", "狂暴模式")) {
            return GameState.LOBBY;
        }

        // 匹配中
        if (containsAny(allText, "匹配中", "正在匹配", "取消匹配", "正在寻找")) {
            return GameState.MATCHMAKING;
        }

        // 弹窗
        if (containsAny(allText, "确定", "取消", "确认", "提示", "ok", "知道了")) {
            return GameState.POPUP;
        }

        // 对局中（有商店、棋盘等元素）
        if (containsAny(allText, "刷新", "购买经验", "金币", "阶段", "回合",
                "锁定", "利息")) {
            return GameState.IN_GAME;
        }

        return currentState; // 保持之前的状态
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 执行点击操作（基于屏幕百分比坐标）
     */
    public void performClick(float percentX, float percentY) {
        int x = (int) (screenWidth * percentX);
        int y = (int) (screenHeight * percentY);
        performClickAbsolute(x, y);
    }

    /**
     * 执行绝对坐标点击
     */
    public void performClickAbsolute(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
            dispatchGesture(builder.build(), null, null);
        }
    }

    /**
     * 执行长按（拖拽用）
     */
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
        }
    }

    /**
     * 点击包含特定文本的节点
     */
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
                    break;
                } else {
                    // 尝试点击父节点
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        clicked = true;
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
