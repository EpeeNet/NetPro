package org.epee.client;

import javafx.geometry.Insets;
import javafx.scene.control.Button;

import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.control.Label;

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
        root.getStyleClass().add("chat-root");

        // ★ 채팅창 생성 (Rich Text 지원을 위해 VBox + ScrollPane 사용)
        chatBox = new VBox(5);
        chatBox.getStyleClass().add("chat-content");
        chatBox.setFillWidth(true);

        scrollPane = new javafx.scene.control.ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        // scrollPane.setPrefHeight(430); // 고정 높이 제거
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS); // 가용 공간 채우기
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);

        input = new TextField();
        input.setPromptText("메시지 입력...");
        input.getStyleClass().add("chat-input");

        Button sendBtn = new Button("Send");

        // ★ send 버튼 흰색 -> 테마색
        sendBtn.getStyleClass().add("send-btn");

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
        app.requestGameFocus(); // 게임으로 포커스 반환
    }

    public void addSystemMessage(String msg) {
        appendMessage("System", "System", msg);
    }

    public void addSystemMessage(String prefix, String highlight, String suffix) {
        Platform.runLater(() -> {
            VBox messageContainer = new VBox(2);
            HBox row = new HBox();

            javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
            flow.getStyleClass().add("system-msg");
            flow.setMaxWidth(240);
            flow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            javafx.scene.text.Text txtPrefix = new javafx.scene.text.Text(prefix);
            txtPrefix.setFill(javafx.scene.paint.Color.web("#888888")); // 시스템 텍스트 색상

            javafx.scene.text.Text txtHighlight = new javafx.scene.text.Text(highlight);
            txtHighlight.setFill(getColorForNickname(highlight));
            txtHighlight.setStyle("-fx-font-weight: bold;");

            javafx.scene.text.Text txtSuffix = new javafx.scene.text.Text(suffix);
            txtSuffix.setFill(javafx.scene.paint.Color.web("#888888")); // 시스템 텍스트 색상

            flow.getChildren().addAll(txtPrefix, txtHighlight, txtSuffix);

            row.getChildren().add(flow);
            row.setAlignment(javafx.geometry.Pos.CENTER);
            chatBox.getChildren().add(row);

            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }

    private javafx.scene.paint.Color getColorForNickname(String name) {
        int hash = name.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = hash & 0x0000FF;
        return javafx.scene.paint.Color.rgb(r, g, b).brighter(); // 어두운 배경에서 잘 보이도록 밝게 조정
    }

    public void appendMessage(String senderId, String senderName, String msg) {
        final String finalSenderId = (senderId == null) ? "Unknown" : senderId;

        Platform.runLater(() -> {
            // 시스템 메시지 처리
            if ("System".equals(finalSenderId)) {
                HBox row = new HBox();
                Label sysLabel = new Label(msg);
                sysLabel.getStyleClass().add("system-msg");
                sysLabel.setWrapText(true);
                sysLabel.setMaxWidth(240);
                sysLabel.setAlignment(javafx.geometry.Pos.CENTER);

                row.getChildren().add(sysLabel);
                row.setAlignment(javafx.geometry.Pos.CENTER);
                chatBox.getChildren().add(row);

                scrollPane.layout();
                scrollPane.setVvalue(1.0);
                return;
            }

            // 일반 채팅
            boolean isMe = finalSenderId.equals(app.getPlayerId());
            String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            VBox msgContainer = new VBox(2);

            // 이름 라벨 (헤더)
            Label nameLabel = new Label(senderName);
            nameLabel.getStyleClass().add("chat-name");

            // 시간 라벨
            Label timeLabel = new Label(timeStr);
            timeLabel.getStyleClass().add("chat-time");
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888; -fx-padding: 0 5 2 5;"); // 하단 패딩 정렬

            // 메시지 말풍선
            Label msgLabel = new Label(msg);
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(200);
            msgLabel.getStyleClass().add("chat-bubble");

            HBox contentRow = new HBox(5); // 말풍선과 시간 사이 간격
            contentRow.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT); // 기본 하단 정렬

            if (isMe) {
                // 내 메시지: 노란 말풍선, 검은 텍스트
                msgLabel.getStyleClass().add("chat-bubble-me");
                // 레이아웃: [시간] [말풍선] (우측 정렬)
                contentRow.getChildren().addAll(timeLabel, msgLabel);
                contentRow.setAlignment(javafx.geometry.Pos.BOTTOM_RIGHT);

                msgContainer.getChildren().addAll(nameLabel, contentRow);
                msgContainer.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
            } else {
                // 상대방 메시지: 어두운 말풍선, 흰 텍스트
                msgLabel.getStyleClass().add("chat-bubble-other");
                // 레이아웃: [말풍선] [시간] (좌측 정렬)
                contentRow.getChildren().addAll(msgLabel, timeLabel);
                contentRow.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);

                msgContainer.getChildren().addAll(nameLabel, contentRow);
                msgContainer.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            }

            HBox row = new HBox(msgContainer);
            if (isMe) {
                row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            } else {
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            chatBox.getChildren().add(row);

            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }

    public void appendSystemMessageWithHighlight(String prefix, String highlight, String suffix, String colorHex) {
        Platform.runLater(() -> {
            HBox row = new HBox();
            row.setAlignment(javafx.geometry.Pos.CENTER);

            javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
            flow.getStyleClass().add("system-msg-flow");
            flow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            flow.setMaxWidth(240);

            javafx.scene.text.Text text1 = new javafx.scene.text.Text(prefix == null ? "" : prefix);
            text1.setFill(javafx.scene.paint.Color.web("#888888")); // 기본 시스템 텍스트 색상
            text1.setStyle("-fx-font-size: 10px;"); // 작게

            javafx.scene.text.Text text2 = new javafx.scene.text.Text(highlight == null ? "Unknown" : highlight);
            text2.setFill(javafx.scene.paint.Color.web(colorHex));
            text2.setStyle("-fx-font-size: 10px; -fx-font-weight: normal;"); // 작고 얇게 (보통 굵기)

            javafx.scene.text.Text text3 = new javafx.scene.text.Text(suffix == null ? "" : suffix);
            text3.setFill(javafx.scene.paint.Color.web("#888888"));
            text3.setStyle("-fx-font-size: 10px;"); // 작게

            flow.getChildren().addAll(text1, text2, text3);

            VBox container = new VBox(flow);
            container.getStyleClass().add("system-msg");
            container.setMaxWidth(240);
            container.setAlignment(javafx.geometry.Pos.CENTER);

            row.getChildren().add(container);
            chatBox.getChildren().add(row);
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }

    public VBox getView() {
        return root;
    }
}
