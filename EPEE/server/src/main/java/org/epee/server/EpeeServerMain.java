package org.epee.server;

public class EpeeServerMain {

    public static void main(String[] args) {
        int port = 8080;
        GameServer server = new GameServer(port);
        server.start();
        System.out.println("ÉPÉE WebSocket Server started on ws://localhost:" + port);
    }
}
