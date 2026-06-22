package com.wheeltheory;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class TeacherUI {
  private TeacherUI() {}

  public static BorderPane dashboard() {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));

    Models.UserProfile user = Main.FIREBASE.getCurrentUser();
    Label heading = new Label("Welcome, " + (user != null ? user.displayName() : "Teacher"));
    heading.getStyleClass().add("title");

    Button createClass = new Button("+ Create Class");
    createClass.getStyleClass().addAll("btn-primary", "prominent");

    ListView<Models.ClassInfo> classList = new ListView<>();
    classList.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(Models.ClassInfo item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name() + "  (code: " + item.joinCode() + ")");
      }
    });
    classList.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        Models.ClassInfo c = classList.getSelectionModel().getSelectedItem();
        if (c != null) Main.showTeacherClass(c.id(), c.name());
      }
    });

    Button open = new Button("Open Class");
    open.getStyleClass().add("btn-secondary");
    open.setOnAction(e -> {
      Models.ClassInfo c = classList.getSelectionModel().getSelectedItem();
      if (c != null) Main.showTeacherClass(c.id(), c.name());
    });

    Button signOut = UiKit.backButton("Sign Out", Main::showAuth);
    signOut.getStyleClass().remove("btn-ghost");
    signOut.getStyleClass().add("btn-secondary");

    createClass.setOnAction(e -> {
      TextInputDialog d = new TextInputDialog();
      d.setTitle("New Class");
      d.setHeaderText("Class name");
      d.showAndWait().ifPresent(name -> {
        if (name.isBlank()) return;
        Main.runAsync(() -> Main.FIREBASE.createClass(name.trim()).thenApply(c -> null),
            v -> refreshClasses(classList, createClass));
      });
    });

    VBox center = new VBox(12, heading, classList, new HBox(10, open, createClass));
    center.setAlignment(Pos.TOP_LEFT);
    classList.setPrefHeight(400);
    pane.setCenter(wrapCard(center));
    pane.setTop(new HBox(signOut));

    refreshClasses(classList, createClass);
    return pane;
  }

  private static void refreshClasses(ListView<Models.ClassInfo> list, Button createBtn) {
    Main.runAsync(() -> Main.FIREBASE.listTeacherClasses(), classes -> {
      list.setItems(FXCollections.observableArrayList(classes));
      createBtn.getStyleClass().remove("prominent");
      if (classes.isEmpty()) createBtn.getStyleClass().add("prominent");
    });
  }

  public static BorderPane classDetail(String classId, String className) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));

    Label title = new Label(className);
    title.getStyleClass().add("title");

    Label joinLabel = new Label("Class code");
    joinLabel.getStyleClass().add("meta");
    HBox joinRow = new HBox(12, joinLabel);
    joinRow.setAlignment(Pos.CENTER_LEFT);

    Button back = UiKit.backButton("← Dashboard", Main::showTeacherDashboard);
    Button assignments = new Button("Add / Edit Assignments");
    assignments.getStyleClass().add("btn-primary");
    assignments.setOnAction(e -> Main.showAssignments(classId, className));

    TableView<StudentRow> table = new TableView<>();
    UiKit.styleTable(table);
    TableColumn<StudentRow, String> nameCol = new TableColumn<>("Name");
    nameCol.setCellValueFactory(c -> c.getValue().name);
    TableColumn<StudentRow, String> userCol = new TableColumn<>("Username");
    userCol.setCellValueFactory(c -> c.getValue().username);
    userCol.setPrefWidth(120);
    TableColumn<StudentRow, String> avgCol = new TableColumn<>("Average");
    avgCol.setCellValueFactory(c -> c.getValue().average);
    avgCol.setPrefWidth(110);
    TableColumn<StudentRow, Void> expandCol = new TableColumn<>(" ");
    expandCol.setPrefWidth(100);
    expandCol.setMinWidth(100);
    expandCol.setMaxWidth(100);
    expandCol.setCellFactory(col -> new TableCell<>() {
      private final Button btn = UiKit.smallButton("View", "btn-secondary");
      {
        btn.setOnAction(e -> {
          StudentRow row = getTableView().getItems().get(getIndex());
          Main.showTeacherStudent(classId, className, row.student);
        });
      }
      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });
    table.getColumns().addAll(nameCol, userCol, avgCol, expandCol);
    UiKit.grow(nameCol);

    Main.runAsync(() -> Main.FIREBASE.listTeacherClasses(), list -> {
      for (Models.ClassInfo c : list) {
        if (c.id().equals(classId)) {
          joinRow.getChildren().add(UiKit.copyableCode(c.joinCode()));
        }
      }
    });

    VBox content = new VBox(16, title, joinRow, table, assignments);
    pane.setTop(back);
    pane.setCenter(wrapCard(content));
    refreshStudents(table, classId);
    return pane;
  }

  private static void refreshStudents(TableView<StudentRow> table, String classId) {
    Main.runAsync(() -> Main.FIREBASE.listStudentsInClass(classId), students ->
        UiKit.setItems(table, students.stream().map(StudentRow::new).toList()));
  }

  public static BorderPane studentDetail(String classId, String className, Models.StudentInClass student) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));

    Button back = UiKit.backButton("← Back to class", () -> Main.showTeacherClass(classId, className));

    Label title = new Label(student.firstName() + " " + student.lastName());
    title.getStyleClass().add("title");
    Label meta = new Label("@" + student.username() + " · Average: "
        + String.format("%.1f", student.average()) + " (" + student.letter() + ")");
    meta.getStyleClass().add("meta");

    TableView<GradeRow> table = new TableView<>();
    UiKit.styleTable(table);
    TableColumn<GradeRow, String> aCol = new TableColumn<>("Assignment");
    aCol.setCellValueFactory(c -> c.getValue().assignment);
    TableColumn<GradeRow, String> sCol = new TableColumn<>("Grade");
    sCol.setCellValueFactory(c -> c.getValue().score);
    TableColumn<GradeRow, String> dCol = new TableColumn<>("Date");
    dCol.setCellValueFactory(c -> c.getValue().date);
    TableColumn<GradeRow, Void> editCol = new TableColumn<>(" ");
    editCol.setPrefWidth(90);
    editCol.setMinWidth(90);
    editCol.setMaxWidth(90);
    editCol.setCellFactory(col -> new TableCell<>() {
      private final Button btn = UiKit.smallButton("Edit", "btn-secondary");
      {
        btn.setOnAction(e -> {
          GradeRow row = getTableView().getItems().get(getIndex());
          TextInputDialog d = new TextInputDialog(row.score.get().replace("%", ""));
          d.setHeaderText("New grade for " + row.assignment.get());
          d.showAndWait().ifPresent(val -> {
            try {
              int score = Integer.parseInt(val.trim());
              Main.runAsync(() -> Main.FIREBASE.saveGrade(classId, row.assignmentId, student.uid(), score),
                  v -> loadStudentGrades(table, classId, student.uid()));
            } catch (NumberFormatException ex) {
              Main.showError("Enter a number");
            }
          });
        });
      }
      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);
      }
    });
    table.getColumns().addAll(aCol, sCol, dCol, editCol);
    UiKit.grow(aCol);
    UiKit.minColumn(sCol, 72);
    UiKit.minColumn(dCol, 118);

    pane.setTop(back);
    pane.setCenter(wrapCard(new VBox(10, title, meta, table)));
    loadStudentGrades(table, classId, student.uid());
    return pane;
  }

  private static void loadStudentGrades(TableView<GradeRow> table, String classId, String studentUid) {
    String teacherId = Main.FIREBASE.getLocalId();
    Main.runAsync(() -> Main.FIREBASE.getStudentGradesInClass(teacherId, classId, studentUid), grades ->
        UiKit.setItems(table, grades.stream().map(GradeRow::new).toList()));
  }

  public static BorderPane assignments(String classId, String className) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));
    Button back = UiKit.backButton("← Back to class", () -> Main.showTeacherClass(classId, className));

    TableView<Models.Assignment> table = new TableView<>();
    UiKit.styleTable(table);
    TableColumn<Models.Assignment, String> nameCol = new TableColumn<>("Assignment");
    nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
    TableColumn<Models.Assignment, Void> actions = new TableColumn<>(" ");
    actions.setPrefWidth(180);
    actions.setMinWidth(180);
    actions.setMaxWidth(180);
    actions.setCellFactory(col -> new TableCell<>() {
      private final Button open = UiKit.smallButton("Open", "btn-secondary");
      private final Button remove = UiKit.smallButton("Remove", "btn-danger");
      private final HBox box = new HBox(6, open, remove);
      {
        open.setOnAction(e -> {
          Models.Assignment a = getTableView().getItems().get(getIndex());
          Main.showAssignmentDetail(classId, className, a);
        });
        remove.setOnAction(e -> {
          Models.Assignment a = getTableView().getItems().get(getIndex());
          Main.runAsync(() -> Main.FIREBASE.deleteAssignment(classId, a.id()),
              v -> refreshAssignments(table, classId));
        });
      }
      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : box);
      }
    });
    table.getColumns().addAll(nameCol, actions);
    UiKit.grow(nameCol);

    Button add = new Button("+ Add Assignment");
    add.getStyleClass().add("btn-primary");
    add.setOnAction(e -> {
      TextInputDialog d = new TextInputDialog();
      d.setHeaderText("Assignment name");
      d.showAndWait().ifPresent(name -> {
        if (name.isBlank()) return;
        Main.runAsync(() -> Main.FIREBASE.createAssignment(classId, name.trim()).thenApply(a -> {
          Main.showSpinGrades(classId, className, a);
          return a;
        }), null);
      });
    });

    Label hdr = new Label(className + " — Assignments");
    hdr.getStyleClass().add("subtitle");
    pane.setTop(back);
    pane.setCenter(wrapCard(new VBox(12, hdr, table, add)));
    refreshAssignments(table, classId);
    return pane;
  }

  private static void refreshAssignments(TableView<Models.Assignment> table, String classId) {
    Main.runAsync(() -> Main.FIREBASE.listAssignments(classId),
        list -> UiKit.setItems(table, list));
  }

  public static BorderPane assignmentDetail(String classId, String className, Models.Assignment assignment) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));
    Button back = UiKit.backButton("← Assignments", () -> Main.showAssignments(classId, className));

    Label title = new Label(assignment.name());
    title.getStyleClass().add("title");

    TableView<GradeEditRow> table = new TableView<>();
    UiKit.styleTable(table);
    TableColumn<GradeEditRow, String> studentCol = new TableColumn<>("Student");
    studentCol.setCellValueFactory(c -> c.getValue().name);
    TableColumn<GradeEditRow, String> gradeCol = new TableColumn<>("Grade");
    gradeCol.setCellValueFactory(c -> c.getValue().grade);
    TableColumn<GradeEditRow, Void> editCol = new TableColumn<>("Change");
    editCol.setPrefWidth(200);
    editCol.setMinWidth(200);
    editCol.setMaxWidth(200);
    editCol.setCellFactory(col -> new TableCell<>() {
      private final Button edit = UiKit.smallButton("Edit", "btn-secondary");
      private final Button respin = UiKit.smallButton("Respin", "btn-accent");
      private final HBox box = new HBox(6, edit, respin);
      {
        edit.setOnAction(e -> {
          GradeEditRow row = getTableView().getItems().get(getIndex());
          TextInputDialog d = new TextInputDialog(row.score >= 0 ? String.valueOf(row.score) : "");
          d.showAndWait().ifPresent(val -> {
            try {
              int s = Integer.parseInt(val.trim());
              Main.runAsync(() -> Main.FIREBASE.saveGrade(classId, assignment.id(), row.uid, s),
                  ignored -> loadAssignmentGrades(table, classId, assignment.id()));
            } catch (NumberFormatException ex) {
              Main.showError("Invalid grade");
            }
          });
        });
        respin.setOnAction(e -> {
          GradeEditRow row = getTableView().getItems().get(getIndex());
          int g = Models.WHEEL_GRADES[new Random().nextInt(Models.WHEEL_GRADES.length)];
          Main.runAsync(() -> Main.FIREBASE.saveGrade(classId, assignment.id(), row.uid, g),
              v -> loadAssignmentGrades(table, classId, assignment.id()));
        });
      }
      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : box);
      }
    });
    table.getColumns().addAll(studentCol, gradeCol, editCol);
    UiKit.grow(studentCol);
    UiKit.minColumn(gradeCol, 72);

    pane.setTop(back);
    pane.setCenter(wrapCard(new VBox(10, title, table)));
    loadAssignmentGrades(table, classId, assignment.id());
    return pane;
  }

  private static void loadAssignmentGrades(TableView<GradeEditRow> table, String classId, String assignmentId) {
    Main.runAsync(() -> Main.FIREBASE.getAssignmentGrades(classId, assignmentId), grades ->
        UiKit.setItems(table, grades.stream().map(GradeEditRow::new).toList()));
  }

  public static BorderPane spinGrades(String classId, String className, Models.Assignment assignment) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));
    pane.setTop(UiKit.backButton("← Cancel", () -> Main.showAssignments(classId, className)));

    Label title = new Label("Spin Grades  —  " + assignment.name());
    title.getStyleClass().add("title");

    Label progress = new Label("Loading students…");
    progress.getStyleClass().add("wheel-progress");
    progress.setMinHeight(22);
    progress.setPrefHeight(22);
    progress.setMaxHeight(22);
    progress.setAlignment(Pos.CENTER);
    progress.setMaxWidth(Double.MAX_VALUE);

    StackPane wheelHost = new StackPane();
    wheelHost.setMinSize(460, 460);
    wheelHost.setPrefSize(460, 460);
    wheelHost.setMaxSize(460, 460);
    wheelHost.setAlignment(Pos.CENTER);

    Label resultLabel = new Label(" ");
    resultLabel.getStyleClass().add("wheel-result");
    resultLabel.setMinHeight(56);
    resultLabel.setPrefHeight(56);
    resultLabel.setMaxHeight(56);
    resultLabel.setAlignment(Pos.CENTER);
    resultLabel.setMaxWidth(Double.MAX_VALUE);

    Button spinAll = new Button("⚡ Spin All at Once");
    spinAll.getStyleClass().add("btn-accent");
    Button spinOne = new Button("▶  Spin One by One");
    spinOne.getStyleClass().add("btn-primary");
    spinAll.setDisable(true);
    spinOne.setDisable(true);

    HBox btnRow = new HBox(14, spinOne, spinAll);
    btnRow.setAlignment(Pos.CENTER);

    Button spinBtn = new Button("Spin");
    spinBtn.getStyleClass().addAll("btn-primary", "btn-wheel", "prominent");
    spinBtn.setVisible(false);
    spinBtn.setManaged(false);

    StackPane actionArea = new StackPane(btnRow, spinBtn);
    actionArea.setMinHeight(50);
    actionArea.setPrefHeight(50);
    actionArea.setMaxHeight(50);
    StackPane.setAlignment(btnRow, Pos.CENTER);
    StackPane.setAlignment(spinBtn, Pos.CENTER);

    VBox content = new VBox(12, title, progress, wheelHost, resultLabel, actionArea);
    content.setAlignment(Pos.CENTER);
    pane.setCenter(wrapCard(content));

    // Show idle wheel while students load
    WheelUI.drawIdle(wheelHost);

    final List<Models.StudentInClass> students = new ArrayList<>();
    Main.runAsync(() -> Main.FIREBASE.listStudentsInClass(classId), list -> {
      students.clear();
      students.addAll(list);
      if (list.isEmpty()) {
        progress.setText("No students in class yet.");
      } else {
        progress.setText(list.size() + " student" + (list.size() == 1 ? "" : "s") + " · choose a spin mode");
        spinAll.setDisable(false);
        spinOne.setDisable(false);
      }
    });

    spinAll.setOnAction(e -> {
      if (students.isEmpty()) { Main.showError("No students in class"); return; }
      spinAll.setDisable(true);
      spinOne.setDisable(true);
      progress.setText("Assigning random grades to all " + students.size() + " students…");
      Map<String, Integer> grades = new LinkedHashMap<>();
      List<Integer> random = Models.randomWheelGrades(students.size());
      for (int i = 0; i < students.size(); i++) grades.put(students.get(i).uid(), random.get(i));
      Main.runAsync(() -> Main.FIREBASE.saveGradesBatch(classId, assignment.id(), grades),
          v -> showGradesSummary(classId, className, assignment, grades, students));
    });

    spinOne.setOnAction(e -> {
      if (students.isEmpty()) { Main.showError("No students in class"); return; }
      btnRow.setVisible(false);
      btnRow.setManaged(false);
      spinBtn.setVisible(true);
      spinBtn.setManaged(true);
      progress.setText("Student 1 of " + students.size());
      runSpinSequence(classId, className, assignment, students, wheelHost, progress, resultLabel, spinBtn, 0, new LinkedHashMap<>());
    });

    return pane;
  }

  private static void runSpinSequence(String classId, String className, Models.Assignment assignment,
                                      List<Models.StudentInClass> students, StackPane wheelHost,
                                      Label progress, Label resultLabel, Button spinBtn,
                                      int index, Map<String, Integer> collected) {
    if (index >= students.size()) {
      progress.setText("All done! Saving…");
      Main.runAsync(() -> Main.FIREBASE.saveGradesBatch(classId, assignment.id(), collected),
          v -> showGradesSummary(classId, className, assignment, collected, students));
      return;
    }
    Models.StudentInClass s = students.get(index);
    progress.setText("Student " + (index + 1) + " of " + students.size()
        + "  —  " + s.firstName() + " " + s.lastName());
    WheelUI.spin(wheelHost, resultLabel, spinBtn, grade -> {
      collected.put(s.uid(), grade);
      runSpinSequence(classId, className, assignment, students, wheelHost, progress, resultLabel, spinBtn, index + 1, collected);
    });
  }

  private static void showGradesSummary(String classId, String className, Models.Assignment assignment,
                                        Map<String, Integer> grades, List<Models.StudentInClass> students) {
    StringBuilder sb = new StringBuilder();
    for (Models.StudentInClass s : students) {
      Integer g = grades.get(s.uid());
      if (g != null) sb.append(String.format("%-22s %d%%\n",
          s.firstName() + " " + s.lastName(), g));
    }
    Main.showInfo("Grades saved for " + assignment.name() + ":\n\n" + sb);
    Main.showAssignmentDetail(classId, className, assignment);
  }

  private static VBox wrapCard(javafx.scene.Node content) {
    VBox card = new VBox(content);
    card.getStyleClass().add("card");
    card.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(content, Priority.ALWAYS);
    return card;
  }

  private static class StudentRow {
    final Models.StudentInClass student;
    final SimpleStringProperty name = new SimpleStringProperty();
    final SimpleStringProperty username = new SimpleStringProperty();
    final SimpleStringProperty average = new SimpleStringProperty();

    StudentRow(Models.StudentInClass s) {
      student = s;
      name.set(s.firstName() + " " + s.lastName());
      username.set(s.username());
      average.set(String.format("%.1f (%s)", s.average(), s.letter()));
    }
  }

  private static class GradeRow {
    final String assignmentId;
    final SimpleStringProperty assignment = new SimpleStringProperty();
    final SimpleStringProperty score = new SimpleStringProperty();
    final SimpleStringProperty date = new SimpleStringProperty();

    GradeRow(Models.StudentGradeRow g) {
      assignmentId = g.assignmentId();
      assignment.set(g.assignmentName());
      score.set(g.score() + "%");
      date.set(g.gradedAt() > 0 ? new Models.GradeEntry(g.score(), g.gradedAt()).dateLabel() : "—");
    }
  }

  private static class GradeEditRow {
    final String uid;
    final int score;
    final SimpleStringProperty name = new SimpleStringProperty();
    final SimpleStringProperty grade = new SimpleStringProperty();

    GradeEditRow(Models.AssignmentGrade g) {
      uid = g.studentUid();
      score = g.score();
      name.set(g.studentName());
      grade.set(g.score() >= 0 ? g.score() + "%" : "—");
    }
  }
}
