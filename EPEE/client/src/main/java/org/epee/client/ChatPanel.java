package org.epee.client;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ChatPanel {

    private VBox root;
    private TextArea chatArea;
    private TextField input;
    private final FencingClientApp app;

    public ChatPanel(FencingClientApp app) {
        this.app = app;

        root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setPrefWidth(260);
        root.setStyle("-fx-background-color: #000;");

        // ★ 채팅창 생성
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(430);

        // ★ 가로 길이 100%
        chatArea.setMaxWidth(Double.MAX_VALUE);

        chatArea.setStyle("-fx-control-inner-background: #000; -fx-text-fill: white;");

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

        root.getChildren().addAll(chatArea, row);
    }

    private void sendMessage() {
        String text = input.getText();
        if (text.isBlank()) return;

        app.sendChat(text);
        input.clear();
    }

    public void appendMessage(String msg) {
        chatArea.appendText(msg + "\n");
    }

    public VBox getView() {
        return root;
    }
}
