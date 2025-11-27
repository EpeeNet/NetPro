package org.epee.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

            // 룸에 소켓 등록
            rooms.computeIfAbsent(msg.room(), k -> ConcurrentHashMap.newKeySet()).add(conn);

            // 기본 상태 없으면 생성
            states.computeIfAbsent(msg.room(), this::defaultState);

            GameState current = states.get(msg.room());

            switch (msg.type()) {
                case "move" -> {
                    GameState updated = applyMove(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());
                }
                case "attack" -> {
                    GameState updated = applyAttack(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());
                }
                case "chat" -> {
                    String text = msg.playerId() + ": " + msg.chat();

                    // 브로드캐스트용 JSON
                    Map<String, String> chatMsg = Map.of(
                            "type", "chat",
                            "text", text
                    );

                    // 전체 룸에게 전송
                    broadcastChat(msg.room(), chatMsg);
                }
                default -> System.out.println("Unknown message type: " + msg.type());
            }
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

    private GameState defaultState(String room) {
        Player p1 = new Player("p1", 100, 300, true, false);
        Player p2 = new Player("p2", 700, 300, false, false);
        return new GameState(room, p1, p2, 0, 0);
    }

    private GameState applyMove(GameState st, Msg m) {
        Player p1 = st.p1();
        Player p2 = st.p2();

        if ("p1".equals(m.playerId())) {
            p1 = new Player(p1.id(), m.x(), m.y(), m.facingRight(), p1.attacking());
        } else if ("p2".equals(m.playerId())) {
            p2 = new Player(p2.id(), m.x(), m.y(), m.facingRight(), p2.attacking());
        }
        return new GameState(st.room(), p1, p2, st.score1(), st.score2());
    }

    private GameState applyAttack(GameState st, Msg m) {
        Player p1 = st.p1();
        Player p2 = st.p2();
        boolean p1Hit = false;
        boolean p2Hit = false;

        if ("p1".equals(m.playerId())) {
            p1 = new Player(p1.id(), p1.x(), p1.y(), p1.facingRight(), true);
            p1Hit = hit(p1, p2);
        } else if ("p2".equals(m.playerId())) {
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
        String type,        // "move", "attack"
        String room,        // 예: "room-001"
        String playerId,    // "p1" or "p2"
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
