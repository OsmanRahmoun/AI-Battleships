import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.Random;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Duration;
import javafx.scene.control.Label;



public class GuiClient extends Application {
    private Client clientConnection;
    private Scene mainScene;
    private StackPane rootLayout; // Used for layering the background and other components
    private Stage primaryStage;

    private boolean running = false;
    private Board enemyBoard, playerBoard;

    private int shipsToPlace = 5;

    private boolean enemyTurn = false;

    private Random random = new Random();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        clientConnection = new Client(data -> {
            Platform.runLater(() -> {
                if (data.toString().startsWith("MOVE")) {
                    String[] parts = data.toString().split(" ");
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    handleEnemyMove(x,y);

                } else if(data.toString().startsWith("game_over")) {
                    // Handle other kinds of messages
                    showAlert("Game Over", "Game Over!", data.toString().substring(10));
                } else if (data.toString().equals("Good shot")) {
                    clientConnection.goodHit = 1;
                }
                else{
                    //handle other
                }
            });
        });

        clientConnection.start();
        initializeViews();

        primaryStage.setScene(mainScene);
        primaryStage.setTitle("Battleship Game Client");
        primaryStage.show();
    }

    private void initializeViews() {
        Button btnTwoPlayer = new Button("Play 2-Player Game");
        Button btnPlayAI = new Button("Play Against AI");

        btnTwoPlayer.setPrefHeight(50);
        btnTwoPlayer.setPrefWidth(200);
        btnPlayAI.setPrefHeight(50);
        btnPlayAI.setPrefWidth(200);

        setupButtonEffects(btnTwoPlayer, btnPlayAI);

        // Load images
        ImageView backgroundView = new ImageView(new Image("file:src/main/resources/background.png"));
        backgroundView.setFitWidth(primaryStage.getWidth());
        backgroundView.setFitHeight(primaryStage.getHeight());
        backgroundView.setPreserveRatio(false);

        ImageView titleView = new ImageView(new Image("file:src/main/resources/title.png"));
        titleView.setPreserveRatio(true);
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            titleView.setFitHeight(newVal.doubleValue() * 0.45);
        });

        VBox contentLayout = new VBox(20, titleView, btnTwoPlayer, btnPlayAI);
        contentLayout.setAlignment(Pos.CENTER);

        rootLayout = new StackPane();
        rootLayout.getChildren().addAll(backgroundView, contentLayout);

        mainScene = new Scene(rootLayout, 300, 400); // Adjusted window height to show changes clearly
        primaryStage.setMinWidth(300);
        primaryStage.setMinHeight(400);

        // Set actions for buttons
        btnTwoPlayer.setOnAction(e -> switchToTwoPlayerGame());
        btnPlayAI.setOnAction(e -> switchToAIGame());
    }

    private void requestTwoPlayerGame() {
        clientConnection.send("request_two_player_game");  // Sending a request message to the server
    }

    private void setupButtonEffects(Button btnTwoPlayer, Button btnPlayAI) {
        String defaultButtonStyle = "-fx-background-color: #2e5066; -fx-font-family: 'SansSerif'; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;-fx-border-width: 2;-fx-border-style: solid;";
        String hoverButtonStyle = "-fx-background-color: #3e7089; -fx-font-family: 'SansSerif'; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;-fx-border-style: solid;";

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setColor(Color.GOLDENROD);

        setupButtonEffects(btnTwoPlayer, defaultButtonStyle, hoverButtonStyle, shadow);
        setupButtonEffects(btnPlayAI, defaultButtonStyle, hoverButtonStyle, shadow);
    }

    private void setupButtonEffects(Button button, String defaultStyle, String hoverStyle, DropShadow shadow) {
        button.setStyle(defaultStyle);
        button.setOnMouseEntered(e -> {
            button.setStyle(hoverStyle);
            button.setEffect(shadow);
        });
        button.setOnMouseExited(e -> {
            button.setStyle(defaultStyle);
            button.setEffect(shadow);
        });
    }

    private void switchToTwoPlayerGame() {
        requestTwoPlayerGame();
        BorderPane root = new BorderPane();
        root.setPrefSize(600, 800);

        enemyBoard = new Board(true, event -> {
            if (running && !enemyTurn) {
                Board.Cell cell = (Board.Cell) event.getSource();
                if (!cell.wasShot) {
                    clientConnection.sendMove(cell.x, cell.y); // Send the coordinates of the move to the server
                    final Timeline[] timelineHolder = new Timeline[1];
                    timelineHolder[0] = new Timeline(new KeyFrame(Duration.seconds(.1), ev -> {
                        if (clientConnection.goodHit == 1) {
                            cell.playerShootHit();
                            clientConnection.goodHit = 0;
                            // COMMENT LINE BELOW IF WE DONT WANT CONSECUTIVE HITS
                            enemyTurn = false;
                        } else {
                            cell.shoot();
                            // COMMENT LINE BELOW IF WE DONT WANT CONSECUTIVE HITS
                            enemyTurn = true;
                        }
                        timelineHolder[0].stop();
                    }));
                    // UNCOMMENT THIS LINE BELOW IF WE DONT WANT CONSECUTIVE HITS
                    // enemyTurn = false;
                    timelineHolder[0].setCycleCount(Timeline.INDEFINITE);
                    timelineHolder[0].play();
                    //enemyTurn = true; // It becomes the enemy's turn
                }
            }
        });



        // Initialize the player board for ship placement
        playerBoard = new Board(false, event -> {
            // This board is interactive during game setup to place ships
            if (shipsToPlace > 0) {
                Board.Cell cell = (Board.Cell) event.getSource();
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (playerBoard.placeShip(new Ship(shipsToPlace, false), cell.x, cell.y)) {
                        shipsToPlace--;
                        if (shipsToPlace == 0) {
                            // All ships placed, set running to true to start the game
                            running = true;
                            setupGame();
                        }
                    }
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    // Optionally handle ship rotation on right click
                    if (playerBoard.placeShip(new Ship(shipsToPlace, true), cell.x, cell.y)) {
                        shipsToPlace--;
                        if (shipsToPlace == 0) {
                            running = true;
                            setupGame();
                        }
                    }
                }
            }
        });

        VBox vbox = new VBox(20, new Text("Enemy's Board"), enemyBoard, new Text("Your Board"), playerBoard);
        vbox.setAlignment(Pos.CENTER);
        root.setCenter(vbox);

        Scene scene = new Scene(root, 600, 800);
        primaryStage.setTitle("Two Player Game");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Prompt the player to place their ships
        if (shipsToPlace > 0) {
            showShipPlacementInstructions();
        }
    }

    private void showShipPlacementInstructions() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Place Your Ships");
        alert.setHeaderText("You need to place " + shipsToPlace + " ships.");
        alert.setContentText("Click to place a ship. Right-click to rotate a ship before placing.");
        alert.showAndWait();
    }

    private void setupGame() {
        // Start the game after ships have been placed
        clientConnection.send("player_ready");
        enemyTurn = false;  // Adjust based on who should start the game
    }



    private void handleEnemyMove(int x, int y) {
        Platform.runLater(() -> {
            Board.Cell cell = playerBoard.getCell(x, y);
            if (!cell.wasShot) {
                if (cell.shoot()) {
                    clientConnection.send("Good enemy shot");
                }
                enemyTurn = false; // After processing the enemy's move, it should be your turn
                checkGameStatus();
            }
        });
    }




    private void checkGameStatus() {
        Platform.runLater(() -> {
            if (enemyBoard.ships == 0) {
                showAlert("Game Over", "Congratulations!", "YOU WON!! All the enemy's ships have been destroyed!");
                running = false;
                clientConnection.send("game_over You won!");
            } else if (playerBoard.ships == 0) {
                showAlert("Game Over", "Game Over!", "YOU LOSE!! All your ships have been destroyed!");
                running = false;
                clientConnection.send("game_over You Won!");
            }
        });
    }







    private void switchToAIGame() {
        // Create a scene for the AI game
        Parent content = createContent();
        Scene aiScene = new Scene(content, 600, 800); // Ensure dimensions match the expected content size
        primaryStage.setTitle("AI GamePlay");
        primaryStage.setScene(aiScene);

    }


    //Code from youtune VIDEO for the AI GAMEPLAY
    private Parent createContent() {
        BorderPane root = new BorderPane();
        root.setPrefSize(600, 800);

        // Create the Text object with game instructions
        Text controlsText = new Text("RIGHT SIDEBAR - CONTROLS\n\n" +
                "Instructions:\n" +
                "1. Set your 5 ships first. Placement options:\n" +
                "   - For Mac: Single tap for vertical placement, two-finger tap for horizontal placement.\n" +
                "   - For Windows: Left-click for vertical, right-click for horizontal ship placements.\n" +
                "2. Tap anywhere on the enemy board to attack.\n" +
                "3. Outcome of your attack:\n" +
                "   - Red: Hit a ship. Continue hitting until you miss.\n" +
                "   - Black: Missed the ship. You get only one hit per turn.");
        controlsText.setStyle("-fx-font-size: 16px; " +
                "-fx-font-family: 'Arial'; " +
                "-fx-fill: #333333; " +
                "-fx-font-weight: bold;");

        // Create a VBox for better control styling and placement
        VBox textContainer = new VBox();
        textContainer.setAlignment(Pos.CENTER);  // Center the text in the VBox
        textContainer.setStyle("-fx-border-color: black; " +  // Border color
                "-fx-border-width: 2; " +  // Border width
                "-fx-border-style: solid; " +  // Border style
                "-fx-padding: 20; " +  // Padding inside the border
                "-fx-background-color: #ffffff;");  // Background color
        textContainer.getChildren().add(controlsText);  // Add the text to the VBox

        // Set the styled VBox to the right side of your root layout
        root.setRight(textContainer);

        Label aiLabel = new Label("AI Board");
        aiLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        aiLabel.setAlignment(Pos.CENTER);

        Label playerLabel = new Label("Your Board");
        playerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        playerLabel.setAlignment(Pos.CENTER);


        enemyBoard = new Board(true, event -> {
            if (!running)
                return;

            Board.Cell cell = (Board.Cell) event.getSource();
            if (cell.wasShot)
                return;


            enemyTurn = !cell.shoot();

            if (enemyBoard.ships == 0) {
                showAlert("Game Over", "YOU WON!!", "All the AI's ships have been destroyed!");
            }

            if (enemyTurn)
                enemyMove();
        });

        playerBoard = new Board(false, event -> {
            if (running)
                return;

            Board.Cell cell = (Board.Cell) event.getSource();
            if (playerBoard.placeShip(new Ship(shipsToPlace, event.getButton() == MouseButton.PRIMARY), cell.x, cell.y)) {
                if (--shipsToPlace == 0) {
                    startGame();
                }
            }
        });

        VBox vbox = new VBox(20);  // Adjust spacing as needed
        vbox.getChildren().addAll(aiLabel, enemyBoard, playerLabel, playerBoard);
        vbox.setAlignment(Pos.CENTER);

        root.setCenter(vbox);

        return root;
    }

    private void enemyMove() {
        while (enemyTurn) {
            int x = random.nextInt(10);
            int y = random.nextInt(10);

            Board.Cell cell = playerBoard.getCell(x, y);
            if (cell.wasShot)
                continue;

            enemyTurn = cell.shoot();

            if (playerBoard.ships == 0) {
                showAlert("Game Over", "YOU LOSE!!", "All your ships have been destroyed!");
            }
        }


    }

    private void showAlert(String title, String headerText, String contentText) {
        // Run later is used to ensure that the alert is shown on the JavaFX thread
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(headerText);
            alert.setContentText(contentText);
            alert.showAndWait();
            Platform.exit();
            System.exit(5);  // Optionally close the application after acknowledging the alert
        });
    }






    private void startGame() {
        // place enemy ships
        int type = 5;

        while (type > 0) {
            int x = random.nextInt(10);
            int y = random.nextInt(10);

            if (enemyBoard.placeShip(new Ship(type, Math.random() < 0.5), x, y)) {
                type--;
            }
        }

        running = true;
    }

}