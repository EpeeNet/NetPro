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

    // WebSocket -> RoomId
    private final Map<WebSocket, String> connToRoom = new ConcurrentHashMap<>();
    // WebSocket -> PlayerId ("p1" or "p2")
    private final Map<WebSocket, String> connToPlayerId = new ConcurrentHashMap<>();

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

        String roomId = connToRoom.remove(conn);
        String playerId = connToPlayerId.remove(conn);

        if (roomId != null) {
            Set<WebSocket> set = rooms.get(roomId);
            if (set != null) {
                set.remove(conn);
                if (set.isEmpty()) {
                    rooms.remove(roomId);
                    states.remove(roomId);
                } else {
                    // 방에 사람이 남아있으면 상태 업데이트 및 알림
                    if (playerId != null) {
                        // 1. 채팅 알림
                        Map<String, String> chatMsg = Map.of(
                                "type", "chat",
                                "senderId", "System",
                                "text", "[System] " + playerId + " 님이 나갔습니다.");
                        broadcastChat(roomId, chatMsg);

                        // 2. GameState 업데이트 (캐릭터 제거)
                        GameState current = states.get(roomId);
                        if (current != null) {
                            Player p1 = current.p1();
                            Player p2 = current.p2();

                            if ("p1".equals(playerId))
                                p1 = null;
                            if ("p2".equals(playerId))
                                p2 = null;

                            GameState updated = new GameState(roomId, p1, p2, current.score1(), current.score2());
                            states.put(roomId, updated);
                            broadcastState(roomId);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            Msg msg = mapper.readValue(message, Msg.class);

            switch (msg.type()) {
                case "join" -> handleJoin(conn, msg);
                case "move" -> {
                    GameState current = states.get(msg.room());
                    if (current == null)
                        return;
                    GameState updated = applyMove(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());
                }
                case "attack" -> {
                    GameState current = states.get(msg.room());
                    if (current == null)
                        return;

                    int oldScore1 = current.score1();
                    int oldScore2 = current.score2();

                    GameState updated = applyAttack(current, msg);
                    states.put(msg.room(), updated);
                    broadcastState(msg.room());

                    int newScore1 = updated.score1();
                    int newScore2 = updated.score2();

                    // 점수 변화 확인
                    boolean scoreChanged = (newScore1 > oldScore1) || (newScore2 > oldScore2);

                    String text;
                    if (scoreChanged) { // 공격 성공
                        text = "[System] " + msg.playerId() + " +1 점 획득!";
                    } else { // 공격 실패
                        text = "[System] " + msg.playerId() + "가 공격을 시도했습니다.";
                    }

                    // 메시지 브로드캐스트
                    Map<String, String> chatMsg = Map.of(
                            "type", "chat",
                            "senderId", "System",
                            "text", text);
                    broadcastChat(msg.room(), chatMsg);
                }
                case "chat" -> {
                    // chat 필드는 "닉네임: 내용" 형태일 수 있음.
                    // senderId는 msg.playerId() 사용 (p1 or p2)
                    Map<String, String> chatMsg = Map.of(
                            "type", "chat",
                            "senderId", msg.playerId() == null ? "Unknown" : msg.playerId(),
                            "text", msg.chat());
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
        String nickname = msg.playerId(); // join 시에는 nickname이 들어옴

        // 소켓을 room에 넣음
        Set<WebSocket> conns = rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        conns.add(conn);

        // 현재 접속자 수 기준으로 p1/p2 배정
        String assignedId;
        double spawnX, spawnY;
        boolean facingRight;

        int count = conns.size();

        if (count == 1) {
            assignedId = "p1";
            spawnX = 100; // 왼쪽
            spawnY = 300;
            facingRight = true;

        } else if (count == 2) {
            assignedId = "p2";
            spawnX = 700; // 오른쪽
            spawnY = 300;
            facingRight = false;

        } else {
            // 이미 방이 가득 찼으면
            sendSimple(conn, "error", "Room full (only 2 players allowed)");
            return;
        }

        // GameState가 없다면 생성
        GameState st = states.get(roomId);
        if (st == null) {
            Player initP1 = assignedId.equals("p1") ? new Player("p1", spawnX, spawnY, facingRight, false) : null;
            Player initP2 = assignedId.equals("p2") ? new Player("p2", spawnX, spawnY, facingRight, false) : null;
            st = new GameState(roomId, initP1, initP2, 0, 0);
            states.put(roomId, st);
        }

        // 기존 상태 업데이트
        Player newPlayer = new Player(assignedId, spawnX, spawnY, facingRight, false);

        Player p1 = st.p1();
        Player p2 = st.p2();

        if (assignedId.equals("p1")) {
            st = new GameState(roomId, newPlayer, p2, st.score1(), st.score2());
        } else {
            st = new GameState(roomId, p1, newPlayer, st.score1(), st.score2());
        }

        states.put(roomId, st);

        // 배정된 ID 클라이언트에게 전달
        sendSimple(conn, "assign", Map.of("playerId", assignedId));

        // 전체에게 현재 상태 전달
        broadcastState(roomId);

        System.out.println("[" + roomId + "] " + nickname + " joined as " + assignedId);

        // 입장 메시지 브로드캐스트
        Map<String, String> chatMsg = Map.of(
                "type", "chat",
                "senderId", "System",
                "text", "[System] " + assignedId + " 가 방을 입장했습니다.");
        broadcastChat(roomId, chatMsg);

        // 연결 추적 맵 업데이트
        connToRoom.put(conn, roomId);
        connToPlayerId.put(conn, assignedId);
    }

    private void sendSimple(WebSocket conn, String type, String msg) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "type", type,
                    "msg", msg));
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
        if (state == null)
            return;

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
        String type, // "join", "move", "attack", "chat"
        String room, // 예: "room-001"
        String playerId, // join 시: nickname, 이후: "p1" / "p2"
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
