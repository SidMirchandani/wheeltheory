package com.wheeltheory;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class UiKit {
  public static final double ROW_H = 44;

  private UiKit() {}

  public static void styleTable(TableView<?> table) {
    table.getStyleClass().add("data-table");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setFixedCellSize(ROW_H);
    table.setPlaceholder(new Label("No rows yet"));
    table.itemsProperty().addListener((o, a, b) -> fitTableHeight(table));
    if (table.getItems() != null) {
      table.getItems().addListener((ListChangeListener<Object>) c -> fitTableHeight(table));
    }
  }

  public static <T> void setItems(TableView<T> table, java.util.List<T> items) {
    table.setItems(javafx.collections.FXCollections.observableArrayList(items));
    fitTableHeight(table);
  }

  public static void fitTableHeight(TableView<?> table) {
    int rows = table.getItems() == null ? 0 : table.getItems().size();
    double h = ROW_H * (Math.max(rows, 1) + 1.15);
    table.setPrefHeight(h);
    table.setMinHeight(Region.USE_PREF_SIZE);
    table.setMaxHeight(Region.USE_PREF_SIZE);
  }

  public static Button smallButton(String text, String... styles) {
    Button b = new Button(text);
    b.getStyleClass().add("btn-sm");
    b.getStyleClass().addAll(styles);
    return b;
  }

  public static HBox copyableCode(String code) {
    Label codeLabel = new Label(code);
    codeLabel.getStyleClass().add("join-code");
    Button copy = smallButton("Copy", "btn-secondary");
    copy.setOnAction(e -> {
      ClipboardContent cc = new ClipboardContent();
      cc.putString(code);
      Clipboard.getSystemClipboard().setContent(cc);
      copy.setText("Copied!");
      javafx.application.Platform.runLater(() -> copy.setText("Copy"));
    });
    HBox box = new HBox(10, codeLabel, copy);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  public static Button backButton(String text, Runnable action) {
    Button b = new Button(text);
    b.getStyleClass().add("btn-ghost");
    b.setOnAction(e -> action.run());
    return b;
  }

  public static void grow(TableColumn<?, ?> col) {
    col.setPrefWidth(1000);
    col.setMaxWidth(Double.MAX_VALUE);
  }

  /** Prevent constrained table layout from shrinking a column until text ellipsizes. */
  public static void minColumn(TableColumn<?, ?> col, double width) {
    col.setPrefWidth(width);
    col.setMinWidth(width);
  }

  public static void fieldGrow(javafx.scene.control.Control field) {
    field.setMaxWidth(Double.MAX_VALUE);
    field.getStyleClass().add("field");
  }

  public static Button primaryButton(String text) {
    Button b = new Button(text);
    b.getStyleClass().add("btn-primary");
    return b;
  }

  public static Button secondaryButton(String text) {
    Button b = new Button(text);
    b.getStyleClass().add("btn-secondary");
    return b;
  }

  public static Button accentButton(String text) {
    Button b = new Button(text);
    b.getStyleClass().add("btn-accent");
    return b;
  }
}
