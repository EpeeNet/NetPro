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
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class FencingClientApp extends Application {

    // 고정이었던 ROOM_ID, PLAYER_ID 제거
    private String roomName;
    private String nickname;
    private String playerId; // 서버에서 "p1" 또는 "p2"로 assign

    public String getPlayerId() {
        return playerId;
    }

    private Canvas canvas;
    private GraphicsContext g;

    private double x = 100;
    private double y = 300;
    private boolean facingRight = true;
    private boolean attacking = false;

    private final Set<javafx.scene.input.KeyCode> pressedKeys = new HashSet<>();

    private GameState latestState;
    private GameWebSocketClient wsClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private ChatPanel chatPanel;

    @Override
    public void start(Stage stage) {
        // 1) 방 이름 입력
        TextInputDialog roomDialog = new TextInputDialog("room-001");
        roomDialog.setTitle("Room");
        roomDialog.setHeaderText("Enter Room Name");
        roomDialog.setContentText("Room:");
        this.roomName = roomDialog.showAndWait().orElse("").trim();

        if (roomName.isEmpty()) {
            System.out.println("Room name is required. Closing.");
            Platform.exit();
            return;
        }

        // 2) 닉네임 입력
        TextInputDialog nickDialog = new TextInputDialog("Player");
        nickDialog.setTitle("Nickname");
        nickDialog.setHeaderText("Enter Nickname");
        nickDialog.setContentText("Nickname:");
        this.nickname = nickDialog.showAndWait().orElse("").trim();

        if (nickname.isEmpty()) {
            System.out.println("Nickname is required. Closing.");
            Platform.exit();
            return;
        }

        canvas = new Canvas(900, 500);
        g = canvas.getGraphicsContext2D();

        BorderPane root = new BorderPane(canvas);
        root.setCenter(canvas);

        // ChatPanel 추가
        chatPanel = new ChatPanel(this);
        root.setRight(chatPanel.getView());

        Scene scene = new Scene(root);

        stage.setTitle("ÉPÉE Client - " + nickname + " (Connecting...)");
        stage.setScene(scene);
        stage.show();

        scene.setOnMouseClicked(e -> canvas.requestFocus());
        canvas.requestFocus(); // 최초 실행 시 포커스 부여

        // 키 입력 처리
        scene.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F) {
                attacking = false;
            }
            pressedKeys.remove(e.getCode());
        });

        // WebSocket 연결
        try {
            wsClient = new GameWebSocketClient(new URI("ws://localhost:8080"));
            wsClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // 게임 루프
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

    // 채팅 전송: 닉네임까지 붙여서 서버에 보냄
    public void sendChat(String text) {
        if (text == null || text.isBlank())
            return;

        if (roomName == null || playerId == null) {
            chatPanel.appendMessage("System", "[시스템] 아직 방에 완전히 입장하지 않았습니다.");
            return;
        }

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
        // 아직 서버에서 p1/p2 배정 안 받았으면 움직임만 로컬로 그리고, 서버에는 안 보냄
        if (roomName == null || playerId == null) {
            return;
        }

        double speed = 280; // px/s

        if (pressedKeys.contains(javafx.scene.input.KeyCode.A)) {
            x -= speed * dt;
            facingRight = false;
        }
        if (pressedKeys.contains(javafx.scene.input.KeyCode.D)) {
            x += speed * dt;
            facingRight = true;
        }
        if (pressedKeys.contains(javafx.scene.input.KeyCode.W)) {
            y -= speed * dt;
        }
        if (pressedKeys.contains(javafx.scene.input.KeyCode.S)) {
            y += speed * dt;
        }

        // 화면 범위 제한
        x = Math.max(40, Math.min(860, x));
        y = Math.max(100, Math.min(460, y));

        // 공격 입력
        if (pressedKeys.contains(javafx.scene.input.KeyCode.F) && !attacking) {
            attacking = true;
            sendMsg(new Msg("attack", roomName, playerId, x, y, facingRight, null));
        }

        // 위치 동기화
        sendMsg(new Msg("move", roomName, playerId, x, y, facingRight, null));
    }

    private void render() {
        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (latestState != null) {
            drawPlayer(latestState.p1(), Color.CORNFLOWERBLUE);
            drawPlayer(latestState.p2(), Color.SALMON);

            g.setFill(Color.WHITE);
            g.fillText("Room: " + latestState.room(), 20, 30);
            g.fillText("Score P1: " + latestState.score1() + "  P2: " + latestState.score2(), 20, 50);
        } else {
            g.setFill(Color.WHITE);
            g.fillText("Waiting for server state...", 360, 40);

            g.setFill(Color.GRAY);
            g.fillOval(x - 5, y - 5, 10, 10);
        }
    }

    private void drawPlayer(Player p, Color color) {
        if (p == null)
            return;

        double w = 30;
        double h = 50;

        g.setFill(color);
        g.fillRect(p.x() - w / 2, p.y() - h, w, h);

        double tipX = p.facingRight() ? p.x() + 40 : p.x() - 40;
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
            String json = mapper.writeValueAsString(msg);
            wsClient.send(json);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void onServerState(String json) {
        try {
            GameState state = mapper.readValue(json, GameState.class);
            Platform.runLater(() -> {
                latestState = state;
            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (wsClient != null) {
            wsClient.close();
        }
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
            System.out.println("Connected to server");
            // 접속하자마자 join 메시지 보냄
            Platform.runLater(() -> chatPanel.appendMessage("System", "[System] 서버에 연결되었습니다. 방에 참가 중..."));
            sendJoin();
        }

        private void sendJoin() {
            try {
                Msg joinMsg = new Msg(
                        "join",
                        roomName,
                        nickname, // 여기서는 playerId 대신 nickname을 잠깐 사용
                        x,
                        y,
                        facingRight,
                        null);
                String json = mapper.writeValueAsString(joinMsg);
                this.send(json);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(String message) {
            try {
                // type이 있는 메시지인지 먼저 시도 (chat, assign 등)
                Map<String, Object> map = mapper.readValue(message, Map.class);
                Object typeObj = map.get("type");

                if (typeObj != null) {
                    String type = typeObj.toString();

                    if ("chat".equals(type)) {
                        String text = (String) map.get("text");
                        String senderId = (String) map.get("senderId");
                        Platform.runLater(() -> chatPanel.appendMessage(senderId, text));
                        return;
                    }

                    if ("assign".equals(type)) {
                        String assignedId = (String) map.get("playerId");
                        playerId = assignedId;

                        Platform.runLater(() -> {
                            // ★ 서버가 배정한 p1/p2에 맞춰 초기 스폰 위치 설정
                            if ("p1".equals(playerId)) {
                                x = 100;
                                y = 300;
                                facingRight = true;
                            } else if ("p2".equals(playerId)) {
                                x = 700;
                                y = 300;
                                facingRight = false;
                            }

                            chatPanel.appendMessage("System", "[System] 당신은 " + playerId + " 로 배정되었습니다.");
                        });

                        return;
                    }

                    if ("error".equals(type)) {
                        String msg = (String) map.get("msg");
                        Platform.runLater(() -> {
                            chatPanel.appendMessage("System", "[Error] " + msg);
                        });
                        return;
                    }

                    // type은 있는데 우리가 따로 처리 안 하는 경우 → 무시하거나 로그
                    return;
                }

                // type이 없으면 GameState로 처리 시도
                onServerState(message);

            } catch (Exception e) {
                // Map 파싱이 실패하면 GameState일 수 있으니 한 번 더 시도
                try {
                    onServerState(message);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("Disconnected from server: " + reason);
            Platform.runLater(() -> chatPanel.appendMessage("System", "[System] 서버와의 연결이 종료되었습니다."));
        }

        @Override
        public void onError(Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> chatPanel.appendMessage("System", "[Error] " + ex.getMessage()));
        }
    }
}

/** ==== 클라이언트측 record 정의 (서버와 필드 맞춰야 함) ==== */

record Msg(
        String type,
        String room,
        String playerId, // join 때는 nickname, 이후에는 "p1"/"p2"
        double x,
        double y,
        boolean facingRight,
        String chat) {
}

record Player(
        String id,
        double x,
        double y,
        boolean facingRight,
        boolean attacking) {
}

record GameState(
        String room,
        Player p1,
        Player p2,
        int score1,
        int score2) {
}
