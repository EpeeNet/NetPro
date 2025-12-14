package org.epee.server;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GameServer extends WebSocketServer {

    private final ObjectMapper mapper = new ObjectMapper();

    private static class RoomState {
        Player p1 = null;
        Player p2 = null;
        int score1 = 0;
        int score2 = 0;

<<<<<<< Updated upstream
        long lastP1Update = 0;
        long lastP2Update = 0;
        long lastScoreTime = 0;
        long gameStartTime = 0;
=======
        long lastP1Input = 0;
        long lastP2Input = 0;

        // 공격 시작 시각(서버 기준)
        long p1AttackStart = -1;
        long p2AttackStart = -1;

        // 득점 직후 move가 덮어쓰는 것 방지용 락
        long respawnLockUntil = 0;
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
                if ("p1".equals(pid))
                    r.p1 = null;
                if ("p2".equals(pid))
                    r.p2 = null;

                if (r.p1 == null && r.p2 == null) {
                    rooms.remove(room);
                } else {
                    broadcastState(room);
                }
=======
                if ("p1".equals(pid)) r.p1 = null;
                if ("p2".equals(pid)) r.p2 = null;
                broadcastState(room);
>>>>>>> Stashed changes
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(message, Map.class);
            String type = (String) map.get("type");

            switch (type) {
                case "join" -> handleJoin(conn, map);
                case "move" -> handleMove(conn, map);
                case "attack" -> handleAttack(conn, map);
                case "chat" -> handleChat(conn, map);
                default -> {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleJoin(WebSocket conn, Map<String, Object> map) {
        String room = (String) map.get("room");

        rooms.putIfAbsent(room, new RoomState());
        RoomState r = rooms.get(room);

        String assigned;

        // Reset scores whenever a new player joins (start of new match or session)
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
            r.gameStartTime = System.currentTimeMillis(); // Start game time when P2 joins
        } else {
            sendError(conn, "Room full");
            return;
        }

        socketToRoom.put(conn, room);
        socketToPlayerId.put(conn, assigned);

        send(conn, Map.of("type", "assign", "playerId", assigned));
        broadcastState(room);
    }

    private void handleMove(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        String pid = socketToPlayerId.get(conn);
        if (room == null || pid == null) return;

        RoomState r = rooms.get(room);
        if (r == null) return;

        // Extract attacking state, default to false if missing
        Object attackingObj = map.get("attacking");
        boolean attacking = attackingObj instanceof Boolean ? (Boolean) attackingObj : false;

        long now = System.currentTimeMillis();

<<<<<<< Updated upstream
        if ("p1".equals(pid)) {
            r.p1 = new Player("p1", r.p1.nickname(), x, y, facing, attacking);
            r.lastP1Update = now;
        } else {
            r.p2 = new Player("p2", r.p2.nickname(), x, y, facing, attacking);
            r.lastP2Update = now;
=======
        // ✅ 득점 직후 리스폰 락 동안은 move 무시
        if (now < r.respawnLockUntil) {
            broadcastState(room);
            return;
>>>>>>> Stashed changes
        }

        double x = ((Number) map.get("x")).doubleValue();
        double y = 300; // ✅ y 고정
        boolean facing = (Boolean) map.get("facingRight");

        // 공격 지속 여부(서버가 시간으로 관리)
        boolean p1Atk = isAttacking(now, r.p1AttackStart);
        boolean p2Atk = isAttacking(now, r.p2AttackStart);

        if ("p1".equals(pid)) {
            r.lastP1Input = now;
            if (r.p1 != null) r.p1 = new Player("p1", x, y, facing, p1Atk);
        } else {
            r.lastP2Input = now;
            if (r.p2 != null) r.p2 = new Player("p2", x, y, facing, p2Atk);
        }

        // ✅ 공격 중이면 move만 와도 판정(칼 tip이 움직이는 시간 동안 닿으면 즉시 득점)
        checkHitAndScore(r, now);

        broadcastState(room);
    }

    // ... (handleAttack remains similar or can be deprecated if move handles it,
    // but keep for now)

    // ...

    private void handleAttack(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        String pid = socketToPlayerId.get(conn);
        if (room == null || pid == null) return;

        RoomState r = rooms.get(room);
        if (r == null) return;

        long now = System.currentTimeMillis();

<<<<<<< Updated upstream
        if ("p1".equals(pid)) {
            r.p1 = new Player(r.p1.id(), r.p1.nickname(), r.p1.x(), r.p1.y(), r.p1.facingRight(), true);
            r.lastP1Update = now;
        } else {
            r.p2 = new Player(r.p2.id(), r.p2.nickname(), r.p2.x(), r.p2.y(), r.p2.facingRight(), true);
            r.lastP2Update = now;
=======
        // ✅ 득점 직후 리스폰 락 동안은 attack 무시
        if (now < r.respawnLockUntil) {
            broadcastState(room);
            return;
>>>>>>> Stashed changes
        }

        // 공격 시작 시간 기록
        if ("p1".equals(pid)) {
            r.p1AttackStart = now;
            r.lastP1Input = now;
            if (r.p1 != null) r.p1 = new Player(r.p1.id(), r.p1.x(), 300, r.p1.facingRight(), true);
        } else {
            r.p2AttackStart = now;
            r.lastP2Input = now;
            if (r.p2 != null) r.p2 = new Player(r.p2.id(), r.p2.x(), 300, r.p2.facingRight(), true);
        }

        // 공격 순간에도 즉시 판정
        checkHitAndScore(r, now);
        broadcastState(room);
    }

    private void checkHitAndScore(RoomState r, long now) {
        if (r.p1 == null || r.p2 == null) return;

        boolean p1Atk = isAttacking(now, r.p1AttackStart);
        boolean p2Atk = isAttacking(now, r.p2AttackStart);

        boolean p1Hit = p1Atk && hit(now, r.p1, r.p2, r.p1AttackStart);
        boolean p2Hit = p2Atk && hit(now, r.p2, r.p1, r.p2AttackStart);

        if (!p1Hit && !p2Hit) return;

        // ✅ 동시 히트면 "최근 입력자"가 득점
        if (p1Hit && p2Hit) {
            boolean p1Wins = r.lastP1Input >= r.lastP2Input;
            onScore(r, p1Wins);
            return;
        }

        if (p1Hit) onScore(r, true);
        else onScore(r, false);
    }

<<<<<<< Updated upstream
    /** 공격 리치 70px 적용 (칼 전진 포함) */
    private boolean hit(Player attacker, Player defender) {
        if (!attacker.attacking())
            return false;

        double reach = 70; // === 40 + bladeOffset(최대 30)
=======
    private boolean isAttacking(long now, long attackStart) {
        return attackStart >= 0 && (now - attackStart) <= 200; // 0.2초
    }
>>>>>>> Stashed changes

    // ✅ 서버가 bladeOffset을 시간 기반으로 직접 계산
    private double bladeOffset(long now, long attackStart) {
        double t = (now - attackStart) / 1000.0; // seconds
        if (t < 0) return 0;
        if (t < 0.1) {
            return (t / 0.1) * 30.0;
        } else if (t < 0.2) {
            return (1.0 - ((t - 0.1) / 0.1)) * 30.0;
        }
        return 0;
    }

    // ✅ 히트 판정: tip 위치가 실제로 전진/복귀
    private boolean hit(long now, Player attacker, Player defender, long attackStart) {
        double baseReach = 40.0;
        double extra = bladeOffset(now, attackStart); // 0~30
        double reach = baseReach + extra;

        // facingRight 방향으로 tip 계산
        double tipX = attacker.facingRight() ? (attacker.x() + reach) : (attacker.x() - reach);

        // x 기준: tip이 상대 중심에 충분히 가까우면 hit
        boolean xHit = Math.abs(tipX - defender.x()) < 20;

        // y는 고정(300), 그래도 안전하게 범위 둠
        boolean yHit = Math.abs(attacker.y() - defender.y()) < 40;

        return xHit && yHit;
    }

    private void onScore(RoomState r, boolean p1Scored) {
<<<<<<< Updated upstream
        long now = System.currentTimeMillis();
        if (now - r.lastScoreTime < 1000)
            return; // Debounce: 1 second cooldown

        r.lastScoreTime = now;

        if (p1Scored)
            r.score1++;
        else
            r.score2++;

        // 즉시 리스폰 (닉네임 유지)
        r.p1 = new Player("p1", r.p1.nickname(), 100, 400, true, false);
        r.p2 = new Player("p2", r.p2.nickname(), 700, 400, false, false);

        r.lastP1Update = r.lastP2Update = now;
=======
        if (p1Scored) r.score1++;
        else r.score2++;

        // ✅ 즉시 리스폰
        r.p1 = new Player("p1", 100, 300, true, false);
        r.p2 = new Player("p2", 700, 300, false, false);

        // ✅ 공격 상태 초기화
        r.p1AttackStart = -1;
        r.p2AttackStart = -1;

        long now = System.currentTimeMillis();
        r.lastP1Input = now;
        r.lastP2Input = now;

        // ✅ 리스폰 직후 move 덮어쓰기 방지(잠깐 락)
        r.respawnLockUntil = now + 200; // 0.2초만 잠금
>>>>>>> Stashed changes
    }

    private void handleChat(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        if (room == null) return;

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
        if (r == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("room", room);

        // attacking은 시간 기반으로 갱신해서 보내기
        long now = System.currentTimeMillis();
        boolean p1Atk = (r.p1 != null) && isAttacking(now, r.p1AttackStart);
        boolean p2Atk = (r.p2 != null) && isAttacking(now, r.p2AttackStart);

        if (r.p1 != null) data.put("p1", new Player(r.p1.id(), r.p1.x(), 300, r.p1.facingRight(), p1Atk));
        else data.put("p1", null);

        if (r.p2 != null) data.put("p2", new Player(r.p2.id(), r.p2.x(), 300, r.p2.facingRight(), p2Atk));
        else data.put("p2", null);

        data.put("score1", r.score1);
        data.put("score2", r.score2);
        data.put("gameStartTime", r.gameStartTime);

        broadcast(room, data);
    }

    private void send(WebSocket conn, Object obj) {
        try {
            conn.send(mapper.writeValueAsString(obj));
        } catch (Exception ignored) {}
    }

    private void broadcast(String room, Object obj) {
        try {
            String json = mapper.writeValueAsString(obj);
            for (var e : socketToRoom.entrySet()) {
                if (room.equals(e.getValue())) {
                    e.getKey().send(json);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Game server started");
    }

<<<<<<< Updated upstream
    public record Player(String id, String nickname, double x, double y, boolean facingRight, boolean attacking) {
    }

    public record Msg(String type, String room, String playerId, String nickname, double x, double y,
            boolean facingRight, boolean attacking, String chat) {
    }
=======
    public record Player(String id, double x, double y, boolean facingRight, boolean attacking) {}
>>>>>>> Stashed changes
}
