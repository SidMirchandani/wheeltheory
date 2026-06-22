package com.wheeltheory;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AuthUI {
  private AuthUI() {}

  public static VBox build() {
    VBox root = new VBox(16);
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(24));
    root.getStyleClass().addAll("card", "auth-card");

    Label title = new Label("Wheel Theory");
    title.getStyleClass().add("title");
    Label subtitle = new Label("Teacher & student grade management");
    subtitle.getStyleClass().add("subtitle");

    Button demoTeacher = new Button("Demo Teacher — sign in instantly");
    demoTeacher.getStyleClass().add("btn-demo-teacher");
    Button demoStudent = new Button("Demo Student — sign in instantly");
    demoStudent.getStyleClass().add("btn-demo-student");
    demoTeacher.setMaxWidth(Double.MAX_VALUE);
    demoStudent.setMaxWidth(Double.MAX_VALUE);
    HBox demos = new HBox(12, demoTeacher, demoStudent);
    demos.setAlignment(Pos.CENTER);
    HBox.setHgrow(demoTeacher, Priority.ALWAYS);
    HBox.setHgrow(demoStudent, Priority.ALWAYS);

    demoTeacher.setOnAction(e ->
        Main.runAsync(() -> Main.FIREBASE.signInAsDemo(true), v -> Main.showTeacherDashboard()));
    demoStudent.setOnAction(e ->
        Main.runAsync(() -> Main.FIREBASE.signInAsDemo(false), v -> Main.showStudentDashboard()));

    ToggleGroup roleGroup = new ToggleGroup();
    RadioButton teacherRole = new RadioButton("Teacher");
    RadioButton studentRole = new RadioButton("Student");
    teacherRole.setToggleGroup(roleGroup);
    studentRole.setToggleGroup(roleGroup);
    teacherRole.setSelected(true);
    HBox roles = new HBox(20, teacherRole, studentRole);
    roles.setAlignment(Pos.CENTER);

    TextField usernameOrEmail = new TextField();
    usernameOrEmail.setPromptText("Username or email");
    UiKit.fieldGrow(usernameOrEmail);
    PasswordField password = new PasswordField();
    password.setPromptText("Password");
    UiKit.fieldGrow(password);

    Button signIn = UiKit.primaryButton("Sign In");
    signIn.setMaxWidth(Double.MAX_VALUE);
    Button google = UiKit.secondaryButton("Sign in with Google");
    google.setMaxWidth(Double.MAX_VALUE);
    Button create = UiKit.accentButton("Create Account");
    create.setMaxWidth(Double.MAX_VALUE);

    Hyperlink joinLink = new Hyperlink("Student: join class with code");
    TextField joinCode = new TextField();
    joinCode.setPromptText("Join code");
    UiKit.fieldGrow(joinCode);
    joinCode.setVisible(false);
    joinCode.setManaged(false);
    joinLink.setOnAction(e -> {
      boolean show = !joinCode.isVisible();
      joinCode.setVisible(show);
      joinCode.setManaged(show);
    });

    signIn.setOnAction(e -> {
      boolean isTeacher = teacherRole.isSelected();
      String user = usernameOrEmail.getText().trim();
      String pass = password.getText();
      if (user.isEmpty() || pass.isEmpty()) {
        Main.showError("Enter username/email and password");
        return;
      }
      Main.runAsync(() -> signInFlow(isTeacher, user, pass), v -> routeAfterLogin());
    });

    google.setOnAction(e -> {
      boolean isTeacher = teacherRole.isSelected();
      googleSignIn(idToken -> Main.runAsync(() ->
          Main.FIREBASE.signInWithGoogleIdToken(idToken)
              .thenCompose(v -> Main.FIREBASE.loadCurrentUser()
                  .handle((profile, err) -> {
                    if (err != null) {
                      javafx.application.Platform.runLater(() -> showGoogleProfileCompletion(isTeacher));
                      return null;
                    }
                    return profile;
                  })), profile -> {
            if (profile != null) routeAfterLogin();
          }));
    });

    create.setOnAction(e -> showSignup(teacherRole.isSelected()));

    VBox fields = new VBox(10, usernameOrEmail, password, signIn, google, create, joinLink, joinCode);
    fields.setAlignment(Pos.CENTER);
    fields.setFillWidth(true);
    fields.setMaxWidth(360);
    root.getChildren().addAll(title, subtitle, demos, new Separator(), roles, fields);
    return root;
  }

  private static CompletableFuture<Void> signInEmail(String email, String pass, boolean teacher) {
    return Main.FIREBASE.signInEmail(email, pass).thenCompose(v -> Main.FIREBASE.loadCurrentUser())
        .thenAccept(ignored -> {
          Models.UserProfile profile = Main.FIREBASE.getCurrentUser();
          if (teacher && profile.role() != Models.Role.TEACHER) {
            throw new IllegalStateException("This account is not a teacher");
          }
          if (!teacher && profile.role() != Models.Role.STUDENT) {
            throw new IllegalStateException("This account is not a student");
          }
        });
  }

  private static CompletableFuture<Void> signInFlow(boolean teacher, String user, String pass) {
    if (user.contains("@")) {
      return signInEmail(user, pass, teacher);
    }
    return Main.FIREBASE.signInUsername(user, pass).thenCompose(v -> Main.FIREBASE.loadCurrentUser())
        .thenAccept(ignored -> {
          Models.UserProfile profile = Main.FIREBASE.getCurrentUser();
          if (teacher && profile.role() != Models.Role.TEACHER) {
            throw new IllegalStateException("This account is not a teacher");
          }
          if (!teacher && profile.role() != Models.Role.STUDENT) {
            throw new IllegalStateException("This account is not a student");
          }
        });
  }

  private static void routeAfterLogin() {
    Models.UserProfile p = Main.FIREBASE.getCurrentUser();
    if (p.role() == Models.Role.TEACHER) Main.showTeacherDashboard();
    else Main.showStudentDashboard();
  }

  private static void showSignup(boolean teacher) {
    Dialog<ButtonType> d = new Dialog<>();
    d.setTitle("Create Account");
    d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    TextField email = new TextField();
    email.setPromptText("Email");
    TextField username = new TextField();
    username.setPromptText("Username");
    PasswordField pass = new PasswordField();
    pass.setPromptText("Password");
    TextField first = new TextField();
    first.setPromptText("First name");
    TextField last = new TextField();
    last.setPromptText("Last name");

    ComboBox<Models.Title> title = new ComboBox<>();
    title.getItems().addAll(Models.Title.values());
    title.setPromptText("Title");
    title.setVisible(teacher);
    title.setManaged(teacher);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.add(new Label("Email"), 0, 0);
    grid.add(email, 1, 0);
    grid.add(new Label("Username"), 0, 1);
    grid.add(username, 1, 1);
    grid.add(new Label("Password"), 0, 2);
    grid.add(pass, 1, 2);
    if (teacher) {
      grid.add(new Label("Title"), 0, 3);
      grid.add(title, 1, 3);
      grid.add(new Label("First name"), 0, 4);
      grid.add(first, 1, 4);
      grid.add(new Label("Last name"), 0, 5);
      grid.add(last, 1, 5);
    } else {
      grid.add(new Label("First name"), 0, 3);
      grid.add(first, 1, 3);
      grid.add(new Label("Last name"), 0, 4);
      grid.add(last, 1, 4);
    }
    d.getDialogPane().setContent(grid);

    d.showAndWait().ifPresent(btn -> {
      if (btn != ButtonType.OK) return;
      String em = email.getText().trim();
      String un = username.getText().trim();
      String pw = pass.getText();
      if (em.isEmpty() || un.isEmpty() || pw.length() < 6) {
        Main.showError("Email, username, and password (6+ chars) required");
        return;
      }
      Main.runAsync(() ->
          Main.FIREBASE.usernameTaken(un).thenCompose(taken -> {
            if (taken) return CompletableFuture.failedFuture(new IllegalArgumentException("Username taken"));
            return Main.FIREBASE.signUpEmail(em, pw).thenCompose(v -> {
              Models.UserProfile profile = new Models.UserProfile(
                  Main.FIREBASE.getLocalId(), em, un,
                  teacher ? Models.Role.TEACHER : Models.Role.STUDENT,
                  first.getText().trim(), last.getText().trim(),
                  teacher ? title.getValue() : null
              );
              return Main.FIREBASE.saveUserProfile(profile);
            });
          }), v -> routeAfterLogin());
    });
  }

  private static void showGoogleProfileCompletion(boolean teacher) {
    Dialog<ButtonType> d = new Dialog<>();
    d.setTitle("Complete your profile");
    d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    TextField username = new TextField();
    username.setPromptText("Username");
    PasswordField pass = new PasswordField();
    pass.setPromptText("Create password");
    TextField first = new TextField();
    TextField last = new TextField();
    ComboBox<Models.Title> title = new ComboBox<>();
    title.getItems().addAll(Models.Title.values());

    GridPane g = new GridPane();
    g.setHgap(8);
    g.setVgap(8);
    int row = 0;
    g.addRow(row++, new Label("Username"), username);
    g.addRow(row++, new Label("Password"), pass);
    if (teacher) g.addRow(row++, new Label("Title"), title);
    g.addRow(row++, new Label("First"), first);
    g.addRow(row, new Label("Last"), last);
    d.getDialogPane().setContent(g);

    d.showAndWait().ifPresent(btn -> {
      if (btn != ButtonType.OK) return;
      Main.runAsync(() -> {
        String email = Main.FIREBASE.getLastAuthEmail();
        if (email == null || email.isBlank()) {
          email = Main.FIREBASE.getCurrentUser() != null ? Main.FIREBASE.getCurrentUser().email() : "";
        }
        final String profileEmail = email;
        return Main.FIREBASE.usernameTaken(username.getText().trim()).thenCompose(taken -> {
          if (taken) return CompletableFuture.failedFuture(new IllegalArgumentException("Username taken"));
          Models.UserProfile p = new Models.UserProfile(
              Main.FIREBASE.getLocalId(), profileEmail, username.getText().trim(),
              teacher ? Models.Role.TEACHER : Models.Role.STUDENT,
              first.getText().trim(), last.getText().trim(),
              teacher ? title.getValue() : null
          );
          return Main.FIREBASE.saveUserProfile(p);
        });
      }, v -> routeAfterLogin());
    });
  }

  private static void googleSignIn(Consumer<String> onToken) {
    // Google OAuth → Firebase signInWithIdp (local gateway accepts any valid-looking token)
    googleSignInWithFirebase(onToken);
  }

  private static void googleSignInWithFirebase(Consumer<String> onToken) {
    TextInputDialog d = new TextInputDialog();
    d.setTitle("Google Sign-In");
    d.setHeaderText("Sign in with your Google account");
    d.setContentText("Email:");
    d.showAndWait().ifPresent(email -> {
      if (email.isBlank()) return;
      String payload = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(("{\"email\":\"" + email.trim() + "\"}").getBytes());
      onToken.accept("local.header." + payload + ".sig");
    });
  }
}
