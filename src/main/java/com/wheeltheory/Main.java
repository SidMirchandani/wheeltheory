package com.wheeltheory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.net.URL;

public class Main extends Application {
  public static final FirebaseClient FIREBASE = new FirebaseClient();
  private static StackPane root;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    loadPoppins();
    LocalFirebaseBackend.start();
    stage.setTitle("Wheel Theory");

    root = new StackPane();
    root.setPadding(new Insets(20));
    root.getStyleClass().add("app-root");

    Scene scene = new Scene(root, 1140, 760);
    scene.getStylesheets().add("data:text/css," + css());
    stage.setScene(scene);
    stage.show();

    showAuth();
  }

  private static void loadPoppins() {
    try {
      Font.loadFont(new URL("https://fonts.gstatic.com/s/poppins/v22/pxiEyp8kv8JHgFVrJJfecg.woff2").openStream(), 12);
      Font.loadFont(new URL("https://fonts.gstatic.com/s/poppins/v22/pxiByp8kv8JHgFVrLCz7Z1M.woff2").openStream(), 12);
      Font.loadFont(new URL("https://fonts.gstatic.com/s/poppins/v22/pxiByp8kv8JHgFVrLEj6Z1M.woff2").openStream(), 12);
      Font.loadFont(new URL("https://fonts.gstatic.com/s/poppins/v22/pxiByp8kv8JHgFVrLGT9Z1M.woff2").openStream(), 12);
    } catch (Exception ignored) {}
  }

  public static void showAuth() { setCenter(AuthUI.build()); }
  public static void showTeacherDashboard() { setCenter(TeacherUI.dashboard()); }
  public static void showStudentDashboard() { setCenter(StudentUI.dashboard()); }
  public static void showTeacherClass(String classId, String className) { setCenter(TeacherUI.classDetail(classId, className)); }
  public static void showTeacherStudent(String classId, String className, Models.StudentInClass student) { setCenter(TeacherUI.studentDetail(classId, className, student)); }
  public static void showAssignments(String classId, String className) { setCenter(TeacherUI.assignments(classId, className)); }
  public static void showAssignmentDetail(String classId, String className, Models.Assignment assignment) { setCenter(TeacherUI.assignmentDetail(classId, className, assignment)); }
  public static void showSpinGrades(String classId, String className, Models.Assignment assignment) { setCenter(TeacherUI.spinGrades(classId, className, assignment)); }
  public static void showStudentClass(String classId, String className) { setCenter(StudentUI.classGrades(classId, className)); }

  public static void setCenter(javafx.scene.Node content) {
    Platform.runLater(() -> {
      root.getChildren().clear();
      StackPane wrapper = new StackPane(content);
      wrapper.setMaxWidth(960);
      StackPane.setAlignment(wrapper, Pos.TOP_CENTER);
      root.getChildren().add(wrapper);
    });
  }

  public static <T> void runAsync(java.util.function.Supplier<java.util.concurrent.CompletableFuture<T>> task,
                                  java.util.function.Consumer<T> onSuccess) {
    task.get().whenComplete((result, err) -> Platform.runLater(() -> {
      if (err != null) {
        showError(err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
      } else if (onSuccess != null) {
        onSuccess.accept(result);
      }
    }));
  }

  public static void showError(String message) {
    styleAlert(new Alert(Alert.AlertType.ERROR), message);
  }

  public static void showInfo(String message) {
    styleAlert(new Alert(Alert.AlertType.INFORMATION), message);
  }

  private static void styleAlert(Alert a, String message) {
    a.setTitle("Wheel Theory");
    a.setHeaderText(null);
    a.setContentText(message != null ? message : "");
    a.showAndWait();
  }

  static String css() {
    return String.join("",
        ".root,.app-root{-fx-font-family:'Poppins',sans-serif;-fx-background-color:#0f0f14;}",
        ".page{-fx-background-color:transparent;}",
        ".label{-fx-text-fill:#e8eaed;}",
        ".title{-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#f8fafc;}",
        ".subtitle{-fx-text-fill:#9aa0a6;-fx-font-size:14px;}",
        ".meta{-fx-text-fill:#bdc1c6;-fx-font-size:14px;}",
        ".card{-fx-background-color:#1a1a22;-fx-background-radius:16;-fx-padding:28;-fx-border-color:#2d2d3a;-fx-border-radius:16;-fx-border-width:1;}",
        ".auth-card{-fx-max-width:520;-fx-alignment:center;}",
        ".button{-fx-cursor:hand;-fx-background-insets:0;-fx-background-radius:10;-fx-border-width:0;-fx-padding:10 20;-fx-font-size:13px;}",
        ".button:disabled{-fx-opacity:0.45;}",
        ".btn-primary{-fx-background-color:#8b5cf6;-fx-text-fill:white;-fx-font-weight:bold;}",
        ".btn-primary:hover{-fx-background-color:#7c3aed;}",
        ".btn-primary:pressed{-fx-background-color:#6d28d9;}",
        ".btn-secondary{-fx-background-color:#252530;-fx-text-fill:#e8eaed;-fx-border-color:#3d3d4a;-fx-border-width:1;-fx-border-radius:10;}",
        ".btn-secondary:hover{-fx-background-color:#2d2d3a;-fx-border-color:#52525b;}",
        ".btn-secondary:pressed{-fx-background-color:#1f1f28;}",
        ".btn-accent{-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;}",
        ".btn-accent:hover{-fx-background-color:#1d4ed8;}",
        ".btn-accent:pressed{-fx-background-color:#1e40af;}",
        ".btn-danger{-fx-background-color:#450a0a;-fx-text-fill:#fecaca;-fx-border-color:#7f1d1d;-fx-border-width:1;-fx-border-radius:8;}",
        ".btn-danger:hover{-fx-background-color:#7f1d1d;}",
        ".btn-ghost{-fx-background-color:transparent;-fx-text-fill:#9aa0a6;-fx-padding:8 12;}",
        ".btn-ghost:hover{-fx-text-fill:#e8eaed;-fx-background-color:#1f1f2a;}",
        ".btn-back{-fx-padding:8 16;}",
        ".btn-demo-teacher{-fx-background-color:#1e3a5f;-fx-text-fill:#93c5fd;-fx-font-weight:bold;}",
        ".btn-demo-teacher:hover{-fx-background-color:#1e4976;}",
        ".btn-demo-student{-fx-background-color:#1a3d2e;-fx-text-fill:#86efac;-fx-font-weight:bold;}",
        ".btn-demo-student:hover{-fx-background-color:#22543d;}",
        ".btn-sm{-fx-padding:7 14;-fx-font-size:12px;-fx-background-radius:8;}",
        ".btn-wheel{-fx-min-width:160;}",
        ".prominent{-fx-font-size:14px;-fx-padding:12 24;}",
        ".action-row{-fx-padding:4 0 0 0;}",
        ".field{-fx-pref-height:42;-fx-background-color:#12121a;-fx-text-fill:#e8eaed;-fx-prompt-text-fill:#6b7280;-fx-background-radius:10;-fx-border-color:#2d2d3a;-fx-border-radius:10;-fx-border-width:1;-fx-padding:10 14;}",
        ".text-field,.password-field,.combo-box-base{-fx-background-color:#12121a;-fx-text-fill:#e8eaed;-fx-prompt-text-fill:#6b7280;}",
        ".text-field:focused,.password-field:focused{-fx-border-color:#8b5cf6;-fx-border-width:1;}",
        ".join-code{-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#fbbf24;}",
        ".wheel-frame{-fx-padding:8;}",
        ".wheel-subtitle{-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#c4b5fd;}",
        ".wheel-result{-fx-font-size:48px;-fx-font-weight:bold;-fx-text-fill:#6b7280;}",
        ".wheel-landed{-fx-text-fill:#a78bfa;-fx-effect:dropshadow(gaussian,#7c3aed,18,0.6,0,0);}",
        ".wheel-busted{-fx-text-fill:#f87171;}",
        ".wheel-progress{-fx-text-fill:#9aa0a6;-fx-font-size:14px;}",
        ".btn-wheel{-fx-min-width:160;-fx-font-size:15px;-fx-padding:13 28;}",
        ".separator .line{-fx-border-color:#2d2d3a;}",
        ".radio-button{-fx-text-fill:#e8eaed;}",
        ".hyperlink{-fx-text-fill:#a78bfa;}",
        ".hyperlink:visited{-fx-text-fill:#a78bfa;}",
        ".list-panel{-fx-background-color:#12121a;-fx-background-radius:12;-fx-border-color:#2d2d3a;-fx-border-radius:12;-fx-border-width:1;}",
        ".list-view{-fx-background-color:transparent;-fx-background-insets:0;-fx-padding:4;}",
        ".list-cell{-fx-background-color:transparent;-fx-text-fill:#e8eaed;-fx-padding:14 16;-fx-font-size:14px;}",
        ".list-cell:filled:selected{-fx-background-color:#2d2640;-fx-background-radius:8;}",
        ".list-cell:filled:hover{-fx-background-color:#1f1f2a;-fx-background-radius:8;}",
        ".data-table{-fx-background-color:transparent;-fx-border-color:#2d2d3a;-fx-border-radius:12;-fx-background-radius:12;}",
        ".data-table .column-header-background{-fx-background-color:#12121a;-fx-background-radius:12 12 0 0;}",
        ".data-table .column-header,.data-table .filler{-fx-background-color:transparent;-fx-border-color:transparent;}",
        ".data-table .column-header .label{-fx-text-fill:#9aa0a6;-fx-font-weight:bold;-fx-font-size:12px;-fx-padding:12 14;}",
        ".data-table .table-row-cell{-fx-background-color:transparent;-fx-border-color:transparent;}",
        ".data-table .table-row-cell:odd{-fx-background-color:#14141c;}",
        ".data-table .table-row-cell:even{-fx-background-color:#1a1a22;}",
        ".data-table .table-row-cell:filled:selected{-fx-background-color:#2d2640;}",
        ".data-table .table-cell{-fx-text-fill:#e8eaed;-fx-border-color:#252530;-fx-padding:12 14;-fx-alignment:center-left;}",
        ".data-table .placeholder .label{-fx-text-fill:#6b7280;}",
        ".scroll-bar:vertical,.scroll-bar:horizontal{-fx-background-color:transparent;}",
        ".scroll-bar .thumb{-fx-background-color:#3d3d4a;-fx-background-radius:8;}",
        ".dialog-pane{-fx-background-color:#1a1a22;}",
        ".dialog-pane .label{-fx-text-fill:#e8eaed;}"
    );
  }
}
