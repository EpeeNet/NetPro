package org.epee.client;

import javafx.geometry.Insets;
import javafx.scene.control.Button;

import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ChatPanel {
    private VBox root;
    private javafx.scene.control.ScrollPane scrollPane;
    private VBox chatBox;
    private TextField input;
    private final FencingClientApp app;

    public ChatPanel(FencingClientApp app) {
        this.app = app;

        root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setPrefWidth(260);
        root.setStyle("-fx-background-color: #000;");

        // ★ 채팅창 생성 (Rich Text 지원을 위해 VBox + ScrollPane 사용)
        chatBox = new VBox(5);
        chatBox.setStyle("-fx-background-color: #000;");
        chatBox.setFillWidth(true);

        scrollPane = new javafx.scene.control.ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(430);
        scrollPane.setStyle("-fx-background: #000; -fx-background-color: #000;");
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);

        input = new TextField();
        input.setPromptText("메시지 입력...");
        input.setStyle("-fx-control-inner-background: #222; -fx-text-fill: white;");

        Button sendBtn = new Button("Send");

        // ★ send 버튼 흰색
        sendBtn.setStyle("-fx-background-color: white; -fx-text-fill: black;");

        sendBtn.setOnAction(e -> sendMessage());
        input.setOnAction(e -> sendMessage());

        HBox row = new HBox(5, input, sendBtn);

        // input이 더 넓게
        input.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(input, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().addAll(scrollPane, row);
    }

    private void sendMessage() {
        String text = input.getText();
        if (text.isBlank())
            return;

        app.sendChat(text);
        input.clear();
    }

    public void appendMessage(String senderId, String msg) {
        if (senderId == null)
            senderId = "Unknown";

        VBox messageContainer = new VBox(2);
        HBox row = new HBox();

        // 시스템 메시지 처리
        if ("System".equals(senderId)) {
            javafx.scene.text.TextFlow sysFlow = new javafx.scene.text.TextFlow();
            sysFlow.setStyle("-fx-padding: 5; -fx-background-color: transparent;");
            sysFlow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            // "p1", "p2"를 기준으로 토큰화
            String[] tokens = msg.split("(?<=p1)|(?=p1)|(?<=p2)|(?=p2)");

            for (String token : tokens) {
                javafx.scene.text.Text textNode = new javafx.scene.text.Text(token);
                if ("p1".equals(token)) {
                    textNode.setFill(javafx.scene.paint.Color.CORNFLOWERBLUE);
                    textNode.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");
                } else if ("p2".equals(token)) {
                    textNode.setFill(javafx.scene.paint.Color.SALMON);
                    textNode.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");
                } else {
                    textNode.setFill(javafx.scene.paint.Color.web("#aaa"));
                    textNode.setStyle("-fx-font-size: 10px;");
                }
                sysFlow.getChildren().add(textNode);
            }

            row.getChildren().add(sysFlow);
            row.setAlignment(javafx.geometry.Pos.CENTER);
            chatBox.getChildren().add(row);

            scrollPane.layout();
            scrollPane.setVvalue(1.0);
            return;
        }

        // 일반 채팅 (p1, p2)
        boolean isMe = senderId.equals(app.getPlayerId());

        // 1) 닉네임 라벨
        javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(senderId);
        nameLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 10px;");

        // 2) 말풍선 (StackPane + Label/TextFlow)
        javafx.scene.control.Label bubbleLabel = new javafx.scene.control.Label(msg);
        bubbleLabel.setWrapText(true);
        bubbleLabel.setMaxWidth(200);
        bubbleLabel.setPadding(new Insets(8));

        if (isMe) {
            // 내 채팅: 오른쪽 정렬, 노란색/연두색 계열
            bubbleLabel.setStyle("-fx-background-color: #dcf8c6; -fx-text-fill: black; -fx-background-radius: 10;");
            messageContainer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            messageContainer.getChildren().addAll(nameLabel, bubbleLabel);
        } else {
            // 상대 채팅: 왼쪽 정렬, 흰색
            bubbleLabel.setStyle("-fx-background-color: white; -fx-text-fill: black; -fx-background-radius: 10;");
            messageContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            messageContainer.getChildren().addAll(nameLabel, bubbleLabel);
        }

        row.getChildren().add(messageContainer);
        chatBox.getChildren().add(row);

        // 자동 스크롤
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    public VBox getView() {
        return root;
    }
}
