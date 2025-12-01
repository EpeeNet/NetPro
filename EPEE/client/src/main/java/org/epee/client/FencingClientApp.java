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

    // 고정 ROOM_ID, PLAYER_ID 제거 (동적 할당)
    private String roomName;
    private String nickname;
    private String playerId; // 서버에서 "p1" 또는 "p2" 할당 받음

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

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        new LoginScreen(stage, this::startGame).show();
    }

    private void startGame(String nickname, String roomName) {
        this.nickname = nickname;
        this.roomName = roomName;

        if (nickname.isEmpty())
            return; // 간단한 유효성 검사

        // 캔버스 크기를 컨테이너에 맞춤 (논리적 크기는 고정)
        canvas = new Canvas();
        g = canvas.getGraphicsContext2D();

        // 캔버스를 StackPane으로 감싸서 중앙 정렬 및 리사이징 지원
        javafx.scene.layout.StackPane canvasContainer = new javafx.scene.layout.StackPane(canvas);
        canvasContainer.setStyle("-fx-background-color: #2F4F4F;"); // 배경색: Dark Slate Gray

        // 캔버스 크기를 컨테이너 크기에 바인딩
        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        BorderPane root = new BorderPane();
        root.setCenter(canvasContainer);

        // ChatPanel 추가
        chatPanel = new ChatPanel(this);
        root.setRight(chatPanel.getView());

        Scene scene = new Scene(root, 1000, 500); // 게임 화면 크기 확대

        primaryStage.setTitle("ÉPÉE Client - " + nickname);
        primaryStage.setScene(scene);

        scene.setOnMouseClicked(e -> canvas.requestFocus());
        canvas.requestFocus(); // 게임 시작 시 포커스 요청

        // 키보드 입력 처리
        scene.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F) {
                attacking = false;
            }
            pressedKeys.remove(e.getCode());
        });

        // 웹소켓 연결 시작
        try {
            wsClient = new GameWebSocketClient(new URI("ws://localhost:8080"));
            wsClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // 게임 루프 (애니메이션 타이머)
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

    // 채팅 메시지 전송
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
        // 플레이어 ID 배정 전에는 로컬 이동만 처리
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

        // 캐릭터 이동 범위 제한
        x = Math.max(40, Math.min(860, x));
        y = Math.max(100, Math.min(460, y));

        // 공격 키 입력 처리
        if (pressedKeys.contains(javafx.scene.input.KeyCode.F) && !attacking) {
            attacking = true;
            sendMsg(new Msg("attack", roomName, playerId, x, y, facingRight, null));
        }

        // 위치 정보 서버로 전송
        sendMsg(new Msg("move", roomName, playerId, x, y, facingRight, null));
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 화면 초기화
        g.setFill(Color.DARKSLATEGRAY);
        g.fillRect(0, 0, w, h);

        // 900x500 논리적 게임 화면을 현재 창 크기에 맞게 스케일링
        // 화면 비율 유지하며 꽉 채우기 (Letterboxing 없이)
        // 여기서는 비율 유지하며 중앙 정렬 방식을 사용

        double logicalW = 900;
        double logicalH = 500;

        double scaleX = w / logicalW;
        double scaleY = h / logicalH;

        // 화면 잘림 없이 전체를 보여주기 위해 더 작은 스케일 사용
        double scale = Math.min(scaleX, scaleY);

        // 게임 화면 중앙 정렬
        double offsetX = (w - logicalW * scale) / 2;
        double offsetY = (h - logicalH * scale) / 2;

        g.save();
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        // 게임 영역 배경 그리기 (선택 사항)
        g.setFill(Color.DARKSLATEGRAY); // 배경색과 동일하게
        g.fillRect(0, 0, logicalW, logicalH);

        if (latestState != null) {
            drawPlayer(latestState.p1(), Color.CORNFLOWERBLUE);
            drawPlayer(latestState.p2(), Color.SALMON);

            g.setFill(Color.WHITE);
            // 점수 및 방 정보 텍스트 표시
            g.fillText("Room: " + latestState.room(), 20, 30);
            g.fillText("Score P1: " + latestState.score1() + "  P2: " + latestState.score2(), 20, 50);
        } else {
            g.setFill(Color.WHITE);
            g.fillText("Waiting for server state...", 360, 40);

            g.setFill(Color.GRAY);
            g.fillOval(x - 5, y - 5, 10, 10);
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
            // 연결 즉시 입장 메시지 전송
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
                // 메시지 타입 확인 (chat, assign 등)
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

                    // 처리하지 않는 타입은 무시
                    return;
                }

                // 타입이 없으면 게임 상태 업데이트로 처리
                onServerState(message);

            } catch (Exception e) {
                // 파싱 실패 시 게임 상태 업데이트 재시도
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

/** ==== 데이터 레코드 정의 (서버와 동일) ==== */

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
