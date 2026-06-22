package com.wheeltheory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Pre-built demo teacher & student accounts — fully in-memory, no Firebase. */
final class DemoData {
  static final String TEACHER_EMAIL = "jane.smith@lincolnhigh.edu";
  static final String STUDENT_EMAIL = "alex.johnson@lincolnhigh.edu";
  static final String PASSWORD = "demo123";
  static final String TEACHER_UID = "demoTeacher01";
  static final String STUDENT_UID = "demoStudent01";
  static final String CLASS_ID = "classDemo01";
  static final String JOIN_CODE = "WHEEL1";
  static final String TEACHER_USERNAME = "jane.smith";
  static final String STUDENT_USERNAME = "alex.johnson";

  private record DemoStudent(String uid, String username, String firstName, String lastName) {}

  private static final List<DemoStudent> CLASS_STUDENTS = List.of(
      new DemoStudent("demoStudent01", "alex.johnson",   "Alex",      "Johnson"),
      new DemoStudent("demoStudent02", "emily.chen",     "Emily",     "Chen"),
      new DemoStudent("demoStudent03", "marcus.williams","Marcus",    "Williams"),
      new DemoStudent("demoStudent04", "sophia.patel",   "Sophia",    "Patel"),
      new DemoStudent("demoStudent05", "liam.garcia",    "Liam",      "Garcia"),
      new DemoStudent("demoStudent06", "olivia.brown",   "Olivia",    "Brown"),
      new DemoStudent("demoStudent07", "noah.davis",     "Noah",      "Davis"),
      new DemoStudent("demoStudent08", "ava.martinez",   "Ava",       "Martinez"),
      new DemoStudent("demoStudent09", "ethan.kim",      "Ethan",     "Kim"),
      new DemoStudent("demoStudent10", "isabella.taylor","Isabella",  "Taylor")
  );

  private static final Map<String, DemoStudent> STUDENT_BY_UID = new LinkedHashMap<>();
  static {
    for (DemoStudent s : CLASS_STUDENTS) STUDENT_BY_UID.put(s.uid(), s);
  }

  private DemoData() {}

  static boolean isDemoUid(String uid) {
    return TEACHER_UID.equals(uid) || STUDENT_UID.equals(uid);
  }

  /** Mutable in-memory store used when demo buttons are clicked. */
  static final class Store {
    private static Store instance = new Store();

    private final Map<String, Models.ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, String> joinCodes = new HashMap<>();
    private final Map<String, Set<String>> studentsByClass = new HashMap<>();
    private final Map<String, List<Models.Assignment>> assignmentsByClass = new HashMap<>();
    private final Map<String, Map<String, Map<String, Models.GradeEntry>>> grades = new HashMap<>();
    private final Map<String, Map<String, StudentEnrollment>> studentClasses = new HashMap<>();

    private record StudentEnrollment(String teacherId, String className, long joinedAt) {}

    static synchronized void reset() {
      instance = new Store();
    }

    static synchronized Store get() {
      return instance;
    }

    private Store() {
      seed();
    }

    private void seed() {
      long now = System.currentTimeMillis();
      long week = 7L * 24 * 60 * 60 * 1000;

      Models.ClassInfo literature = new Models.ClassInfo(CLASS_ID, "AP Literature", JOIN_CODE, TEACHER_UID);
      classes.put(CLASS_ID, literature);
      joinCodes.put(JOIN_CODE, CLASS_ID);
      studentsByClass.put(CLASS_ID, new LinkedHashSet<>());
      for (DemoStudent s : CLASS_STUDENTS) {
        studentsByClass.get(CLASS_ID).add(s.uid());
      }

      List<Models.Assignment> assignments = new ArrayList<>();
      assignments.add(new Models.Assignment("essay01", "Essay", now - week * 2));
      assignments.add(new Models.Assignment("doc01", "Documentary", now - week));
      assignments.add(new Models.Assignment("quiz01", "Reading Quiz", now - 2L * 24 * 60 * 60 * 1000));
      assignmentsByClass.put(CLASS_ID, assignments);

      int[][] scores = {
          {96, 98, 92},  // Alex Johnson
          {94, 90, 88},  // Emily Chen
          {100, 96, 94}, // Marcus Williams
          {88, 86, 90},  // Sophia Patel
          {92, 94, 96},  // Liam Garcia
          {84, 88, 82},  // Olivia Brown
          {90, 92, 88},  // Noah Davis
          {98, 100, 96}, // Ava Martinez
          {86, 84, 88},  // Ethan Kim
          {94, 92, 90},  // Isabella Taylor
      };
      String[] assignmentIds = {"essay01", "doc01", "quiz01"};
      long[] gradedAt = {now - week * 2, now - week, now - 2L * 24 * 60 * 60 * 1000};
      for (int i = 0; i < CLASS_STUDENTS.size(); i++) {
        String uid = CLASS_STUDENTS.get(i).uid();
        for (int j = 0; j < assignmentIds.length; j++) {
          putGrade(CLASS_ID, assignmentIds[j], uid, scores[i][j], gradedAt[j]);
        }
        if (STUDENT_UID.equals(uid)) {
          Map<String, StudentEnrollment> enrolled = new LinkedHashMap<>();
          enrolled.put(CLASS_ID, new StudentEnrollment(TEACHER_UID, "AP Literature", now - week * 3));
          studentClasses.put(uid, enrolled);
        }
      }
    }

    private void putGrade(String classId, String assignmentId, String studentUid, int score, long gradedAt) {
      grades.computeIfAbsent(classId, k -> new HashMap<>())
          .computeIfAbsent(assignmentId, k -> new HashMap<>())
          .put(studentUid, new Models.GradeEntry(score, gradedAt));
    }

    List<Models.ClassInfo> listTeacherClasses(String teacherId) {
      if (!TEACHER_UID.equals(teacherId)) return List.of();
      return classes.values().stream()
          .sorted(Comparator.comparing(Models.ClassInfo::name))
          .toList();
    }

    List<Models.StudentClassSummary> listStudentClasses(String studentUid) {
      Map<String, StudentEnrollment> enrolled = studentClasses.getOrDefault(studentUid, Map.of());
      List<Models.StudentClassSummary> out = new ArrayList<>();
      for (var e : enrolled.entrySet()) {
        List<Integer> scores = gradeScoresForStudent(e.getKey(), studentUid);
        double avg = Models.average(scores);
        out.add(new Models.StudentClassSummary(e.getKey(), e.getValue().className(), avg, Models.letterGrade(avg)));
      }
      return out;
    }

    List<Models.StudentInClass> listStudentsInClass(String teacherId, String classId) {
      if (!TEACHER_UID.equals(teacherId) || !classes.containsKey(classId)) return List.of();
      List<Models.StudentInClass> out = new ArrayList<>();
      for (String uid : studentsByClass.getOrDefault(classId, Set.of())) {
        List<Integer> scores = gradeScoresForStudent(classId, uid);
        double avg = Models.average(scores);
        DemoStudent profile = STUDENT_BY_UID.get(uid);
        if (profile != null) {
          out.add(new Models.StudentInClass(
              uid, profile.username(), profile.firstName(), profile.lastName(), avg, Models.letterGrade(avg)));
        }
      }
      out.sort(Comparator.comparing(Models.StudentInClass::lastName));
      return out;
    }

    List<Models.Assignment> listAssignments(String teacherId, String classId) {
      if (!TEACHER_UID.equals(teacherId) || !classes.containsKey(classId)) return List.of();
      return assignmentsByClass.getOrDefault(classId, List.of()).stream()
          .sorted(Comparator.comparingLong(Models.Assignment::createdAt).reversed())
          .toList();
    }

    Models.ClassInfo createClass(String teacherId, String name) {
      String classId = UUID.randomUUID().toString().substring(0, 8);
      String joinCode = randomJoinCode();
      Models.ClassInfo info = new Models.ClassInfo(classId, name, joinCode, teacherId);
      classes.put(classId, info);
      joinCodes.put(joinCode, classId);
      studentsByClass.put(classId, new LinkedHashSet<>());
      assignmentsByClass.put(classId, new ArrayList<>());
      return info;
    }

    Models.Assignment createAssignment(String teacherId, String classId, String name) {
      String id = UUID.randomUUID().toString().substring(0, 8);
      Models.Assignment a = new Models.Assignment(id, name, System.currentTimeMillis());
      assignmentsByClass.computeIfAbsent(classId, k -> new ArrayList<>()).add(a);
      return a;
    }

    void deleteAssignment(String teacherId, String classId, String assignmentId) {
      if (!TEACHER_UID.equals(teacherId)) return;
      List<Models.Assignment> list = assignmentsByClass.get(classId);
      if (list != null) list.removeIf(a -> a.id().equals(assignmentId));
      Map<String, Map<String, Models.GradeEntry>> byAssignment = grades.get(classId);
      if (byAssignment != null) byAssignment.remove(assignmentId);
    }

    List<Models.AssignmentGrade> getAssignmentGrades(String teacherId, String classId, String assignmentId) {
      List<Models.StudentInClass> students = listStudentsInClass(teacherId, classId);
      Map<String, Models.GradeEntry> byStudent = grades.getOrDefault(classId, Map.of())
          .getOrDefault(assignmentId, Map.of());
      List<Models.AssignmentGrade> out = new ArrayList<>();
      for (Models.StudentInClass s : students) {
        Models.GradeEntry g = byStudent.get(s.uid());
        if (g != null) {
          out.add(new Models.AssignmentGrade(s.uid(), s.firstName() + " " + s.lastName(), g.score(), g.gradedAt()));
        } else {
          out.add(new Models.AssignmentGrade(s.uid(), s.firstName() + " " + s.lastName(), -1, 0));
        }
      }
      out.sort(Comparator.comparing(Models.AssignmentGrade::studentName));
      return out;
    }

    void saveGrade(String teacherId, String classId, String assignmentId, String studentUid, int score) {
      if (!TEACHER_UID.equals(teacherId)) return;
      putGrade(classId, assignmentId, studentUid, score, System.currentTimeMillis());
    }

    void saveGradesBatch(String teacherId, String classId, String assignmentId, Map<String, Integer> batch) {
      long now = System.currentTimeMillis();
      for (var e : batch.entrySet()) {
        putGrade(classId, assignmentId, e.getKey(), e.getValue(), now);
      }
    }

    List<Models.StudentGradeRow> getStudentGradesInClass(String teacherId, String classId, String studentUid) {
      if (!classes.containsKey(classId)) return List.of();
      List<Models.Assignment> assignments = assignmentsByClass.getOrDefault(classId, List.of());
      Map<String, Map<String, Models.GradeEntry>> byAssignment = grades.getOrDefault(classId, Map.of());
      List<Models.StudentGradeRow> out = new ArrayList<>();
      for (Models.Assignment a : assignments) {
        Models.GradeEntry g = byAssignment.getOrDefault(a.id(), Map.of()).get(studentUid);
        if (g != null) {
          out.add(new Models.StudentGradeRow(a.id(), a.name(), g.score(), g.gradedAt(), false));
        }
      }
      out.sort(Comparator.comparingLong(Models.StudentGradeRow::gradedAt).reversed());
      return out;
    }

    String getTeacherIdForStudentClass(String studentUid, String classId) {
      StudentEnrollment e = studentClasses.getOrDefault(studentUid, Map.of()).get(classId);
      return e != null ? e.teacherId() : null;
    }

    String getClassNameForStudent(String studentUid, String classId) {
      StudentEnrollment e = studentClasses.getOrDefault(studentUid, Map.of()).get(classId);
      return e != null ? e.className() : "";
    }

    void joinClassByCode(String studentUid, String joinCode) {
      String classId = joinCodes.get(joinCode.toUpperCase());
      if (classId == null) throw new IllegalArgumentException("Invalid join code");
      Models.ClassInfo cls = classes.get(classId);
      if (cls == null) throw new IllegalArgumentException("Class not found");
      if (studentClasses.getOrDefault(studentUid, Map.of()).containsKey(classId)) {
        throw new IllegalArgumentException("Already enrolled in this class");
      }
      long now = System.currentTimeMillis();
      studentsByClass.computeIfAbsent(classId, k -> new LinkedHashSet<>()).add(studentUid);
      studentClasses.computeIfAbsent(studentUid, k -> new LinkedHashMap<>())
          .put(classId, new StudentEnrollment(cls.teacherId(), cls.name(), now));
    }

    private List<Integer> gradeScoresForStudent(String classId, String studentUid) {
      Map<String, Map<String, Models.GradeEntry>> byAssignment = grades.getOrDefault(classId, Map.of());
      List<Integer> scores = new ArrayList<>();
      for (Map<String, Models.GradeEntry> byStudent : byAssignment.values()) {
        Models.GradeEntry g = byStudent.get(studentUid);
        if (g != null) scores.add(g.score());
      }
      return scores;
    }

    private static String randomJoinCode() {
      String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
      Random r = new Random();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
      return sb.toString();
    }

    static <T> CompletableFuture<T> done(T value) {
      return CompletableFuture.completedFuture(value);
    }

    static CompletableFuture<Void> doneVoid() {
      return CompletableFuture.completedFuture(null);
    }
  }
}
