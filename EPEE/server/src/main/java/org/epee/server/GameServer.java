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

    private static class RoomState {
        Player p1 = null;
        Player p2 = null;

        int score1 = 0;
        int score2 = 0;

        // ✅ "최근 입력" 기준으로 우선순위 판단 (move spam 때문에 update 시간 쓰면 안됨)
        long lastP1Input = 0;
        long lastP2Input = 0;

        // ✅ 공격 시작 시각(서버 기준) - 공격 윈도우 & bladeOffset 계산용
        long p1AttackStart = -1;
        long p2AttackStart = -1;

        // ✅ attacking true/false 전환 감지용
        boolean p1WasAttacking = false;
        boolean p2WasAttacking = false;

        // ✅ 득점 직후 잠깐 move 무시(리스폰 덮임 방지)
        long respawnLockUntil = 0;

        // 기존
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
                if ("p1".equals(pid)) r.p1 = null;
                if ("p2".equals(pid)) r.p2 = null;

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
        String nickname = (String) map.get("nickname"); // ✅ 클라 join에서 nickname 사용
        if (nickname == null) nickname = "";

        rooms.putIfAbsent(room, new RoomState());
        RoomState r = rooms.get(room);

        String assigned;

        // ✅ 새 매치 시작 느낌: 한쪽이라도 비어있으면 점수 리셋
        if (r.p1 == null || r.p2 == null) {
            r.score1 = 0;
            r.score2 = 0;
            r.lastP1Input = 0;
            r.lastP2Input = 0;
            r.p1AttackStart = -1;
            r.p2AttackStart = -1;
            r.p1WasAttacking = false;
            r.p2WasAttacking = false;
            r.respawnLockUntil = 0;
            r.lastScoreTime = 0;
            r.gameStartTime = 0;
        }

        if (r.p1 == null) {
            assigned = "p1";
            r.p1 = new Player("p1", nickname, 100, 400, true, false);
        } else if (r.p2 == null) {
            assigned = "p2";
            r.p2 = new Player("p2", nickname, 700, 400, false, false);
            r.gameStartTime = System.currentTimeMillis(); // P2 입장 시 경기 시작
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

        long now = System.currentTimeMillis();

        // ✅ 득점 직후 잠깐은 move 무시 (리스폰 덮임 방지)
        if (now < r.respawnLockUntil) {
            broadcastState(room);
            return;
        }

        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        boolean facing = (Boolean) map.get("facingRight");

        // ✅ attacking 필드가 올 수도 있고 안 올 수도 있으니 안전 처리
        boolean attackingFlag = false;
        Object attackingObj = map.get("attacking");
        if (attackingObj instanceof Boolean b) attackingFlag = b;

        if ("p1".equals(pid)) {
            if (r.p1 == null) return;

            boolean moved = Math.abs(x - r.p1.x()) > 0.1 || Math.abs(y - r.p1.y()) > 0.1;

            // ✅ 공격 시작 감지( false -> true )
            if (attackingFlag && !r.p1WasAttacking) {
                r.p1AttackStart = now;
                r.lastP1Input = now; // 최근 입력 갱신
            }
            r.p1WasAttacking = attackingFlag;

            if (moved) r.lastP1Input = now; // ✅ 실제로 위치가 바뀐 경우만 입력으로 취급

            boolean attackingNow = isAttacking(now, r.p1AttackStart);
            r.p1 = new Player("p1", r.p1.nickname(), x, y, facing, attackingNow);

        } else {
            if (r.p2 == null) return;

            boolean moved = Math.abs(x - r.p2.x()) > 0.1 || Math.abs(y - r.p2.y()) > 0.1;

            if (attackingFlag && !r.p2WasAttacking) {
                r.p2AttackStart = now;
                r.lastP2Input = now;
            }
            r.p2WasAttacking = attackingFlag;

            if (moved) r.lastP2Input = now;

            boolean attackingNow = isAttacking(now, r.p2AttackStart);
            r.p2 = new Player("p2", r.p2.nickname(), x, y, facing, attackingNow);
        }

        // ✅ move 때도 판정
        checkHitWithPriority(r, now);
        broadcastState(room);
    }

    private void handleAttack(WebSocket conn, Map<String, Object> map) {
        // ✅ attack 메시지도 유지 (move에 attacking이 안 들어오거나, 즉시 반응용)
        String room = socketToRoom.get(conn);
        String pid = socketToPlayerId.get(conn);
        if (room == null || pid == null) return;

        RoomState r = rooms.get(room);
        if (r == null) return;

        long now = System.currentTimeMillis();

        if (now < r.respawnLockUntil) {
            broadcastState(room);
            return;
        }

        if ("p1".equals(pid)) {
            if (r.p1 == null) return;
            r.p1AttackStart = now;
            r.p1WasAttacking = true;
            r.lastP1Input = now;
            r.p1 = new Player(r.p1.id(), r.p1.nickname(), r.p1.x(), r.p1.y(), r.p1.facingRight(), true);
        } else {
            if (r.p2 == null) return;
            r.p2AttackStart = now;
            r.p2WasAttacking = true;
            r.lastP2Input = now;
            r.p2 = new Player(r.p2.id(), r.p2.nickname(), r.p2.x(), r.p2.y(), r.p2.facingRight(), true);
        }

        checkHitWithPriority(r, now);
        broadcastState(room);
    }

    // ✅ 최근 입력자 우선 + 득점 1회만
    private void checkHitWithPriority(RoomState r, long now) {
        if (r.p1 == null || r.p2 == null) return;

        boolean p1First = r.lastP1Input >= r.lastP2Input;

        if (p1First) {
            if (hit(r, true, now)) { onScore(r, true, now); return; }
            if (hit(r, false, now)) { onScore(r, false, now); }
        } else {
            if (hit(r, false, now)) { onScore(r, false, now); return; }
            if (hit(r, true, now)) { onScore(r, true, now); }
        }
    }

    // ✅ p1Attacker = true면 p1이 공격자
    private boolean hit(RoomState r, boolean p1Attacker, long now) {
        Player attacker = p1Attacker ? r.p1 : r.p2;
        Player defender = p1Attacker ? r.p2 : r.p1;

        if (attacker == null || defender == null) return false;

        // ✅ 공격 윈도우 내에서만 판정
        long start = p1Attacker ? r.p1AttackStart : r.p2AttackStart;
        if (!isAttacking(now, start)) return false;

        // ✅ 서버에서 bladeOffset 계산해서 팁 이동 반영
        double offset = bladeOffset(now, start); // 0~30
        double reach = 40 + offset;             // 40 + 0~30 = 40~70

        double tipX = attacker.facingRight() ? attacker.x() + reach : attacker.x() - reach;

        return Math.abs(tipX - defender.x()) < 20
                && Math.abs(attacker.y() - defender.y()) < 40;
    }

    private boolean isAttacking(long now, long start) {
        if (start < 0) return false;
        long dt = now - start;
        return dt >= 0 && dt <= 200; // 0.2초 공격 윈도우
    }

    // ✅ 0~0.1 전진, 0.1~0.2 복귀 (삼각파)
    private double bladeOffset(long now, long start) {
        if (start < 0) return 0.0;
        double t = (now - start) / 1000.0; // sec
        if (t < 0) return 0.0;
        if (t < 0.1) return (t / 0.1) * 30.0;
        if (t < 0.2) return (1.0 - ((t - 0.1) / 0.1)) * 30.0;
        return 0.0;
    }

    private void onScore(RoomState r, boolean p1Scored, long now) {
        // ✅ 디바운스(연속 득점 방지)
        if (now - r.lastScoreTime < 250) return;
        r.lastScoreTime = now;

        if (p1Scored) r.score1++;
        else r.score2++;

        // ✅ 즉시 리스폰 (닉 유지)
        r.p1 = new Player("p1", r.p1.nickname(), 100, 400, true, false);
        r.p2 = new Player("p2", r.p2.nickname(), 700, 400, false, false);

        // ✅ 공격 상태 리셋
        r.p1AttackStart = -1;
        r.p2AttackStart = -1;
        r.p1WasAttacking = false;
        r.p2WasAttacking = false;

        // ✅ 리스폰 직후 move 덮임 방지 락
        r.respawnLockUntil = now + 200;
    }

    private void handleChat(WebSocket conn, Map<String, Object> map) {
        String room = socketToRoom.get(conn);
        if (room == null) return;

        String senderId = socketToPlayerId.get(conn);
        String text = (String) map.get("chat");

        RoomState r = rooms.get(room);
        String nick = "";
        if (r != null) {
            if ("p1".equals(senderId) && r.p1 != null) nick = r.p1.nickname();
            if ("p2".equals(senderId) && r.p2 != null) nick = r.p2.nickname();
        }

        broadcast(room, Map.of(
                "type", "chat",
                "senderId", senderId,
                "nickname", nick,
                "text", text
        ));
    }

    private void sendError(WebSocket conn, String msg) {
        send(conn, Map.of("type", "error", "msg", msg));
    }

    private void broadcastState(String room) {
        RoomState r = rooms.get(room);
        if (r == null) return;

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
        } catch (Exception ignored) {}
    }

    private void broadcast(String room, Object obj) {
        try {
            String json = mapper.writeValueAsString(obj);
            for (var e : socketToRoom.entrySet()) {
                if (room.equals(e.getValue())) e.getKey().send(json);
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

    public record Player(String id, String nickname, double x, double y, boolean facingRight, boolean attacking) {}
}
