package com.tft.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
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

    public enum GameState {
        UNKNOWN, LOBBY, MATCHMAKING, IN_GAME, SHOP_PHASE, GAME_END, POPUP
    }

    private GameState currentState = GameState.UNKNOWN;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        handler = new Handler(Looper.getMainLooper());
        automator = new GameAutomator(this);
        isActive = true;

        MainActivity.log("✅ 无障碍服务已连接 " + screenWidth + "x" + screenHeight);
        Log.i(TAG, "Connected " + screenWidth + "x" + screenHeight);

        // 启动检测循环
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isActive) return;
                if (MainActivity.isRunning) {
                    doDetect();
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!MainActivity.isRunning) return;
        // 事件触发时也检测
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Window: " + pkg);
        }
    }

    @Override
    public void onInterrupt() {
        MainActivity.log("⚠️ 服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    private void doDetect() {
        // 获取根节点
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            MainActivity.log("⚠️ root=null");
            Log.w(TAG, "root is null");
            currentState = GameState.UNKNOWN;
            return;
        }

        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "null";
        Log.d(TAG, "root pkg: " + pkg);

        if (!pkg.equals(TFT_PACKAGE)) {
            // 不是金铲铲，跳过
            currentState = GameState.UNKNOWN;
            root.recycle();
            return;
        }

        // 收集文本
        List<String> texts = new ArrayList<>();
        grabTexts(root, texts);
        String all = String.join(" ", texts).toLowerCase();

        Log.d(TAG, "texts(" + texts.size() + "): " + all.substring(0, Math.min(300, all.length())));

        // 分类
        GameState old = currentState;
        if (all.contains("再来一局") || all.contains("返回大厅") || all.contains("对局结算")) {
            currentState = GameState.GAME_END;
        } else if (all.contains("开始游戏") || all.contains("开始匹配") || all.contains("排位赛")) {
            currentState = GameState.LOBBY;
        } else if (all.contains("匹配中") || all.contains("正在匹配")) {
            currentState = GameState.MATCHMAKING;
        } else if (all.contains("刷新") || all.contains("购买经验") || all.contains("金币")) {
            currentState = GameState.IN_GAME;
        } else if (all.contains("确定") || all.contains("确认") || all.contains("知道了")) {
            currentState = GameState.POPUP;
        }

        if (currentState != old) {
            MainActivity.log("🎮 状态: " + currentState.name());
            Log.i(TAG, "State: " + currentState);
        }

        root.recycle();

        // 执行操作
        automator.execute(currentState, screenWidth, screenHeight);
    }

    private void grabTexts(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        if (node.getText() != null) {
            String t = node.getText().toString().trim();
            if (!t.isEmpty()) out.add(t);
        }
        if (node.getContentDescription() != null) {
            String d = node.getContentDescription().toString().trim();
            if (!d.isEmpty()) out.add(d);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                grabTexts(child, out);
                child.recycle();
            }
        }
    }

    // ===== 手势操作 =====

    public void performClick(float px, float py) {
        int x = (int) (screenWidth * px);
        int y = (int) (screenHeight * py);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 100));
            dispatchGesture(b.build(), null, null);
        }
    }

    public void performDrag(float fx, float fy, float tx, float ty, long ms) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int x1 = (int) (screenWidth * fx), y1 = (int) (screenHeight * fy);
            int x2 = (int) (screenWidth * tx), y2 = (int) (screenHeight * ty);
            Path p = new Path();
            p.moveTo(x1, y1);
            p.lineTo(x2, y2);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, ms));
            dispatchGesture(b.build(), null, null);
        }
    }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        boolean ok = false;
        if (nodes != null) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isClickable()) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    ok = true;
                    break;
                }
                AccessibilityNodeInfo p = n.getParent();
                if (p != null && p.isClickable()) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    p.recycle();
                    ok = true;
                    break;
                }
            }
            for (AccessibilityNodeInfo n : nodes) n.recycle();
        }
        root.recycle();
        return ok;
    }

    public GameState getCurrentState() { return currentState; }
}
