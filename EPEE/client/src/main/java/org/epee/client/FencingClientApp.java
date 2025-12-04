package org.epee.client;

import java.net.URI;
import java.net.URISyntaxException;
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
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
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
    private double y = 300;
    private boolean facingRight = true;

    private boolean attacking = false;
    private double attackProgress = 0.0; // 0 ~ 0.2
    private double bladeOffset = 0.0; // 공격 시 칼 전진 거리

    private final Set<javafx.scene.input.KeyCode> pressedKeys = new HashSet<>();

    private GameState latestState;
    private GameWebSocketClient wsClient;
    private final ObjectMapper mapper = new ObjectMapper();

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

        canvas = new Canvas();
        g = canvas.getGraphicsContext2D();

        canvasContainer = new javafx.scene.layout.StackPane(canvas);
        canvasContainer.setStyle("-fx-background-color: #2F4F4F;");
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        root = new BorderPane();

        chatPanel = new ChatPanel(this);

        double initialHeight = isCreator ? 700 : 500;
        Scene scene = new Scene(root, 1000, initialHeight);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

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
            root.setCenter(canvasContainer);
            root.setRight(chatPanel.getView());
            canvas.requestFocus();
        }
    }

    private void setupInputHandlers(Scene scene) {
        scene.setOnMouseClicked(e -> {
            if (root.getCenter() == canvasContainer)
                canvas.requestFocus();
        });

        scene.setOnKeyPressed(e -> {
            javafx.scene.input.KeyCode code = e.getCode();

            // --- 이동: A, D 한 번 눌렀을 때 30px ---
            if (code == javafx.scene.input.KeyCode.A) {
                facingRight = false;
                x -= 30;
            }
            if (code == javafx.scene.input.KeyCode.D) {
                facingRight = true;
                x += 30;
            }

            // --- 공격: F ---
            if (code == javafx.scene.input.KeyCode.F && !attacking) {
                attacking = true;
                attackProgress = 0.0;
                sendMsg(new Msg("attack", roomName, playerId, x, y, facingRight, null));
            }
        });

        scene.setOnKeyReleased(e -> {
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
                x,
                y,
                facingRight,
                text));
    }

    private void update(double dt) {
        if (roomName == null || playerId == null)
            return;

        x = Math.max(40, Math.min(860, x));

        // ---- 찌르기 애니메이션 ----
        if (attacking) {
            attackProgress += dt;

            if (attackProgress < 0.1) {
                // 전진 0~0.1초
                bladeOffset = (attackProgress / 0.1) * 30;
            } else if (attackProgress < 0.2) {
                // 복귀 0.1~0.2초
                bladeOffset = (1 - ((attackProgress - 0.1) / 0.1)) * 30;
            } else {
                attacking = false;
                bladeOffset = 0;
            }
        }

        // 서버로 위치 전달
        sendMsg(new Msg("move", roomName, playerId, x, y, facingRight, null));
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, w, h);

        double logicalW = 900;
        double logicalH = 500;

        double scale = Math.min(w / logicalW, h / logicalH);
        double offsetX = (w - logicalW * scale) / 2;
        double offsetY = (h - logicalH * scale) / 2;

        g.save();
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        if (latestState != null) {
            drawPlayer(latestState.p1(), Color.CORNFLOWERBLUE);
            drawPlayer(latestState.p2(), Color.SALMON);

            g.setFill(Color.WHITE);
            g.fillText("Room: " + latestState.room(), 20, 30);
            g.fillText("Score P1: " + latestState.score1() + "  P2: " + latestState.score2(), 20, 50);
        }

        g.restore();
    }

    private void drawPlayer(Player p, Color color) {
        if (p == null)
            return;

        double w = 30;
        double h = 50;

        g.setFill(color);
        g.fillRect(p.x() - w / 2, p.y() - h, w, h);

        double tipX = p.facingRight()
                ? p.x() + 40 + bladeOffset
                : p.x() - 40 - bladeOffset;

        double tipY = p.y() - 30;

        g.setStroke(Color.WHITE);
        g.setLineWidth(3);
        g.strokeLine(p.x(), tipY, tipX, tipY);

        if (p.attacking()) {
            g.setStroke(Color.YELLOW);
            g.setLineWidth(2);
            g.strokeOval(tipX - 6, tipY - 6, 12, 12);
        }
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
                latestState = state;

                if (waitingRoomPanel != null &&
                        root.getCenter() == waitingRoomPanel.getView() &&
                        state.p2() != null) {

                    root.setCenter(canvasContainer);
                    root.setRight(chatPanel.getView());
                    primaryStage.setHeight(528);
                    canvas.requestFocus();

                    chatPanel.appendMessage("System", "[System] 플레이어가 입장했습니다. 게임을 시작합니다!");
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
            Platform.runLater(() -> chatPanel.appendMessage("System", "[System] 서버에 연결되었습니다. 방에 참가 중..."));
            sendJoin();
        }

        private void sendJoin() {
            try {
                Msg join = new Msg(
                        "join",
                        roomName,
                        nickname,
                        x,
                        y,
                        facingRight,
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
                        String text = (String) map.get("text");
                        Platform.runLater(() -> chatPanel.appendMessage(sender, text));
                        return;
                    }
                    if (type.equals("assign")) {
                        playerId = (String) map.get("playerId");

                        Platform.runLater(() -> {
                            if ("p1".equals(playerId)) {
                                x = 100;
                                y = 300;
                                facingRight = true;
                            } else {
                                x = 700;
                                y = 300;
                                facingRight = false;
                            }
                            chatPanel.appendMessage("System", "[System] 당신은 " + playerId + " 입니다.");
                        });
                        return;
                    }
                    if (type.equals("error")) {
                        String msg = (String) map.get("msg");
                        Platform.runLater(() -> chatPanel.appendMessage("System", "[Error] " + msg));
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
            Platform.runLater(() -> chatPanel.appendMessage("System", "[System] 서버 연결 종료됨."));
        }

        @Override
        public void onError(Exception ex) {
            Platform.runLater(() -> chatPanel.appendMessage("System", "[Error] " + ex.getMessage()));
        }
    }
}

/** 데이터 구조 동일 */
record Msg(String type, String room, String playerId, double x, double y, boolean facingRight, String chat) {
}

record Player(String id, double x, double y, boolean facingRight, boolean attacking) {
}

record GameState(String room, Player p1, Player p2, int score1, int score2) {
}
