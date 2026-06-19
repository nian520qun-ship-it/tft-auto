package com.tft.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

public class TFTAccessibilityService extends AccessibilityService {

    private static final String TAG = "TFTService";
    private static final String TFT_PACKAGE = "com.tencent.jkchess";

    private Handler handler;
    private int screenWidth, screenHeight;
    private GameAutomator automator;
    private boolean isActive = false;
    private int failCount = 0;

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

        // 尝试开启窗口检索
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                setServiceInfo(info);
                Log.i(TAG, "Added FLAG_RETRIEVE_INTERACTIVE_WINDOWS");
            }
        } catch (Exception e) {
            Log.w(TAG, "Flag not supported: " + e.getMessage());
        }

        handler = new Handler(Looper.getMainLooper());
        automator = new GameAutomator(this);
        isActive = true;

        MainActivity.log("✅ 无障碍已连接 " + screenWidth + "x" + screenHeight);
        Log.i(TAG, "Connected " + screenWidth + "x" + screenHeight);

        // 延迟2秒再启动检测，等窗口稳定
        handler.postDelayed(this::startLoop, 2000);
    }

    private void startLoop() {
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
        Log.i(TAG, "Detection loop started");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不依赖事件驱动，用轮询
    }

    @Override
    public void onInterrupt() {
        MainActivity.log("⚠️ 服务中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    /**
     * 获取根节点 - 多种方式尝试
     */
    private AccessibilityNodeInfo findRoot() {
        // 方式1: getRootInActiveWindow
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                if (pkg.equals(TFT_PACKAGE)) {
                    Log.d(TAG, "getRootInActiveWindow → TFT ✓");
                    return root;
                }
                // 不是金铲铲，但至少能拿到窗口
                Log.d(TAG, "getRootInActiveWindow → " + pkg);
                root.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "getRootInActiveWindow failed: " + e.getMessage());
        }

        // 方式2: getWindows() 遍历
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                Log.d(TAG, "getWindows: " + windows.size() + " windows");
                for (int i = 0; i < windows.size(); i++) {
                    AccessibilityWindowInfo w = windows.get(i);
                    try {
                        AccessibilityNodeInfo wRoot = w.getRoot();
                        if (wRoot != null) {
                            String pkg = wRoot.getPackageName() != null ? wRoot.getPackageName().toString() : "";
                            Log.d(TAG, "  window[" + i + "] type=" + w.getType() + " pkg=" + pkg);
                            if (pkg.equals(TFT_PACKAGE)) {
                                Log.d(TAG, "Found TFT in window[" + i + "] ✓");
                                return wRoot;
                            }
                            wRoot.recycle();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "  window[" + i + "] error: " + e.getMessage());
                    }
                }
            } else {
                Log.d(TAG, "getWindows: empty");
            }
        } catch (Exception e) {
            Log.w(TAG, "getWindows failed: " + e.getMessage());
        }

        // 方式3: 再试一次getRootInActiveWindow（不检查包名）
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                Log.d(TAG, "getRootInActiveWindow (any): got it");
                return root;
            }
        } catch (Exception e) {
            Log.w(TAG, "getRootInActiveWindow retry failed");
        }

        return null;
    }

    private void doDetect() {
        AccessibilityNodeInfo root = findRoot();

        if (root == null) {
            failCount++;
            if (failCount <= 3 || failCount % 10 == 0) {
                MainActivity.log("⚠️ 无法获取窗口 (第" + failCount + "次)");
                Log.w(TAG, "root is null #" + failCount);
            }
            currentState = GameState.UNKNOWN;
            return;
        }

        failCount = 0;
        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";

        if (!pkg.equals(TFT_PACKAGE)) {
            if (currentState != GameState.UNKNOWN) {
                MainActivity.log("📱 当前: " + pkg);
            }
            currentState = GameState.UNKNOWN;
            root.recycle();
            return;
        }

        // 收集文本
        List<String> texts = new ArrayList<>();
        grabTexts(root, texts);
        String all = String.join(" ", texts).toLowerCase();

        if (texts.isEmpty()) {
            Log.d(TAG, "No texts found in TFT window");
        } else {
            Log.d(TAG, "texts(" + texts.size() + "): " + all.substring(0, Math.min(300, all.length())));
        }

        // 分类状态
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
            MainActivity.log("🎮 状态: " + old + " → " + currentState);
            Log.i(TAG, "State: " + currentState);
        }

        root.recycle();

        // 执行操作
        automator.execute(currentState, screenWidth, screenHeight);
    }

    private void grabTexts(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        try {
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
        } catch (Exception e) {
            Log.w(TAG, "grabTexts error: " + e.getMessage());
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
            Log.d(TAG, "Click (" + x + "," + y + ")");
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
            Log.d(TAG, "Drag (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")");
        }
    }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = findRoot();
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
