package org.epee.server;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GameServer extends WebSocketServer {

    private final ObjectMapper mapper = new ObjectMapper();

    // 방 상태
    private static class RoomState {
        Player p1 = null;
        Player p2 = null;
        int score1 = 0;
        int score2 = 0;

        long lastP1Update = 0;
        long lastP2Update = 0;
        long lastScoreTime = 0;
        long gameStartTime = 0;
    }

    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> socketToRoom = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> socketToPlayerId = new ConcurrentHashMap<>();

    public GameServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String room = socketToRoom.remove(conn);
        String pid = socketToPlayerId.remove(conn);

        if (room != null) {
            RoomState r = rooms.get(room);
            if (r != null && pid != null) {
                if ("p1".equals(pid))
                    r.p1 = null;
                if ("p2".equals(pid))
                    r.p2 = null;

                if (r.p1 == null && r.p2 == null) {
                    rooms.remove(room);
                } else {
                    broadcastState(room);
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            Map<String, Object> map = mapper.readValue(message, Map.class);
            String type = (String) map.get("type");

            switch (type) {
                case "join" -> handleJoin(conn, map);
                case "move" -> handleMove(conn, map);
                case "attack" -> handleAttack(conn, map);
                case "chat" -> handleChat(conn, map);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleJoin(WebSocket conn, Map<String, Object> map) {
        String room = (String) map.get("room");
        String nickname = (String) map.get("playerId");

        rooms.putIfAbsent(room, new RoomState());
        RoomState r = rooms.get(room);

        String assigned;

        // 새 플레이어 입장 시 점수 초기화 (새 매치 시작)
        if (r.p1 == null || r.p2 == null) {
            r.score1 = 0;
            r.score2 = 0;
        }

        if (r.p1 == null) {
            assigned = "p1";
            r.p1 = new Player("p1", nickname, 100, 400, true, false);
        } else if (r.p2 == null) {
            assigned = "p2";
            r.p2 = new Player("p2", nickname, 700, 400, false, false);
            r.gameStartTime = System.currentTimeMillis(); // P2 입장 시 게임 시간 시작
        } else {
            sendError(conn, "Room full");
            return;
        }

        socketToRoom.put(conn, room);
        socketToPlayerId.put(conn, assigned);

        send(conn, Map.of(
                "type", "assign",
                "playerId", assigned));

        broadcastState(room);
    }

    private void handleMove(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        String pid = socketToPlayerId.get(conn);

        if (room == null || pid == null)
            return;

        RoomState r = rooms.get(room);
        if (r == null)
            return;

        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        boolean facing = (Boolean) map.get("facingRight");

        // 공격 상태 추출 (기본값 false)
        Object attackingObj = map.get("attacking");
        boolean attacking = attackingObj instanceof Boolean ? (Boolean) attackingObj : false;

        long now = System.currentTimeMillis();

        if ("p1".equals(pid)) {
            r.p1 = new Player("p1", r.p1.nickname(), x, y, facing, attacking);
            r.lastP1Update = now;
        } else {
            r.p2 = new Player("p2", r.p2.nickname(), x, y, facing, attacking);
            r.lastP2Update = now;
        }

        checkHitWithPriority(r);
        broadcastState(room);
    }

    // ... (handleAttack은 move와 유사하지만 유지)

    // ...

    private void handleAttack(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        String pid = socketToPlayerId.get(conn);

        if (room == null || pid == null)
            return;

        RoomState r = rooms.get(room);
        if (r == null)
            return;

        long now = System.currentTimeMillis();

        if ("p1".equals(pid)) {
            r.p1 = new Player(r.p1.id(), r.p1.nickname(), r.p1.x(), r.p1.y(), r.p1.facingRight(), true);
            r.lastP1Update = now;
        } else {
            r.p2 = new Player(r.p2.id(), r.p2.nickname(), r.p2.x(), r.p2.y(), r.p2.facingRight(), true);
            r.lastP2Update = now;
        }

        checkHitWithPriority(r);
        broadcastState(room);
    }

    private void checkHitWithPriority(RoomState r) {
        if (r.p1 == null || r.p2 == null)
            return;

        boolean p1First = r.lastP1Update > r.lastP2Update;

        if (p1First) {
            if (hit(r.p1, r.p2))
                onScore(r, true);
            if (hit(r.p2, r.p1))
                onScore(r, false);
        } else {
            if (hit(r.p2, r.p1))
                onScore(r, false);
            if (hit(r.p1, r.p2))
                onScore(r, true);
        }
    }

    /** 공격 리치 70px 적용 (칼 전진 포함) */
    private boolean hit(Player attacker, Player defender) {
        if (!attacker.attacking())
            return false;

        double reach = 70; // === 40 + bladeOffset(최대 30)

        double tip = attacker.facingRight()
                ? attacker.x() + reach
                : attacker.x() - reach;

        return Math.abs(tip - defender.x()) < 20 &&
                Math.abs(attacker.y() - defender.y()) < 40;
    }

    private void onScore(RoomState r, boolean p1Scored) {
        long now = System.currentTimeMillis();
        if (now - r.lastScoreTime < 1000)
            return; // 디바운스: 1초 쿨다운

        r.lastScoreTime = now;

        if (p1Scored)
            r.score1++;
        else
            r.score2++;

        // 즉시 리스폰 (닉네임 유지)
        r.p1 = new Player("p1", r.p1.nickname(), 100, 400, true, false);
        r.p2 = new Player("p2", r.p2.nickname(), 700, 400, false, false);

        r.lastP1Update = r.lastP2Update = now;
    }

    private void handleChat(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        if (room == null)
            return;

        String sender = socketToPlayerId.get(conn);
        String text = (String) map.get("chat");

        broadcast(room, Map.of(
                "type", "chat",
                "senderId", sender,
                "text", text));
    }

    private void sendError(WebSocket conn, String msg) {
        send(conn, Map.of("type", "error", "msg", msg));
    }

    private void broadcastState(String room) {
        RoomState r = rooms.get(room);
        if (r == null)
            return;

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("room", room);
        data.put("p1", r.p1);
        data.put("p2", r.p2);
        data.put("score1", r.score1);
        data.put("score2", r.score2);
        data.put("gameStartTime", r.gameStartTime);

        broadcast(room, data);
    }

    private void send(WebSocket conn, Object obj) {
        try {
            conn.send(mapper.writeValueAsString(obj));
        } catch (Exception ignored) {
        }
    }

    private void broadcast(String room, Object obj) {
        try {
            String json = mapper.writeValueAsString(obj);
            for (var e : socketToRoom.entrySet()) {
                if (room.equals(e.getValue()))
                    e.getKey().send(json);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Game server started");
    }

    public record Player(String id, String nickname, double x, double y, boolean facingRight, boolean attacking) {
    }

    public record Msg(String type, String room, String playerId, String nickname, double x, double y,
            boolean facingRight, boolean attacking, String chat) {
    }
}
