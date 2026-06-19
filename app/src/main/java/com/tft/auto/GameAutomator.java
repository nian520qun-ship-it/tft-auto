package com.tft.auto;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 游戏自动化逻辑
 * 根据游戏状态执行相应操作
 */
public class GameAutomator {

    private static final String TAG = "GameAutomator";

    private final TFTAccessibilityService service;
    private final Handler handler;

    // 操作间隔 (ms)
    private static final int CLICK_DELAY = 300;
    private static final int ACTION_DELAY = 1000;
    private static final int LONG_ACTION_DELAY = 2000;

    // 上次操作时间，防止重复点击
    private long lastActionTime = 0;
    private TFTAccessibilityService.GameState lastState = TFTAccessibilityService.GameState.UNKNOWN;

    // ============== 坐标配置 (屏幕百分比) ==============
    // 这些坐标基于 16:9 / 20:9 屏幕的金铲铲之战 UI 布局

    // 大厅 - 开始游戏按钮
    private static final float LOBBY_START_X = 0.50f;
    private static final float LOBBY_START_Y = 0.85f;

    // 大厅 - 选择模式后确认
    private static final float MODE_CONFIRM_X = 0.50f;
    private static final float MODE_CONFIRM_Y = 0.75f;

    // 匹配中 - 取消按钮位置 (用于检测)
    private static final float MATCH_CANCEL_X = 0.50f;
    private static final float MATCH_CANCEL_Y = 0.85f;

    // 商店 - 5张卡牌位置 (从左到右)
    private static final float[][] SHOP_CARDS = {
        {0.10f, 0.92f}, {0.30f, 0.92f}, {0.50f, 0.92f},
        {0.70f, 0.92f},  {0.90f, 0.92f}
    };

    // 刷新按钮
    private static final float REFRESH_X = 0.88f;
    private static final float REFRESH_Y = 0.75f;

    // 购买经验按钮
    private static final float BUY_XP_X = 0.12f;
    private static final float BUY_XP_Y = 0.75f;

    // 候选席位(板凳) Y 坐标
    private static final float BENCH_Y = 0.82f;
    private static final float[] BENCH_X = {0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 0.10f};

    // 棋盘放牌位置 (3行x7列)
    private static final float BOARD_START_X = 0.18f;
    private static final float BOARD_END_X = 0.82f;
    private static final float BOARD_ROW1_Y = 0.35f;
    private static final float BOARD_ROW2_Y = 0.45f;
    private static final float BOARD_ROW3_Y = 0.55f;

    // 对局结束 - 再来一局
    private static final float PLAY_AGAIN_X = 0.50f;
    private static final float PLAY_AGAIN_Y = 0.75f;

    // 对局结束 - 返回大厅
    private static final float BACK_LOBBY_X = 0.30f;
    private static final float BACK_LOBBY_Y = 0.80f;

    // 弹窗确认按钮
    private static final float POPUP_CONFIRM_X = 0.55f;
    private static final float POPUP_CONFIRM_Y = 0.60f;

    // 当前板凳索引（用于放牌）
    private int benchIndex = 0;
    // 当前棋盘行
    private int boardRow = 0;
    // 当前棋盘列
    private int boardCol = 0;
    // 买牌计数
    private int buyCount = 0;
    // 对局中阶段计数
    private int roundCount = 0;

    public GameAutomator(TFTAccessibilityService service) {
        this.service = service;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * 根据游戏状态执行自动化操作
     */
    public void execute(TFTAccessibilityService.GameState state, int screenW, int screenH) {
        long now = System.currentTimeMillis();

        // 状态切换时重置计数
        if (state != lastState) {
            onStateChanged(state);
        }

        // 操作节流
        if (now - lastActionTime < getDelayForState(state)) {
            return;
        }

        // 检查目标应用是否在前台
        if (state == TFTAccessibilityService.GameState.UNKNOWN) {
            return;
        }

        lastActionTime = now;

        switch (state) {
            case LOBBY:
                handleLobby();
                break;
            case MATCHMAKING:
                handleMatchmaking();
                break;
            case IN_GAME:
                handleInGame();
                break;
            case SHOP_PHASE:
                handleShopPhase();
                break;
            case GAME_END:
                handleGameEnd();
                break;
            case POPUP:
                handlePopup();
                break;
        }
    }

    private void onStateChanged(TFTAccessibilityService.GameState newState) {
        lastState = newState;
        switch (newState) {
            case LOBBY:
                benchIndex = 0;
                boardRow = 0;
                boardCol = 0;
                buyCount = 0;
                roundCount = 0;
                break;
            case IN_GAME:
                roundCount++;
                break;
        }
    }

    private int getDelayForState(TFTAccessibilityService.GameState state) {
        switch (state) {
            case LOBBY: return ACTION_DELAY;
            case MATCHMAKING: return 3000; // 匹配中少操作
            case IN_GAME: return 1500;
            case SHOP_PHASE: return CLICK_DELAY;
            case GAME_END: return LONG_ACTION_DELAY;
            case POPUP: return 500;
            default: return 2000;
        }
    }

    /**
     * 大厅处理 - 点击开始游戏
     */
    private void handleLobby() {
        MainActivity.log("🎯 大厅 - 尝试开始游戏");
        // 先尝试通过文本点击
        boolean clicked = service.clickNodeByText("开始游戏");
        if (!clicked) {
            clicked = service.clickNodeByText("开始匹配");
        }
        if (!clicked) {
            // 回退到坐标点击
            service.performClick(LOBBY_START_X, LOBBY_START_Y);
        }
    }

    /**
     * 匹配中 - 等待，不做操作
     */
    private void handleMatchmaking() {
        // 匹配中不需要操作，等待
        MainActivity.log("⏳ 匹配中...");
    }

    /**
     * 对局中 - 自动买牌、升级、刷新
     */
    private void handleInGame() {
        roundCount++;
        MainActivity.log("🎮 对局中 (回合 " + roundCount + ") - 执行自动化");

        // 策略：
        // 1. 如果金币>=4，买经验
        // 2. 尝试购买商店里的牌
        // 3. 将板凳上的牌放到棋盘

        // 买经验
        if (roundCount % 3 == 0) {
            handler.postDelayed(() -> {
                service.performClick(BUY_XP_X, BUY_XP_Y);
                MainActivity.log("📈 购买经验");
            }, 200);
        }

        // 买牌
        buyAllShopCards();

        // 延迟后放牌
        handler.postDelayed(this::placeBenchesOnBoard, 1500);
    }

    /**
     * 买牌阶段 - 购买商店中的所有卡牌
     */
    private void handleShopPhase() {
        buyAllShopCards();
        handler.postDelayed(this::placeBenchesOnBoard, 1000);
    }

    /**
     * 购买商店所有卡牌
     */
    private void buyAllShopCards() {
        for (int i = 0; i < SHOP_CARDS.length; i++) {
            final int idx = i;
            handler.postDelayed(() -> {
                service.performClick(SHOP_CARDS[idx][0], SHOP_CARDS[idx][1]);
                MainActivity.log("🛒 购买卡牌 " + (idx + 1));
            }, (long) i * CLICK_DELAY);
        }
        buyCount += SHOP_CARDS.length;

        // 购买后刷新商店
        handler.postDelayed(() -> {
            service.performClick(REFRESH_X, REFRESH_Y);
            MainActivity.log("🔄 刷新商店");
        }, SHOP_CARDS.length * CLICK_DELAY + 200);
    }

    /**
     * 将板凳上的棋子放到棋盘上
     */
    private void placeBenchesOnBoard() {
        // 尝试从板凳拖到棋盘
        // 每次放一个，间隔一定时间
        for (int i = 0; i < 3 && benchIndex < BENCH_X.length; i++) {
            final int bi = benchIndex;
            final int row = boardRow;
            final int col = boardCol;

            float boardX = BOARD_START_X + (BOARD_END_X - BOARD_START_X) * col / 6f;
            float boardY;
            switch (row) {
                case 0: boardY = BOARD_ROW1_Y; break;
                case 1: boardY = BOARD_ROW2_Y; break;
                default: boardY = BOARD_ROW3_Y; break;
            }

            final float bx = boardX;
            final float by = boardY;

            handler.postDelayed(() -> {
                // 长按拖拽：从板凳拖到棋盘
                service.performLongClick(BENCH_X[bi], BENCH_Y, bx, by, 500);
                MainActivity.log("♟️ 放置棋子: 板凳" + bi + " → 棋盘[" + row + "," + col + "]");
            }, (long) i * LONG_ACTION_DELAY);

            benchIndex++;
            boardCol++;
            if (boardCol >= 7) {
                boardCol = 0;
                boardRow++;
                if (boardRow >= 3) {
                    boardRow = 0; // 循环放置
                }
            }
        }
    }

    /**
     * 对局结束 - 再来一局
     */
    private void handleGameEnd() {
        MainActivity.log("🏆 对局结束 - 开始下一局");

        // 优先尝试文本点击
        boolean clicked = service.clickNodeByText("再来一局");
        if (!clicked) {
            clicked = service.clickNodeByText("play again");
        }
        if (!clicked) {
            // 尝试返回大厅再开始
            clicked = service.clickNodeByText("返回大厅");
            if (!clicked) {
                service.performClick(PLAY_AGAIN_X, PLAY_AGAIN_Y);
            }
        }

        // 重置状态
        benchIndex = 0;
        boardRow = 0;
        boardCol = 0;
        buyCount = 0;
    }

    /**
     * 弹窗处理 - 点击确认
     */
    private void handlePopup() {
        MainActivity.log("💬 弹窗 - 尝试关闭");
        boolean clicked = service.clickNodeByText("确定");
        if (!clicked) {
            clicked = service.clickNodeByText("确认");
        }
        if (!clicked) {
            clicked = service.clickNodeByText("知道了");
        }
        if (!clicked) {
            service.performClick(POPUP_CONFIRM_X, POPUP_CONFIRM_Y);
        }
    }
}
