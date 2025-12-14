package org.epee.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class FencingClientApp extends Application {

    private String roomName;
    private String nickname;
    private String playerId;

    public String getPlayerId() {
        return playerId;
    }

    private Canvas canvas;
    private GraphicsContext g;

    private double x = 100;
    private double y = 400;
    private boolean facingRight = true;

    private boolean attacking = false;
    private double attackProgress = 0.0;
    private double bladeOffset = 0.0;

    private final Set<KeyCode> pressedOnce = new HashSet<>();

    private GameState latestState;
    private GameState previousState;
    private GameWebSocketClient wsClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private Image imgIdle;
    private Image imgForward;
    private Image imgAttack;
    private Image imgBackground;

    private Label lblScore1;
    private Label lblScore2;
    private Label lblName1;
    private Label lblName2;

    private final Map<String, Long> lastAttackTimeMap = new HashMap<>();
    private final Map<String, Long> lastForwardTimeMap = new HashMap<>();

    private ChatPanel chatPanel;
    private Stage primaryStage;

    private WaitingRoomPanel waitingRoomPanel;
    private BorderPane root;
    private javafx.scene.layout.StackPane canvasContainer;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        new LoginScreen(stage, this::startGame).show();
    }

    private void startGame(String nickname, String roomName, boolean isCreator) {
        this.nickname = nickname;
        this.roomName = roomName;

        try {
            imgIdle = new Image(getClass().getResourceAsStream("/fencing1.png"));
            imgForward = new Image(getClass().getResourceAsStream("/fencing2.png"));
            imgAttack = new Image(getClass().getResourceAsStream("/fencing3.png"));
            imgBackground = new Image(getClass().getResourceAsStream("/background.png"));
        } catch (Exception e) {
            System.err.println("Failed to load images: " + e.getMessage());
        }

        canvas = new Canvas();
        g = canvas.getGraphicsContext2D();

        canvasContainer = new StackPane(canvas);
        // canvasContainer.setStyle("-fx-background-color: #2F4F4F;"); // 이미지 배경 사용으로
        // 제거됨
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        root = new BorderPane();
        chatPanel = new ChatPanel(this);

        // Create UI Overlay
        HBox scoreBoard = createScoreBoard();
        HBox bottomBar = createBottomBar();

        BorderPane uiOverlay = new BorderPane();
        uiOverlay.setTop(scoreBoard);
        uiOverlay.setBottom(bottomBar);
        uiOverlay.setPickOnBounds(false); // 클릭이 캔버스로 전달되도록 설정

        StackPane mainStack = new StackPane(canvasContainer, uiOverlay);

        double initialHeight = isCreator ? 700 : 600; // UI 공간 확보를 위해 높이 증가
        Scene scene = new Scene(root, 1000, initialHeight);
        scene.getStylesheets().add(getClass().getResource("/ui_styles.css").toExternalForm());

        primaryStage.setTitle("ÉPÉE Client - " + nickname);
        primaryStage.setScene(scene);

        setupInputHandlers(scene);
        startGameLoop();
        connectWebSocket();

        if (isCreator) {
            waitingRoomPanel = new WaitingRoomPanel(roomName, nickname, () -> {
                try {
                    stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                new LoginScreen(primaryStage, this::startGame).show();
            });

            root.setCenter(waitingRoomPanel.getView());
        } else {
            root.setCenter(mainStack);
            root.setRight(chatPanel.getView());
            canvas.requestFocus();
            canvas.setOnMouseClicked(e -> canvas.requestFocus()); // 클릭 시 포커스 획득
        }
    }

    public void requestGameFocus() {
        if (canvas != null) {
            canvas.requestFocus();
        }
    }

    private HBox createScoreBoard() {
        HBox topBox = new HBox(20);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(20));
        topBox.getStyleClass().add("scoreboard-container");

        lblName1 = new Label("Player 1");
        lblName1.getStyleClass().add("player-name");

        lblScore1 = new Label("0");
        lblScore1.getStyleClass().add("score-label");
        VBox box1 = new VBox(lblScore1);
        box1.getStyleClass().add("score-box");

        Label vs = new Label("VS");
        vs.getStyleClass().add("vs-label");

        lblScore2 = new Label("0");
        lblScore2.getStyleClass().add("score-label");
        VBox box2 = new VBox(lblScore2);
        box2.getStyleClass().add("score-box");

        lblName2 = new Label("Player 2");
        lblName2.getStyleClass().add("player-name");

        topBox.getChildren().addAll(lblName1, box1, vs, box2, lblName2);

        // 라운드 정보는 추후 추가 가능

        return topBox;
    }

    private HBox createBottomBar() {
        HBox bottomBox = new HBox(40);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(15));
        bottomBox.getStyleClass().add("controls-container");

        bottomBox.getChildren().addAll(
                createControlGroup("< >", "이동", "A", "D"),
                createControlGroup("⚡", "찌르기", "J"));
        return bottomBox;
    }

    private HBox createControlGroup(String icon, String label, String... keys) {
        HBox group = new HBox(10);
        group.setAlignment(Pos.CENTER_LEFT);
        group.getStyleClass().add("control-group");

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("icon-arrow");

        Label textLbl = new Label(label);
        textLbl.getStyleClass().add("action-label");

        HBox keysBox = new HBox(5);
        keysBox.setAlignment(Pos.CENTER);
        for (String key : keys) {
            Label keyLbl = new Label(key);
            keyLbl.getStyleClass().add("key-box");
            keysBox.getChildren().add(keyLbl);
        }

        group.getChildren().addAll(iconLbl, textLbl, keysBox);
        return group;
    }

    private int attackAttempts = 0;

    private void setupInputHandlers(Scene scene) {

        scene.setOnMouseClicked(e -> {
            if (root.getCenter() == canvasContainer)
                canvas.requestFocus();
        });

        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();

            // 이미 눌린 키면 무시 (1회 입력만)
            if (pressedOnce.contains(code))
                return;

            pressedOnce.add(code);

            // === 이동 (A/D 1회당 30px) ===
            if (code == KeyCode.A) {
                // facingRight = false; // 방향 전환 안함
                x -= 30;
                // P2 즉시 반응 (A가 전진)
                if (playerId != null && "p2".equals(playerId)) {
                    lastForwardTimeMap.put(playerId, System.currentTimeMillis());
                }
            }
            if (code == KeyCode.D) {
                // facingRight = true; // 방향 전환 안함
                x += 30;
                // P1 즉시 반응 (D가 전진)
                if (playerId != null && "p1".equals(playerId)) {
                    lastForwardTimeMap.put(playerId, System.currentTimeMillis());
                }
            }

            // === 공격 ===
            if (code == KeyCode.J && !attacking) {
                attacking = true;
                attackProgress = 0.0;
                attackAttempts++;

                sendMsg(new Msg("attack", roomName, playerId, nickname, x, y, facingRight, true, null));

                // 시스템 메시지: 공격 시도 (클라이언트 예측)
                // 빗나갈 것으로 예상될 때만 표시 (단순 거리 체크)
                // 리치 = 70, Y 차이 < 40
                boolean likelyHit = false;
                if (latestState != null) {
                    Player me = "p1".equals(playerId) ? latestState.p1() : latestState.p2();
                    Player other = "p1".equals(playerId) ? latestState.p2() : latestState.p1();

                    if (me != null && other != null) {
                        double reach = 70;
                        double tip = me.facingRight() ? me.x() + reach : me.x() - reach;
                        if (Math.abs(tip - other.x()) < 20 && Math.abs(me.y() - other.y()) < 40) {
                            likelyHit = true;
                        }
                    }
                }

                if (!likelyHit && chatPanel != null) {
                    String name = nickname;
                    if (name == null || name.isEmpty())
                        name = playerId;

                    String color = "p1".equals(playerId) ? "#00BFFF" : "#FA8072"; // Brighter Blue
                    chatPanel.appendSystemMessageWithHighlight("", name, " 공격 시도!", color);
                }
            }
        });

        scene.setOnKeyReleased(e -> {
            pressedOnce.remove(e.getCode());
        });
    }

    private void startGameLoop() {
        AnimationTimer loop = new AnimationTimer() {
            long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }

                double dt = (now - lastTime) / 1e9;
                lastTime = now;

                update(dt);
                render();
            }
        };
        loop.start();
    }

    private void connectWebSocket() {
        try {
            wsClient = new GameWebSocketClient(new URI("ws://localhost:8080"));
            wsClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void sendChat(String text) {
        if (text == null || text.isBlank())
            return;

        sendMsg(new Msg(
                "chat",
                roomName,
                playerId,
                nickname,
                0, // x 좌표
                0, // y 좌표
                false, // 방향
                false, // 공격 상태
                text));
    }

    private void update(double dt) {
        if (roomName == null || playerId == null || gameOver)
            return;

        x = Math.max(40, Math.min(860, x));

        // ---- 찌르기 애니메이션 (내 캐릭터만) ----
        if (attacking) {
            attackProgress += dt;

            if (attackProgress < 0.1) {
                bladeOffset = (attackProgress / 0.1) * 30;
            } else if (attackProgress < 0.2) {
                bladeOffset = (1 - ((attackProgress - 0.1) / 0.1)) * 30;
            } else {
                attacking = false;
                bladeOffset = 0;
            }
        }

        sendMsg(new Msg("move", roomName, playerId, nickname, x, y, facingRight, attacking, null));
    }

    // ... (입력 핸들러 설정)

<<<<<<< Updated upstream
    // ... (Msg 레코드 정의)
    public record Msg(String type, String room, String playerId, String nickname, double x, double y,
            boolean facingRight, boolean attacking, String chat) {
    }
=======
    // ... (Msg record definition)
    record Msg(
    String type,
    String room,
    String playerId,
    String nickname,
    double x,
    double y,
    boolean facingRight,
    boolean attacking,
    String chat
) { }

>>>>>>> Stashed changes

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 배경 그리기
        if (imgBackground != null) {
            g.drawImage(imgBackground, 0, 0, w, h);
        } else {
            g.setFill(Color.web("#1A2332"));
            g.fillRect(0, 0, w, h);
        }

        double logicalW = 900;
        double logicalH = 500;

        double scale = Math.min(w / logicalW, h / logicalH);
        double offsetX = (w - logicalW * scale) / 2;
        double offsetY = (h - logicalH * scale) / 2;

        g.save();
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        if (latestState != null) {
            drawPlayer(latestState.p1(), previousState != null ? previousState.p1() : null, Color.web("#00BFFF")); // P1
                                                                                                                   // 색상
                                                                                                                   // 업데이트
            drawPlayer(latestState.p2(), previousState != null ? previousState.p2() : null, Color.SALMON);

            // JavaFX 스레드에서 UI 라벨 업데이트
            Platform.runLater(() -> {
                if (lblScore1 != null)
                    lblScore1.setText(String.valueOf(latestState.score1()));
                if (lblScore2 != null)
                    lblScore2.setText(String.valueOf(latestState.score2()));

                if (lblName1 != null && latestState.p1() != null)
                    lblName1.setText(latestState.p1().nickname());
                if (lblName2 != null && latestState.p2() != null)
                    lblName2.setText(latestState.p2().nickname());
            });
        }

        g.restore();
    }

    private void drawPlayer(Player p, Player prevP, Color color) {
        if (p == null)
            return;

        // 위치 표시기 그리기
        g.setFill(color);
        g.fillOval(p.x() - 15, p.y() - 5, 30, 10);

        if (imgIdle == null) {
            // 이미지 로드 실패 시 대체 처리
            double w = 30;
            double h = 50;
            g.fillRect(p.x() - w / 2, p.y() - h, w, h);
            return;
        }

        // 이미지 결정
        long now = System.currentTimeMillis();

        // 상태 시간 업데이트
        if (p.attacking()) {
            lastAttackTimeMap.put(p.id(), now);
        }

        if (prevP != null) {
            boolean moved = Math.abs(p.x() - prevP.x()) > 0.1;
            if (moved) {
                boolean movingRight = p.x() > prevP.x();
                boolean movingForward = (movingRight && p.facingRight()) || (!movingRight && !p.facingRight());
                if (movingForward) {
                    lastForwardTimeMap.put(p.id(), now);
                }
            }
        }

        Image toDraw = imgIdle;
        Long lastAttack = lastAttackTimeMap.get(p.id());
        Long lastForward = lastForwardTimeMap.get(p.id());

        // 애니메이션 지속 시간 확인 (0.2초 = 200ms)
        if (lastAttack != null && (now - lastAttack < 200)) {
            toDraw = imgAttack;
        } else if (lastForward != null && (now - lastForward < 200)) {
            toDraw = imgForward;
        }

        double imgH = 150; // 가시성을 위해 높이 조정
        double ratio = toDraw.getWidth() / toDraw.getHeight();
        double imgW = imgH * ratio;

        // 이미지 그리기 (중앙 하단 정렬)
        // 원본 이미지가 왼쪽을 보므로 facingRight일 때 반전 처리
        if (p.facingRight()) {
            g.save();
            g.translate(p.x(), p.y());
            g.scale(-1, 1);
            g.drawImage(toDraw, -imgW / 2, -imgH, imgW, imgH);
            g.restore();
        } else {
            g.drawImage(toDraw, p.x() - imgW / 2, p.y() - imgH, imgW, imgH);
        }
    }

    private boolean gameOver = false;
    private long gameStartTime = 0;

    private void showGameOverPopup(String winnerId, String winnerName, int score1, int score2) {
        // 결과 화면 레이아웃 생성
        StackPane resultScreen = new StackPane();
        resultScreen.getStyleClass().add("game-over-overlay"); // 전체 배경을 위해 오버레이 스타일 재사용

        VBox window = new VBox(0);
        window.getStyleClass().add("game-over-window");

        // 헤더
        VBox header = new VBox(5);
        header.getStyleClass().add("game-over-header");

        boolean isWin = winnerId.equals(playerId); // 정확성을 위해 ID 비교

        Label title = new Label(isWin ? "🏆 승리 🏆" : "💀 패배 💀");
        title.getStyleClass().add("game-over-title");
        if (!isWin)
            title.setStyle("-fx-text-fill: #ff4444;"); // 패배 시 빨간색

        Label winner = new Label("승리자: " + winnerName);
        winner.getStyleClass().add("game-over-winner");
        header.getChildren().addAll(title, winner);

        // 본문
        VBox body = new VBox(10);
        body.getStyleClass().add("game-over-body");

        // 통계
        body.getChildren().add(createStatRow("🎯", "최종 점수", score1 + " - " + score2));

        // 성공한 공격 (내 점수)
        int myScore = "p1".equals(playerId) ? score1 : score2;
        body.getChildren().add(createStatRow("◎", "성공한 공격", String.valueOf(myScore)));

        // 공격 시도 (실패한 횟수 = 전체 시도 - 성공)
        int failedAttempts = Math.max(0, attackAttempts - myScore);
        body.getChildren().add(createStatRow("⚡", "공격 시도", String.valueOf(failedAttempts)));

        long duration = (System.currentTimeMillis() - gameStartTime) / 1000;
        long min = duration / 60;
        long sec = duration % 60;
        body.getChildren().add(createStatRow("🕒", "경기 시간", String.format("%d:%02d", min, sec)));

        // 버튼
        Button lobbyBtn = new Button("로비로 돌아가기");
        lobbyBtn.getStyleClass().add("lobby-button");
        lobbyBtn.setOnAction(e -> {
            try {
                stop();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            new LoginScreen(primaryStage, this::startGame).show();
        });

        VBox buttonBox = new VBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        buttonBox.getChildren().add(lobbyBtn);

        body.getChildren().add(buttonBox);

        window.getChildren().addAll(header, body);
        resultScreen.getChildren().add(window);

        // 화면 전환
        root.setCenter(resultScreen);
    }

    private HBox createStatRow(String icon, String label, String value) {
        HBox row = new HBox(10);
        row.getStyleClass().add("stat-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("stat-icon");

        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("stat-label");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("stat-value");

        row.getChildren().addAll(iconLbl, labelLbl, valueLbl);
        return row;
    }

    private void sendMsg(Msg msg) {
        if (wsClient == null || !wsClient.isOpen())
            return;

        try {
            wsClient.send(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void onServerState(String json) {
        try {
            GameState state = mapper.readValue(json, GameState.class);
            Platform.runLater(() -> {
                // 점수 변화 확인 (공격 성공)
                if (latestState != null) {
                    if (state.score1() > latestState.score1()) {
                        if (chatPanel != null) {
                            Player scorer = state.p1();
                            String name = scorer.nickname();
                            if (name == null || name.isEmpty())
                                name = scorer.id();
                            String color = "p1".equals(scorer.id()) ? "#00BFFF" : "#FA8072"; // Brighter Blue
                            chatPanel.appendSystemMessageWithHighlight("", name, " 공격 성공 +1", color);
                        }
                    }
                    if (state.score2() > latestState.score2()) {
                        if (chatPanel != null) {
                            Player scorer = state.p2();
                            String name = scorer.nickname();
                            if (name == null || name.isEmpty())
                                name = scorer.id();
                            String color = "p1".equals(scorer.id()) ? "#00BFFF" : "#FA8072"; // Brighter Blue
                            chatPanel.appendSystemMessageWithHighlight("", name, " 공격 성공 +1", color);
                        }
                    }
                }

                // 서버가 강제 리셋을 요청한 경우 위치 동기화 (큰 차이 발생 시)
                Player myPlayer = null;
                if (state.p1() != null && state.p1().id().equals(playerId)) {
                    myPlayer = state.p1();
                } else if (state.p2() != null && state.p2().id().equals(playerId)) {
                    myPlayer = state.p2();
                }

                if (myPlayer != null) {
                    double dist = Math.abs(x - myPlayer.x()) + Math.abs(y - myPlayer.y());
                    if (dist > 50) { // 강제 리셋 임계값
                        x = myPlayer.x();
                        y = myPlayer.y();
                        attacking = false; // 공격 상태도 초기화
                    }
                }

                previousState = latestState;
                latestState = state;

                if (waitingRoomPanel != null &&
                        root.getCenter() == waitingRoomPanel.getView() &&
                        state.p2() != null) {

                    // 게임 시작
                    if (state.gameStartTime() > 0) {
                        gameStartTime = state.gameStartTime();
                    } else {
                        gameStartTime = System.currentTimeMillis(); // 대체값
                    }
                    gameOver = false;
                    attackAttempts = 0;

                    HBox scoreBoard = createScoreBoard();
                    HBox bottomBar = createBottomBar();
                    BorderPane uiOverlay = new BorderPane();
                    uiOverlay.setTop(scoreBoard);
                    uiOverlay.setBottom(bottomBar);
                    uiOverlay.setPickOnBounds(false);

                    StackPane mainStack = new StackPane(canvasContainer, uiOverlay);

                    root.setCenter(mainStack);
                    root.setRight(chatPanel.getView());
                    primaryStage.setHeight(600); // 높이 조정
                    canvas.requestFocus();

                    if (chatPanel != null) {
                        chatPanel.appendMessage("System", "System", "[System] 플레이어가 입장했습니다. 게임을 시작합니다!");
                    }
                }

                // 게임 종료 확인
                if (!gameOver && (state.score1() >= 5 || state.score2() >= 5)) {
                    gameOver = true;
                    // 승자 ID 결정
                    String winnerId = (state.score1() >= 5) ? "p1" : "p2";
                    String winnerName = (state.score1() >= 5) ? (state.p1() != null ? state.p1().nickname() : "p1")
                            : (state.p2() != null ? state.p2().nickname() : "p2");
                    if (winnerName == null)
                        winnerName = winnerId;

                    showGameOverPopup(winnerId, winnerName, state.score1(), state.score2());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (wsClient != null)
            wsClient.close();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private class GameWebSocketClient extends WebSocketClient {
        public GameWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Platform.runLater(() -> chatPanel.appendMessage("System", "System", "[System] 서버에 연결되었습니다. 방에 참가 중..."));
            sendJoin();
        }

        private void sendJoin() {
            try {
                Msg join = new Msg(
                        "join",
                        roomName,
                        null, // 플레이어 ID는 아직 할당되지 않음
                        nickname,
                        0, // x 좌표 (입장 시 무관)
                        0, // y 좌표 (입장 시 무관)
                        false, // 방향 (입장 시 무관)
                        false, // 공격 상태
                        null);
                this.send(mapper.writeValueAsString(join));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(String message) {
            try {
                Map<String, Object> map = mapper.readValue(message, Map.class);

                if (map.get("type") != null) {
                    String type = map.get("type").toString();

                    if (type.equals("chat")) {
                        String sender = (String) map.get("senderId");
                        String nick = (String) map.get("nickname");
                        String text = (String) map.get("text");
                        // 닉네임이 있으면 사용, 없으면 senderId 사용
                        String displayName = (nick != null && !nick.isEmpty()) ? nick : sender;
                        Platform.runLater(() -> chatPanel.appendMessage(sender, displayName, text));
                        return;
                    }
                    if (type.equals("assign")) {
                        playerId = (String) map.get("playerId");

                        Platform.runLater(() -> {
                            if ("p1".equals(playerId)) {
                                x = 100;
                                y = 400;
                                facingRight = true;
                            } else {
                                x = 700;
                                y = 400;
                                facingRight = false;
                            }
                            String name = nickname;
                            if (name == null || name.isEmpty())
                                name = playerId;
                            String color = "p1".equals(playerId) ? "#00BFFF" : "#FA8072"; // 더 밝은 파란색
                            chatPanel.appendSystemMessageWithHighlight("System", name, " 환영합니다! (" + playerId + ")",
                                    color);
                        });
                        return;
                    }
                    if (type.equals("error")) {
                        String msg = (String) map.get("msg");
                        Platform.runLater(() -> chatPanel.appendMessage("System", "System", "[Error] " + msg));
                        return;
                    }
                    return;
                }

                onServerState(message);

            } catch (Exception e) {
                try {
                    onServerState(message);
                } catch (Exception ignore) {
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Platform.runLater(() -> chatPanel.appendMessage("System", "System", "[System] 서버 연결 종료됨."));
        }

        @Override
        public void onError(Exception ex) {
            Platform.runLater(() -> chatPanel.appendMessage("System", "System", "[Error] " + ex.getMessage()));
        }
    }
}

/** 데이터 구조 동일 */
record Msg(String type, String room, String playerId, String nickname, double x, double y, boolean facingRight,
        String chat) {
}

record Player(String id, String nickname, double x, double y, boolean facingRight, boolean attacking) {
}

record GameState(String room, Player p1, Player p2, int score1, int score2, long gameStartTime) {
}
