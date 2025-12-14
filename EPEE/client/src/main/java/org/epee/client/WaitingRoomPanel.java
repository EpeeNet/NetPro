package org.epee.client;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class WaitingRoomPanel {

    private final BorderPane view;
    private final String roomCode;
    private final String hostName;
    private final Runnable onCancel;

    public WaitingRoomPanel(String roomCode, String hostName, Runnable onCancel) {
        this.roomCode = roomCode;
        this.hostName = hostName;
        this.onCancel = onCancel;
        this.view = createView();
    }

    public BorderPane getView() {
        return view;
    }

    private BorderPane createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root"); // 메인 배경 스타일 재사용
        VBox contentBox = new VBox(30);
        contentBox.setAlignment(Pos.CENTER);
        // contentBox.setMaxWidth(800); // 화면을 채우기 위해 최대 너비 제한 제거
        contentBox.getStyleClass().add("waiting-room-container");

        // 1. 제목 영역
        VBox titleBox = new VBox(10);
        titleBox.setAlignment(Pos.CENTER);
        Label titleLabel = new Label("⚔ ÉPÉE ⚔");
        titleLabel.getStyleClass().add("title-label");
        Label subTitle = new Label("방 대기 중");
        subTitle.getStyleClass().add("subtitle-label");
        titleBox.getChildren().addAll(titleLabel, subTitle);

        // 2. 방 코드 영역
        VBox codeBox = new VBox(10);
        codeBox.setAlignment(Pos.CENTER);

        HBox codeDisplayBox = new HBox(10);
        codeDisplayBox.setAlignment(Pos.CENTER);

        Label codeValue = new Label(roomCode);
        codeValue.getStyleClass().add("room-code-display");

        Button copyBtn = new Button("❐");
        copyBtn.getStyleClass().add("copy-btn");
        copyBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(roomCode);
            clipboard.setContent(content);

            // 선택사항: 토스트 메시지나 피드백 표시
        });

        // 버튼 높이를 입력창(Label) 높이와 동일하게 설정하고, 너비도 높이와 같게 설정 (정사각형)
        copyBtn.prefHeightProperty().bind(codeValue.heightProperty());
        copyBtn.prefWidthProperty().bind(copyBtn.prefHeightProperty());

        codeDisplayBox.getChildren().addAll(codeValue, copyBtn);
        codeBox.getChildren().addAll(codeDisplayBox);

        // 3. 플레이어 목록 영역
        VBox playerListBox = new VBox(10);
        playerListBox.getStyleClass().add("player-list-box");

        HBox hostRow = new HBox();
        hostRow.setAlignment(Pos.CENTER_LEFT);
        Label hostLabel = new Label("방장");
        hostLabel.getStyleClass().add("player-role-label");
        Label hostNameLabel = new Label(hostName);
        hostNameLabel.getStyleClass().add("player-name-label");
        HBox.setHgrow(hostNameLabel, javafx.scene.layout.Priority.ALWAYS);
        hostNameLabel.setMaxWidth(Double.MAX_VALUE);
        hostNameLabel.setAlignment(Pos.CENTER_RIGHT);
        hostRow.getChildren().addAll(hostLabel, hostNameLabel);

        HBox guestRow = new HBox();
        guestRow.setAlignment(Pos.CENTER_LEFT);
        Label guestLabel = new Label("대기 중인 플레이어");
        guestLabel.getStyleClass().add("player-role-label");
        guestStatus = new Label("👥 1 / 2");
        guestStatus.getStyleClass().add("player-count-label");
        HBox.setHgrow(guestStatus, javafx.scene.layout.Priority.ALWAYS);
        guestStatus.setMaxWidth(Double.MAX_VALUE);
        guestStatus.setAlignment(Pos.CENTER_RIGHT);
        guestRow.getChildren().addAll(guestLabel, guestStatus);

        playerListBox.getChildren().addAll(hostRow, guestRow);

        // 4. 로딩 표시기
        VBox loadingBox = new VBox(15);
        loadingBox.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("custom-spinner");
        Label waitingLabel = new Label("상대방이 입장하기를 기다리는 중...");
        waitingLabel.getStyleClass().add("waiting-text");
        Label shareLabel = new Label("친구에게 방 코드를 공유하세요");
        shareLabel.getStyleClass().add("share-text");
        loadingBox.getChildren().addAll(spinner, waitingLabel, shareLabel);

        // 5. 취소 버튼
        Button cancelBtn = new Button("취소");
        cancelBtn.getStyleClass().add("cancel-btn");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> onCancel.run());

        // 컨텐츠 박스에 모두 추가
        contentBox.getChildren().addAll(titleBox, codeBox, playerListBox, loadingBox, cancelBtn);

        root.setCenter(contentBox);
        return root;
    }

    private Label guestStatus;

    public void updatePlayerCount(int count) {
        javafx.application.Platform.runLater(() -> {
            if (guestStatus != null) {
                guestStatus.setText("👥 " + count + " / 2");
            }
        });
    }
}
