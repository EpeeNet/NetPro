package org.epee.server;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 순수 Java-WebSocket 기반 1:1 펜싱 게임 서버
 */
public class GameServer extends WebSocketServer {

    private final ObjectMapper mapper = new ObjectMapper();

    // roomId -> 접속한 소켓들
    private final Map<String, Set<WebSocket>> rooms = new ConcurrentHashMap<>();

    // roomId -> GameState
    private final Map<String, GameState> states = new ConcurrentHashMap<>();

    public GameServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Client disconnected: " + conn.getRemoteSocketAddress());
        // 모든 룸에서 제거
        rooms.values().forEach(set -> set.remove(conn));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            Msg msg = mapper.readValue(message, Msg.class);

            switch (msg.type()) {
                case "join" -> handleJoin(conn, msg);
                case "move" -> {
                    GameState current = states.get(msg.room());
                    if (current == null) return;
                    GameState updated = applyMove(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());
                }
                case "attack" -> {
                    GameState current = states.get(msg.room());
                    if (current == null) return;
                    GameState updated = applyAttack(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());
                }
                case "chat" -> {
                    // chat 필드는 이미 "닉네임: 내용" 형태로 들어온다고 가정하고 그대로 전달
                    Map<String, String> chatMsg = Map.of(
                            "type", "chat",
                            "text", msg.chat()
                    );
                    broadcastChat(msg.room(), chatMsg);
                }
                default -> System.out.println("Unknown message type: " + msg.type());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleJoin(WebSocket conn, Msg msg) {
        String roomId = msg.room();
        String nickname = msg.playerId(); // join 시에는 여기로 닉네임이 들어옴

        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(conn);

        GameState st = states.computeIfAbsent(roomId, this::emptyState);

        Player p1 = st.p1();
        Player p2 = st.p2();

        String assignedId;
        double spawnX, spawnY;
        boolean facingRight;

        if (p1 == null) {
            assignedId = "p1";
            spawnX = 100;
            spawnY = 300;
            facingRight = true;
            p1 = new Player(assignedId, spawnX, spawnY, facingRight, false);
        } else if (p2 == null) {
            assignedId = "p2";
            spawnX = 700;
            spawnY = 300;
            facingRight = false;
            p2 = new Player(assignedId, spawnX, spawnY, facingRight, false);
        } else {
            // 이미 2명 다 참
            sendSimple(conn, "error", "Room full");
            return;
        }

        System.out.println("[" + roomId + "] " + nickname + " joined as " + assignedId);

        GameState updated = new GameState(roomId, p1, p2, st.score1(), st.score2());
        states.put(roomId, updated);

        // 해당 클라이언트에게 배정된 playerId 알려주기
        sendSimple(conn, "assign", Map.of("playerId", assignedId));

        // 전체에게 현재 상태 브로드캐스트
        broadcastState(roomId);
    }

    private void sendSimple(WebSocket conn, String type, String msg) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "type", type,
                    "msg", msg
            ));
            conn.send(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSimple(WebSocket conn, String type, Map<String, String> payload) {
        try {
            Map<String, String> merged = new ConcurrentHashMap<>();
            merged.put("type", type);
            merged.putAll(payload);
            String json = mapper.writeValueAsString(merged);
            conn.send(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Server error:");
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("GameServer started!");
    }

    private GameState emptyState(String room) {
        return new GameState(room, null, null, 0, 0);
    }

    private GameState applyMove(GameState st, Msg m) {
        Player p1 = st.p1();
        Player p2 = st.p2();

        if ("p1".equals(m.playerId()) && p1 != null) {
            p1 = new Player(p1.id(), m.x(), m.y(), m.facingRight(), p1.attacking());
        } else if ("p2".equals(m.playerId()) && p2 != null) {
            p2 = new Player(p2.id(), m.x(), m.y(), m.facingRight(), p2.attacking());
        }
        return new GameState(st.room(), p1, p2, st.score1(), st.score2());
    }

    private GameState applyAttack(GameState st, Msg m) {
        Player p1 = st.p1();
        Player p2 = st.p2();
        boolean p1Hit = false;
        boolean p2Hit = false;

        if ("p1".equals(m.playerId()) && p1 != null && p2 != null) {
            p1 = new Player(p1.id(), p1.x(), p1.y(), p1.facingRight(), true);
            p1Hit = hit(p1, p2);
        } else if ("p2".equals(m.playerId()) && p1 != null && p2 != null) {
            p2 = new Player(p2.id(), p2.x(), p2.y(), p2.facingRight(), true);
            p2Hit = hit(p2, p1);
        }

        int newScore1 = st.score1() + (p1Hit ? 1 : 0);
        int newScore2 = st.score2() + (p2Hit ? 1 : 0);

        return new GameState(st.room(), p1, p2, newScore1, newScore2);
    }

    private boolean hit(Player atk, Player def) {
        double dx = Math.abs(atk.x() - def.x());
        boolean facingOk = (atk.facingRight() && atk.x() <= def.x())
                || (!atk.facingRight() && atk.x() >= def.x());
        return dx < 60 && facingOk;
    }

    private void broadcastState(String roomId) {
        GameState state = states.get(roomId);
        if (state == null) return;

        try {
            String json = mapper.writeValueAsString(state);
            Set<WebSocket> conns = rooms.getOrDefault(roomId, Set.of());
            for (WebSocket c : conns) {
                c.send(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastChat(String roomId, Map<String, String> msg) {
        try {
            String json = mapper.writeValueAsString(msg);
            Set<WebSocket> conns = rooms.getOrDefault(roomId, Set.of());
            for (WebSocket c : conns) {
                c.send(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/** ==== 서버측 record 정의 ==== */

record Msg(
        String type,        // "join", "move", "attack", "chat"
        String room,        // 예: "room-001"
        String playerId,    // join 시: nickname, 이후: "p1" / "p2"
        double x,
        double y,
        boolean facingRight,
        String chat
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
