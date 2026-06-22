package com.wheeltheory;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StudentUI {
  private StudentUI() {}

  public static BorderPane dashboard() {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));

    Models.UserProfile user = Main.FIREBASE.getCurrentUser();
    Label heading = new Label("Hello, " + (user != null ? user.displayName() : "Student"));
    heading.getStyleClass().add("title");

    Button signOut = UiKit.backButton("Sign Out", Main::showAuth);
    signOut.getStyleClass().add("btn-secondary");

    TextField joinCode = new TextField();
    joinCode.setPromptText("Enter class join code");
    joinCode.getStyleClass().add("field");

    ListView<Models.StudentClassSummary> classList = new ListView<>();
    classList.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(Models.StudentClassSummary item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) setText(null);
        else setText(item.className() + "  —  " + String.format("%.1f", item.average()) + " (" + item.letter() + ")");
      }
    });

    Button join = new Button("Join Class");
    join.getStyleClass().add("btn-primary");
    join.setOnAction(e -> {
      String code = joinCode.getText().trim();
      if (code.isEmpty()) return;
      Main.runAsync(() -> Main.FIREBASE.joinClassByCode(code), v -> refreshClassList(classList));
    });

    classList.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        Models.StudentClassSummary c = classList.getSelectionModel().getSelectedItem();
        if (c != null) Main.showStudentClass(c.classId(), c.className());
      }
    });

    Button open = new Button("Open Class");
    open.setOnAction(e -> {
      Models.StudentClassSummary c = classList.getSelectionModel().getSelectedItem();
      if (c != null) Main.showStudentClass(c.classId(), c.className());
    });

    VBox center = new VBox(12, heading, new HBox(10, joinCode, join), classList, open);
    pane.setTop(signOut);
    pane.setCenter(wrapCard(center));
    refreshClassList(classList);
    return pane;
  }

  private static void refreshClassList(ListView<Models.StudentClassSummary> list) {
    Main.runAsync(() -> Main.FIREBASE.listStudentClasses(),
        classes -> list.setItems(FXCollections.observableArrayList(classes)));
  }

  public static BorderPane classGrades(String classId, String className) {
    BorderPane pane = new BorderPane();
    pane.setPadding(new Insets(20));

    Button back = UiKit.backButton("← Dashboard", Main::showStudentDashboard);

    Label title = new Label(className);
    title.getStyleClass().add("title");
    Label avgLabel = new Label();
    avgLabel.getStyleClass().add("meta");

    TableView<WhatIfRow> table = new TableView<>();
    UiKit.styleTable(table);
    TableColumn<WhatIfRow, String> aCol = new TableColumn<>("Assignment");
    aCol.setCellValueFactory(c -> c.getValue().name);
    TableColumn<WhatIfRow, String> gCol = new TableColumn<>("Grade");
    gCol.setCellValueFactory(c -> c.getValue().grade);
    TableColumn<WhatIfRow, String> dCol = new TableColumn<>("Date");
    dCol.setCellValueFactory(c -> c.getValue().date);

    final boolean[] whatIfMode = {false};
    final List<Models.StudentGradeRow> original = new ArrayList<>();
    final List<WhatIfRow> whatIfRows = new ArrayList<>();

    Button whatIf = new Button("What-If");
    whatIf.getStyleClass().add("btn-accent");
    Button cancelWhatIf = new Button("Cancel What-If");
    cancelWhatIf.setVisible(false);
    cancelWhatIf.setManaged(false);
    Button addWhatIf = new Button("+ Add assignment");
    addWhatIf.setVisible(false);
    addWhatIf.setManaged(false);
    Button removeWhatIf = new Button("− Remove selected");
    removeWhatIf.setVisible(false);
    removeWhatIf.setManaged(false);

    Runnable updateAverage = () -> {
      List<Integer> scores = whatIfRows.stream()
          .filter(r -> r.score >= 0)
          .map(r -> r.score)
          .toList();
      double avg = Models.average(scores);
      avgLabel.setText("Average: " + String.format("%.1f", avg) + " (" + Models.letterGrade(avg) + ")"
          + (whatIfMode[0] ? "  [What-If]" : ""));
    };

    Runnable loadReal = () -> {
      whatIfMode[0] = false;
      whatIf.setVisible(true);
      whatIf.setManaged(true);
      cancelWhatIf.setVisible(false);
      cancelWhatIf.setManaged(false);
      addWhatIf.setVisible(false);
      addWhatIf.setManaged(false);
      removeWhatIf.setVisible(false);
      removeWhatIf.setManaged(false);
      table.setEditable(false);

      Main.runAsync(() -> Main.FIREBASE.getTeacherIdForStudentClass(classId).thenCompose(tid ->
          Main.FIREBASE.getStudentGradesInClass(tid, classId, Main.FIREBASE.getLocalId())), grades -> {
        original.clear();
        original.addAll(grades);
        whatIfRows.clear();
        for (Models.StudentGradeRow g : grades) whatIfRows.add(new WhatIfRow(g, false));
        UiKit.setItems(table, whatIfRows);
        updateAverage.run();
      });
    };

    whatIf.setOnAction(e -> {
      whatIfMode[0] = true;
      whatIf.setVisible(false);
      whatIf.setManaged(false);
      cancelWhatIf.setVisible(true);
      cancelWhatIf.setManaged(true);
      addWhatIf.setVisible(true);
      addWhatIf.setManaged(true);
      removeWhatIf.setVisible(true);
      removeWhatIf.setManaged(true);
      table.setEditable(true);
      gCol.setCellFactory(col -> new TableCell<>() {
        private TextField field;
        @Override
        public void startEdit() {
          super.startEdit();
          WhatIfRow row = getTableView().getItems().get(getIndex());
          field = new TextField(row.score >= 0 ? String.valueOf(row.score) : "");
          field.setOnAction(ev -> applyGrade(field.getText()));
          field.focusedProperty().addListener((o, old, focused) -> {
            if (!focused) applyGrade(field.getText());
          });
          setGraphic(field);
          setText(null);
          field.requestFocus();
        }
        private void applyGrade(String val) {
          try {
            int s = Integer.parseInt(val.trim());
            WhatIfRow row = getTableView().getItems().get(getIndex());
            row.score = s;
            row.grade.set(s + "%");
            row.whatIf = true;
            updateAverage.run();
          } catch (NumberFormatException ex) {
            Main.showError("Enter a number");
          }
          cancelEdit();
        }
        @Override
        public void cancelEdit() {
          super.cancelEdit();
          WhatIfRow row = getTableView().getItems().get(getIndex());
          setGraphic(null);
          setText(row.grade.get());
        }
        @Override
        protected void updateItem(String item, boolean empty) {
          super.updateItem(item, empty);
          if (empty) {
            setText(null);
            setGraphic(null);
          } else if (!isEditing()) {
            setText(item);
            setGraphic(null);
          }
        }
      });
      updateAverage.run();
    });

    cancelWhatIf.setOnAction(e -> loadReal.run());

    addWhatIf.setOnAction(e -> {
      TextInputDialog nameD = new TextInputDialog();
      nameD.setHeaderText("What-If assignment name");
      nameD.showAndWait().ifPresent(name -> {
        if (name.isBlank()) return;
        TextInputDialog gradeD = new TextInputDialog("90");
        gradeD.setHeaderText("Grade");
        gradeD.showAndWait().ifPresent(g -> {
          try {
            int score = Integer.parseInt(g.trim());
            WhatIfRow row = new WhatIfRow(
                new Models.StudentGradeRow("whatif-" + UUID.randomUUID(), name.trim(), score, System.currentTimeMillis(), true),
                true);
            whatIfRows.add(row);
            UiKit.setItems(table, whatIfRows);
            updateAverage.run();
          } catch (NumberFormatException ex) {
            Main.showError("Invalid grade");
          }
        });
      });
    });

    removeWhatIf.setOnAction(e -> {
      WhatIfRow sel = table.getSelectionModel().getSelectedItem();
      if (sel != null) {
        whatIfRows.remove(sel);
        UiKit.setItems(table, whatIfRows);
        updateAverage.run();
      }
    });

    table.getColumns().addAll(aCol, gCol, dCol);
    UiKit.grow(aCol);
    UiKit.minColumn(gCol, 72);
    UiKit.minColumn(dCol, 118);

    HBox actions = new HBox(10, whatIf, cancelWhatIf, addWhatIf, removeWhatIf);
    actions.setAlignment(Pos.CENTER_LEFT);

    pane.setTop(back);
    pane.setCenter(wrapCard(new VBox(10, title, avgLabel, table, actions)));
    loadReal.run();
    return pane;
  }

  private static VBox wrapCard(javafx.scene.Node content) {
    VBox card = new VBox(content);
    card.getStyleClass().add("card");
    return card;
  }

  private static class WhatIfRow {
    final SimpleStringProperty name = new SimpleStringProperty();
    final SimpleStringProperty grade = new SimpleStringProperty();
    final SimpleStringProperty date = new SimpleStringProperty();
    int score;
    boolean whatIf;

    WhatIfRow(Models.StudentGradeRow g, boolean whatIf) {
      name.set(g.assignmentName() + (whatIf || g.whatIf() ? " *" : ""));
      score = g.score();
      grade.set(g.score() + "%");
      date.set(g.gradedAt() > 0 ? new Models.GradeEntry(g.score(), g.gradedAt()).dateLabel() : "—");
      this.whatIf = whatIf || g.whatIf();
    }
  }
}
