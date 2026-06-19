package com.tft.auto;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class GameAutomator {

    private static final String TAG = "GameAutomator";

    private final TFTAccessibilityService service;
    private final Handler handler;

    private static final int CLICK_DELAY = 300;
    private static final int ACTION_DELAY = 1000;
    private static final int LONG_ACTION_DELAY = 2000;

    private long lastActionTime = 0;
    private TFTAccessibilityService.GameState lastState = TFTAccessibilityService.GameState.UNKNOWN;

    // 大厅
    private static final float LOBBY_START_X = 0.50f;
    private static final float LOBBY_START_Y = 0.85f;

    // 商店卡牌
    private static final float[][] SHOP_CARDS = {
        {0.10f, 0.92f}, {0.30f, 0.92f}, {0.50f, 0.92f},
        {0.70f, 0.92f}, {0.90f, 0.92f}
    };

    // 刷新按钮
    private static final float REFRESH_X = 0.88f;
    private static final float REFRESH_Y = 0.75f;

    // 买经验
    private static final float BUY_XP_X = 0.12f;
    private static final float BUY_XP_Y = 0.75f;

    // 板凳
    private static final float BENCH_Y = 0.82f;
    private static final float[] BENCH_X = {0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 0.10f};

    // 棋盘
    private static final float BOARD_START_X = 0.18f;
    private static final float BOARD_END_X = 0.82f;
    private static final float BOARD_ROW1_Y = 0.35f;
    private static final float BOARD_ROW2_Y = 0.45f;
    private static final float BOARD_ROW3_Y = 0.55f;

    // 对局结束
    private static final float PLAY_AGAIN_X = 0.50f;
    private static final float PLAY_AGAIN_Y = 0.75f;

    // 弹窗
    private static final float POPUP_CONFIRM_X = 0.55f;
    private static final float POPUP_CONFIRM_Y = 0.60f;

    private int benchIndex = 0;
    private int boardRow = 0;
    private int boardCol = 0;
    private int buyCount = 0;
    private int roundCount = 0;

    public GameAutomator(TFTAccessibilityService service) {
        this.service = service;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void execute(TFTAccessibilityService.GameState state, int screenW, int screenH) {
        long now = System.currentTimeMillis();

        if (state != lastState) {
            onStateChanged(state);
        }

        if (now - lastActionTime < getDelayForState(state)) {
            return;
        }

        if (state == TFTAccessibilityService.GameState.UNKNOWN) {
            return;
        }

        lastActionTime = now;
        Log.d(TAG, "Executing action for state: " + state);

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
        Log.i(TAG, "State changed: " + lastState + " → " + newState);
        lastState = newState;
        if (newState == TFTAccessibilityService.GameState.LOBBY) {
            benchIndex = 0;
            boardRow = 0;
            boardCol = 0;
            buyCount = 0;
            roundCount = 0;
        }
        if (newState == TFTAccessibilityService.GameState.IN_GAME) {
            roundCount++;
        }
    }

    private int getDelayForState(TFTAccessibilityService.GameState state) {
        switch (state) {
            case LOBBY: return ACTION_DELAY;
            case MATCHMAKING: return 3000;
            case IN_GAME: return 1500;
            case SHOP_PHASE: return CLICK_DELAY;
            case GAME_END: return LONG_ACTION_DELAY;
            case POPUP: return 500;
            default: return 2000;
        }
    }

    private void handleLobby() {
        Log.i(TAG, "handleLobby: trying to start game");
        MainActivity.log("🎯 大厅 - 开始游戏");
        boolean clicked = service.clickText("开始游戏");
        if (!clicked) {
            clicked = service.clickText("开始匹配");
        }
        if (!clicked) {
            service.performClick(LOBBY_START_X, LOBBY_START_Y);
            MainActivity.log("🎯 点击坐标 (" + LOBBY_START_X + ", " + LOBBY_START_Y + ")");
        }
    }

    private void handleMatchmaking() {
        // 等待
    }

    private void handleInGame() {
        roundCount++;
        Log.i(TAG, "handleInGame: round " + roundCount);
        MainActivity.log("🎮 回合 " + roundCount);

        if (roundCount % 3 == 0) {
            handler.postDelayed(() -> {
                service.performClick(BUY_XP_X, BUY_XP_Y);
                MainActivity.log("📈 买经验");
            }, 200);
        }

        buyAllShopCards();
        handler.postDelayed(this::placeBenchesOnBoard, 1500);
    }

    private void handleShopPhase() {
        buyAllShopCards();
        handler.postDelayed(this::placeBenchesOnBoard, 1000);
    }

    private void buyAllShopCards() {
        for (int i = 0; i < SHOP_CARDS.length; i++) {
            final int idx = i;
            handler.postDelayed(() -> {
                service.performClick(SHOP_CARDS[idx][0], SHOP_CARDS[idx][1]);
            }, (long) i * CLICK_DELAY);
        }
        buyCount += SHOP_CARDS.length;
        MainActivity.log("🛒 买牌 x" + SHOP_CARDS.length);

        handler.postDelayed(() -> {
            service.performClick(REFRESH_X, REFRESH_Y);
            MainActivity.log("🔄 刷新商店");
        }, SHOP_CARDS.length * CLICK_DELAY + 200);
    }

    private void placeBenchesOnBoard() {
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
                service.performDrag(BENCH_X[bi], BENCH_Y, bx, by, 500);
                MainActivity.log("♟️ 放棋 [" + row + "," + col + "]");
            }, (long) i * LONG_ACTION_DELAY);

            benchIndex++;
            boardCol++;
            if (boardCol >= 7) {
                boardCol = 0;
                boardRow++;
                if (boardRow >= 3) boardRow = 0;
            }
        }
    }

    private void handleGameEnd() {
        Log.i(TAG, "handleGameEnd: starting next game");
        MainActivity.log("🏆 对局结束 - 下一局");

        boolean clicked = service.clickText("再来一局");
        if (!clicked) clicked = service.clickText("play again");
        if (!clicked) clicked = service.clickText("返回大厅");
        if (!clicked) service.performClick(PLAY_AGAIN_X, PLAY_AGAIN_Y);

        benchIndex = 0;
        boardRow = 0;
        boardCol = 0;
        buyCount = 0;
    }

    private void handlePopup() {
        Log.i(TAG, "handlePopup: dismissing");
        MainActivity.log("💬 关闭弹窗");

        boolean clicked = service.clickText("确定");
        if (!clicked) clicked = service.clickText("确认");
        if (!clicked) clicked = service.clickText("知道了");
        if (!clicked) service.performClick(POPUP_CONFIRM_X, POPUP_CONFIRM_Y);
    }
}
