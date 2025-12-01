package org.epee.client;

import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class LoginScreen {

    private final Stage primaryStage;
    private final BiConsumer<String, String> onStartGame;

    public LoginScreen(Stage primaryStage, BiConsumer<String, String> onStartGame) {
        this.primaryStage = primaryStage;
        this.onStartGame = onStartGame;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // === Header ===
        javafx.scene.layout.VBox headerBox = new javafx.scene.layout.VBox(10);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER);
        headerBox.setPadding(new javafx.geometry.Insets(50, 0, 30, 0));

        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label("⚔ ÉPÉE ⚔");
        titleLabel.getStyleClass().add("title-label");

        javafx.scene.control.Label subtitleLabel = new javafx.scene.control.Label("실시간 펜싱 결투");
        subtitleLabel.getStyleClass().add("subtitle-label");

        headerBox.getChildren().addAll(titleLabel, subtitleLabel);
        root.setTop(headerBox);

        javafx.scene.layout.HBox mainContainer = new javafx.scene.layout.HBox(40);
        mainContainer.setAlignment(javafx.geometry.Pos.CENTER);
        mainContainer.setPadding(new javafx.geometry.Insets(0, 50, 50, 50));

        javafx.scene.layout.VBox loginPanel = new javafx.scene.layout.VBox(20);
        loginPanel.getStyleClass().add("panel-box");
        loginPanel.setPrefWidth(400);
        loginPanel.setMaxWidth(400);

        // "펜싱장 입장" 라벨 추가
        javafx.scene.control.Label panelHeader = new javafx.scene.control.Label("펜싱장 입장");
        panelHeader.getStyleClass().add("section-header");
        panelHeader.setMaxWidth(Double.MAX_VALUE);
        panelHeader.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.scene.layout.VBox.setMargin(panelHeader, new javafx.geometry.Insets(0, 0, 8, 0));

        // 닉네임 입력 필드
        javafx.scene.layout.VBox nickBox = new javafx.scene.layout.VBox(8);
        javafx.scene.control.Label nickLabel = new javafx.scene.control.Label("닉네임 입력");
        nickLabel.getStyleClass().add("input-label");
        javafx.scene.control.TextField nickInput = new javafx.scene.control.TextField();
        nickInput.setPromptText("이름을 입력하세요");
        javafx.scene.layout.VBox.setMargin(nickInput, new javafx.geometry.Insets(5, 0, 10, 0));
        nickBox.getChildren().addAll(nickLabel, nickInput);

        // 방 코드 입력 필드
        javafx.scene.layout.VBox roomBox = new javafx.scene.layout.VBox(8);
        javafx.scene.control.Label roomLabel = new javafx.scene.control.Label("방 코드 (선택사항)");
        roomLabel.getStyleClass().add("input-label");
        javafx.scene.control.TextField roomInput = new javafx.scene.control.TextField();
        roomInput.setPromptText("입력 또는 공백으로 두기");
        roomBox.getChildren().addAll(roomLabel, roomInput);

        // 버튼 (방 만들기, 참가하기)
        javafx.scene.control.Button createBtn = new javafx.scene.control.Button("방 만들기");
        createBtn.getStyleClass().addAll("button", "create-room-btn");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.VBox.setMargin(createBtn, new javafx.geometry.Insets(30, 0, 0, 0));

        javafx.scene.control.Button joinBtn = new javafx.scene.control.Button("방 참가하기");
        joinBtn.getStyleClass().addAll("button", "join-room-btn");
        joinBtn.setMaxWidth(Double.MAX_VALUE);

        loginPanel.getChildren().addAll(panelHeader, nickBox, roomBox, createBtn, joinBtn);

        // --- 오른쪽 열: 조작법 및 서버 상태 ---
        javafx.scene.layout.VBox rightColumn = new javafx.scene.layout.VBox(20);
        rightColumn.setPrefWidth(300);
        rightColumn.setMaxWidth(300);

        // 조작법 안내
        javafx.scene.layout.VBox controlsPanel = new javafx.scene.layout.VBox(15);
        controlsPanel.getStyleClass().add("panel-box");
        javafx.scene.control.Label controlsHeader = new javafx.scene.control.Label("조작법");
        controlsHeader.getStyleClass().add("section-header");
        controlsPanel.getChildren().add(controlsHeader);

        // 조작법 목록 (아이콘 대신 텍스트/이모지 사용)
        controlsPanel.getChildren().add(createKeyRow("↑ 전진", "W"));
        controlsPanel.getChildren().add(createKeyRow("↓ 후진", "S"));
        controlsPanel.getChildren().add(createKeyRow("⚡ 찌르기 (공격)", "F"));
        controlsPanel.getChildren().add(createKeyRow("🛡 막기 (방어)", "Shift"));
        controlsPanel.getChildren().add(createKeyRow("↑ 점프", "J"));

        // 서버 상태 표시
        javafx.scene.layout.VBox statusPanel = new javafx.scene.layout.VBox(10);
        statusPanel.getStyleClass().add("panel-box");
        javafx.scene.control.Label statusHeader = new javafx.scene.control.Label("서버 상태");
        statusHeader.getStyleClass().add("section-header");
        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("📶 온라인");
        statusLabel.getStyleClass().add("status-online");
        statusPanel.getChildren().addAll(statusHeader, statusLabel);

        // '온라인' 표시
        statusLabel.setText("📶 Online");
        if (!statusLabel.getStyleClass().contains("status-online")) {
            statusLabel.getStyleClass().add("status-online");
        }
        statusLabel.getStyleClass().remove("status-offline");

        rightColumn.getChildren().addAll(controlsPanel, statusPanel);

        // 메인 컨테이너에 패널 추가
        mainContainer.getChildren().addAll(loginPanel, rightColumn);

        // 메인 컨테이너를 화면 중앙에 배치
        javafx.scene.layout.VBox centerBox = new javafx.scene.layout.VBox(20); // Keep centerBox for consistent
                                                                               // padding/alignment if needed
        centerBox.setAlignment(javafx.geometry.Pos.CENTER);
        centerBox.setPadding(new javafx.geometry.Insets(0, 50, 0, 50));
        centerBox.getChildren().clear(); // 이전 내용 지우기
        centerBox.getChildren().add(mainContainer);
        root.setCenter(centerBox);
        root.setRight(null); // 기존 오른쪽 박스 제거

        // 버튼 동작 설정
        createBtn.setOnAction(e -> {
            String nick = nickInput.getText().trim();
            String room = roomInput.getText().trim();
            if (room.isEmpty())
                room = "room-" + (int) (Math.random() * 1000);
            onStartGame.accept(nick, room);
        });

        joinBtn.setOnAction(e -> {
            String nick = nickInput.getText().trim();
            String room = roomInput.getText().trim();
            if (nick.isEmpty()) {
                // Show error or shake
                return;
            }
            if (room.isEmpty()) {
                // Show error
                return;
            }
            onStartGame.accept(nick, room);
        });

        // 창 크기 조절 및 반응형 레이아웃 설정
        // 반응형: 루트를 스크롤 패널로 감쌈
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("root"); // 스크롤 패널에도 어두운 배경 적용

        // 초기 창 크기 설정 (1000x700)
        Scene scene = new Scene(scrollPane, 1000, 700); // Increased size per user request
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("ÉPÉE - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private javafx.scene.layout.HBox createKeyRow(String action, String key) {
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("key-row");

        javafx.scene.control.Label actionLabel = new javafx.scene.control.Label(action);
        actionLabel.getStyleClass().add("action-label");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.Label keyLabel = new javafx.scene.control.Label(key);
        keyLabel.getStyleClass().add("key-box");

        row.getChildren().addAll(actionLabel, spacer, keyLabel);
        return row;
    }
}
