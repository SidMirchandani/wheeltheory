package com.wheeltheory;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.function.IntConsumer;

public final class WheelUI {
  private static final double SIZE = 460;
  private static final double CX   = SIZE / 2;
  private static final double CY   = SIZE / 2;
  private static final double R    = 190;

  // JavaFX canvas: 0° = right, 90° = bottom, 180° = left, 270° = top (12 o'clock)
  private static final double POINTER_DEG = 270.0;

  private static final Color[] SLICE_COLORS = {
      Color.web("#7c3aed"),
      Color.web("#5b21b6"),
      Color.web("#2563eb"),
      Color.web("#1d4ed8"),
      Color.web("#0891b2"),
      Color.web("#0d9488"),
      Color.web("#059669"),
      Color.web("#ca8a04"),
      Color.web("#ea580c"),
  };

  private WheelUI() {}

  /** Draw the wheel at rest (no spin UI). */
  public static void drawIdle(StackPane host) {
    host.getChildren().setAll(buildWheelPane(0));
    host.setAlignment(Pos.CENTER);
  }

  /**
   * Puts only the wheel canvas in {@code host}. Result label and spin button
   * live in the parent layout so the wheel never shifts vertically.
   */
  public static void spin(StackPane host, Label resultLabel, Button spinBtn, IntConsumer onComplete) {
    Canvas canvas = new Canvas(SIZE, SIZE);
    GraphicsContext gc = canvas.getGraphicsContext2D();

    Circle glowRing = new Circle(R + 20);
    glowRing.setFill(Color.TRANSPARENT);
    glowRing.setStroke(Color.web("#8b5cf6", 0.0));
    glowRing.setStrokeWidth(4);
    DropShadow glow = new DropShadow(32, Color.web("#a78bfa", 0.0));
    glowRing.setEffect(glow);

    StackPane wheelPane = new StackPane(glowRing, canvas);
    wheelPane.setAlignment(Pos.CENTER);
    wheelPane.setMinSize(SIZE, SIZE);
    wheelPane.setPrefSize(SIZE, SIZE);
    wheelPane.setMaxSize(SIZE, SIZE);
    host.getChildren().setAll(wheelPane);
    host.setAlignment(Pos.CENTER);

    final double[] angle = {0};
    drawWheel(gc, angle[0]);

    resultLabel.getStyleClass().removeAll("wheel-landed");
    resultLabel.setText(" ");
    spinBtn.setDisable(false);
    spinBtn.setVisible(true);
    spinBtn.setManaged(true);

    spinBtn.setOnAction(e -> runSpin(gc, glowRing, glow, angle, resultLabel, spinBtn, onComplete));
  }

  private static StackPane buildWheelPane(double rotation) {
    Canvas canvas = new Canvas(SIZE, SIZE);
    drawWheel(canvas.getGraphicsContext2D(), rotation);
    StackPane pane = new StackPane(canvas);
    pane.setAlignment(Pos.CENTER);
    pane.setMinSize(SIZE, SIZE);
    pane.setPrefSize(SIZE, SIZE);
    pane.setMaxSize(SIZE, SIZE);
    return pane;
  }

  private static void runSpin(GraphicsContext gc, Circle glowRing, DropShadow glow,
                              double[] angle, Label resultLabel, Button spinBtn,
                              IntConsumer onComplete) {
    spinBtn.setDisable(true);
    resultLabel.getStyleClass().removeAll("wheel-landed");
    resultLabel.setText("Spinning…");

    Timeline glowIn = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(glowRing.strokeProperty(), Color.web("#8b5cf6", 0.0)),
            new KeyValue(glow.colorProperty(), Color.web("#a78bfa", 0.0))),
        new KeyFrame(Duration.millis(350),
            new KeyValue(glowRing.strokeProperty(), Color.web("#8b5cf6", 0.75)),
            new KeyValue(glow.colorProperty(), Color.web("#a78bfa", 0.65)))
    );
    glowIn.play();

    int target      = Models.WHEEL_GRADES[(int) (Math.random() * Models.WHEEL_GRADES.length)];
    double slice    = 360.0 / Models.WHEEL_GRADES.length;
    int targetIdx   = indexOf(target);
    double sliceCenter = targetIdx * slice + slice / 2.0;
    double start    = angle[0];
    double minSpins = 6 + Math.random() * 3;
    double baseEnd  = (POINTER_DEG - sliceCenter % 360 + 360) % 360;
    double end      = start + minSpins * 360 + ((baseEnd - start % 360 + 360) % 360);

    final int frames   = 240;
    final long frameMs = 16;
    Timeline spinAnim = new Timeline();
    for (int i = 0; i <= frames; i++) {
      final int fi = i;
      spinAnim.getKeyFrames().add(new KeyFrame(Duration.millis(fi * frameMs), ev -> {
        double t     = fi / (double) frames;
        double eased = easeWheel(t);
        angle[0] = start + (end - start) * eased;
        drawWheel(gc, angle[0]);
      }));
    }

    spinAnim.setOnFinished(ev -> {
      angle[0] = end;
      drawWheel(gc, angle[0]);

      int landed = gradeAtPointer(angle[0]);

      FadeTransition pulse = new FadeTransition(Duration.millis(180), glowRing);
      pulse.setFromValue(1.0);
      pulse.setToValue(0.25);
      pulse.setCycleCount(6);
      pulse.setAutoReverse(true);
      pulse.play();

      resultLabel.setText(landed + "%");
      resultLabel.getStyleClass().add("wheel-landed");

      PauseTransition pause = new PauseTransition(Duration.millis(1500));
      pause.setOnFinished(x -> onComplete.accept(landed));
      pause.play();
    });

    spinAnim.play();
  }

  // legacy overload removed — use spin(host, resultLabel, spinBtn, onComplete)

  // ── drawing ──────────────────────────────────────────────────────────────────

  private static void drawWheel(GraphicsContext gc, double rotation) {
    gc.clearRect(0, 0, SIZE, SIZE);

    // Shadow
    gc.setFill(Color.color(0, 0, 0, 0.38));
    gc.fillOval(CX - R - 2, CY - R + 12, (R + 2) * 2, (R + 2) * 2);

    // Outer black ring
    double ringR = R + 15;
    gc.setFill(Color.web("#0c0c14"));
    gc.fillOval(CX - ringR, CY - ringR, ringR * 2, ringR * 2);
    gc.setStroke(Color.web("#6d28d9", 0.75));
    gc.setLineWidth(3);
    gc.strokeOval(CX - ringR, CY - ringR, ringR * 2, ringR * 2);

    drawTicks(gc, ringR);

    // Rotating slices — pass 1: fills, pass 2: labels (labels must be after ALL fills)
    gc.save();
    gc.translate(CX, CY);
    gc.rotate(rotation);
    gc.translate(-CX, -CY);

    double slice = 360.0 / Models.WHEEL_GRADES.length;
    for (int i = 0; i < Models.WHEEL_GRADES.length; i++) {
      double startDeg = i * slice;
      Color base  = SLICE_COLORS[i % SLICE_COLORS.length];
      Color light = base.interpolate(Color.WHITE, 0.20);

      gc.setFill(base);
      gc.fillArc(CX - R, CY - R, R * 2, R * 2, startDeg, slice, ArcType.ROUND);

      gc.setFill(light);
      gc.fillArc(CX - R, CY - R, R * 2, R * 2, startDeg, slice * 0.38, ArcType.ROUND);

      gc.setStroke(Color.web("#08080f", 0.88));
      gc.setLineWidth(1.8);
      gc.strokeArc(CX - R, CY - R, R * 2, R * 2, startDeg, slice, ArcType.ROUND);
    }

    // Radial dividers between slices
    gc.setStroke(Color.web("#08080f", 0.90));
    gc.setLineWidth(2);
    for (int i = 0; i < Models.WHEEL_GRADES.length; i++) {
      double rad = Math.toRadians(i * slice);
      gc.strokeLine(CX, CY, CX + Math.cos(rad) * R, CY + Math.sin(rad) * R);
    }

    // Light sheen before labels so labels stay on top
    RadialGradient sheen = new RadialGradient(0, 0, CX, CY - R * 0.28, R * 0.55,
        false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.color(1, 1, 1, 0.05)),
        new Stop(1, Color.color(1, 1, 1, 0.00)));
    gc.setFill(sheen);
    gc.fillOval(CX - R, CY - R, R * 2, R * 2);

    // Pass 2: labels on top of all slices
    gc.setFont(Font.font("Poppins", FontWeight.BOLD, 17));
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setTextBaseline(VPos.CENTER);
    for (int i = 0; i < Models.WHEEL_GRADES.length; i++) {
      double midDeg = i * slice + slice / 2.0;
      double midRad = Math.toRadians(midDeg);
      double tx = CX + Math.cos(midRad) * R * 0.64;
      double ty = CY + Math.sin(midRad) * R * 0.64;
      String label = String.valueOf(Models.WHEEL_GRADES[i]);

      gc.save();
      gc.translate(tx, ty);
      // Keep text upright and readable on every slice
      double textRot = midDeg + 90;
      if (midDeg > 90 && midDeg < 270) textRot += 180;
      gc.rotate(textRot);

      // Dark outline for contrast on any slice color
      gc.setStroke(Color.web("#0a0a12", 0.85));
      gc.setLineWidth(3);
      gc.setLineJoin(StrokeLineJoin.ROUND);
      gc.strokeText(label, 0, 0);

      gc.setFill(Color.WHITE);
      gc.fillText(label, 0, 0);
      gc.restore();
    }
    gc.restore();

    // Center hub
    double hubR = 24;
    gc.setFill(Color.web("#0c0c14"));
    gc.fillOval(CX - hubR, CY - hubR, hubR * 2, hubR * 2);
    gc.setStroke(Color.web("#a78bfa"));
    gc.setLineWidth(2.5);
    gc.strokeOval(CX - hubR, CY - hubR, hubR * 2, hubR * 2);
    gc.setFill(Color.web("#c4b5fd"));
    gc.fillOval(CX - 5, CY - 5, 10, 10);

    drawPointer(gc);
  }

  private static void drawTicks(GraphicsContext gc, double ringR) {
    int ticks = 36;
    gc.setStroke(Color.web("#a78bfa", 0.40));
    gc.setLineWidth(1.5);
    for (int i = 0; i < ticks; i++) {
      double a     = Math.toRadians(i * 360.0 / ticks);
      double outer = ringR - 2;
      double inner = ringR - (i % 3 == 0 ? 9 : 5);
      gc.strokeLine(CX + Math.cos(a) * inner, CY + Math.sin(a) * inner,
                    CX + Math.cos(a) * outer, CY + Math.sin(a) * outer);
    }
  }

  private static void drawPointer(GraphicsContext gc) {
    // POINTER_DEG = 270 → top of circle in JavaFX canvas coords
    double pRad   = Math.toRadians(POINTER_DEG);
    double tipX   = CX + Math.cos(pRad) * (R + 16);
    double tipY   = CY + Math.sin(pRad) * (R + 16);
    double baseX  = CX + Math.cos(pRad) * (R - 1);
    double baseY  = CY + Math.sin(pRad) * (R - 1);
    double perp   = pRad + Math.PI / 2;
    double spread = 13;

    double x1 = tipX, y1 = tipY;
    double x2 = baseX + Math.cos(perp) * spread;
    double y2 = baseY + Math.sin(perp) * spread;
    double x3 = baseX - Math.cos(perp) * spread;
    double y3 = baseY - Math.sin(perp) * spread;

    // Drop shadow
    gc.setFill(Color.color(0, 0, 0, 0.45));
    gc.fillPolygon(new double[]{x1+2, x2+2, x3+2}, new double[]{y1+2, y2+2, y3+2}, 3);

    gc.setFill(Color.web("#fbbf24"));
    gc.fillPolygon(new double[]{x1, x2, x3}, new double[]{y1, y2, y3}, 3);
    gc.setStroke(Color.web("#78350f"));
    gc.setLineWidth(1.5);
    gc.setLineJoin(StrokeLineJoin.ROUND);
    gc.strokePolygon(new double[]{x1, x2, x3}, new double[]{y1, y2, y3}, 3);

    // Tip highlight dot
    gc.setFill(Color.web("#fef3c7"));
    gc.fillOval(tipX - 4, tipY - 4, 8, 8);
  }

  // ── math ─────────────────────────────────────────────────────────────────────

  private static int gradeAtPointer(double rotation) {
    double slice   = 360.0 / Models.WHEEL_GRADES.length;
    // Which slice is under the pointer?
    // pointer is at POINTER_DEG; after rotating by `rotation` the slice at angle A
    // appears at canvas angle (A + rotation). We want (A + rotation) mod 360 == POINTER_DEG.
    double onWheel = ((POINTER_DEG - rotation) % 360 + 360) % 360;
    int idx        = (int) (onWheel / slice) % Models.WHEEL_GRADES.length;
    return Models.WHEEL_GRADES[idx];
  }

  private static int indexOf(int grade) {
    for (int i = 0; i < Models.WHEEL_GRADES.length; i++) {
      if (Models.WHEEL_GRADES[i] == grade) return i;
    }
    return 0;
  }

  /** Quick ease-in → long cruise → steep deceleration at the end. */
  private static double easeWheel(double t) {
    if (t < 0.12) {
      return (t / 0.12) * (t / 0.12) * 0.12 * 0.7;   // gentle ease-in
    }
    double u = 1.0 - t;
    return 1.0 - Math.pow(u, 4) * 5.5;                // strong ease-out
  }
}
