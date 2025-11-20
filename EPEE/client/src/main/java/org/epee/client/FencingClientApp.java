package org.epee.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

public class FencingClientApp extends Application {

    private static final String ROOM_ID = "room-001";
    private static final String PLAYER_ID = "p1";



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

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(900, 500);
        g = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        stage.setTitle("ÉPÉE Client (" + PLAYER_ID + ")");
        stage.setScene(scene);
        stage.show();

        // 키 입력 처리
        scene.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F) {
                attacking = false;
            }
            pressedKeys.remove(e.getCode());
        });

        // WebSocket 연결 (순수 Java 서버 → 경로 없이 포트만)
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

    private void update(double dt) {
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
            sendMsg(new Msg("attack", ROOM_ID, PLAYER_ID, x, y, facingRight));
        }

        // 위치 동기화 (간단히 매 프레임 전송)
        sendMsg(new Msg("move", ROOM_ID, PLAYER_ID, x, y, facingRight));
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
        if (p == null) return;

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
        if (wsClient == null || !wsClient.isOpen()) return;
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
            Platform.runLater(() -> latestState = state);
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
        }

        @Override
        public void onMessage(String message) {
            onServerState(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("Disconnected from server: " + reason);
        }

        @Override
        public void onError(Exception ex) {
            ex.printStackTrace();
        }
    }
}

/** ==== 클라이언트측 record 정의 (서버와 필드 맞춰야 함) ==== */

record Msg(
        String type,
        String room,
        String playerId,
        double x,
        double y,
        boolean facingRight
) {}

record Player(
        String id,
        double x,
        double y,
        boolean facingRight,
        boolean attacking
) {}

record GameState(
        String room,
        Player p1,
        Player p2,
        int score1,
        int score2
) {}
