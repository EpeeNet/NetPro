package org.epee.server;

public class EpeeServerMain {
    public static void main(String[] args) {
        GameServer server = new GameServer(8080);
        server.start();
        System.out.println("ÉPÉE WebSocket Server started on ws://localhost:8080");
    }
}
