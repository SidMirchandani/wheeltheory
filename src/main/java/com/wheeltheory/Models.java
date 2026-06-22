package com.wheeltheory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}

    public static final int[] WHEEL_GRADES = {100, 98, 96, 94, 92, 90, 88, 86, 84};

    public enum Role { TEACHER, STUDENT }

    public enum Title { MR("Mr."), MRS("Mrs."), MS("Ms.");
        public final String label;
        Title(String label) { this.label = label; }
    }

    public static String letterGrade(double avg) {
        if (avg >= 90) return "A";
        if (avg >= 80) return "B";
        if (avg >= 70) return "C";
        if (avg >= 60) return "D";
        return "F";
    }

    public static double average(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) return 0;
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    public record UserProfile(
            String uid,
            String email,
            String username,
            Role role,
            String firstName,
            String lastName,
            Title title
    ) {
        public String displayName() {
            if (role == Role.TEACHER && title != null && firstName != null) {
                return title.label + " " + firstName + (lastName != null ? " " + lastName : "");
            }
            String name = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            return name.isEmpty() ? username : name;
        }
    }

    public record ClassInfo(String id, String name, String joinCode, String teacherId) {}

    public record StudentInClass(
            String uid,
            String username,
            String firstName,
            String lastName,
            double average,
            String letter
    ) {}

    public record Assignment(String id, String name, long createdAt) {}

    public record GradeEntry(int score, long gradedAt) {
        public String dateLabel() {
            return Instant.ofEpochMilli(gradedAt).toString().substring(0, 10);
        }
    }

    public record AssignmentGrade(String studentUid, String studentName, int score, long gradedAt) {}

    public record StudentClassSummary(String classId, String className, double average, String letter) {}

    public record StudentGradeRow(String assignmentId, String assignmentName, int score, long gradedAt, boolean whatIf) {
        public StudentGradeRow withScore(int newScore) {
            return new StudentGradeRow(assignmentId, assignmentName, newScore, gradedAt, true);
        }
    }

    public static List<Integer> randomWheelGrades(int count) {
        List<Integer> out = new ArrayList<>();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < count; i++) {
            out.add(WHEEL_GRADES[r.nextInt(WHEEL_GRADES.length)]);
        }
        return out;
    }
}
