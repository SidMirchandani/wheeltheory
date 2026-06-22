package com.wheeltheory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class FirebaseClient {
    public static final String API_KEY = "AIzaSyDuitQb8zXXWtPjLhBPcoWXWHCaJ7-czqg";
    public static final String AUTH_DOMAIN = "wheel-theory.firebaseapp.com";
    public static final String DATABASE_URL = "https://wheel-theory-default-rtdb.europe-west1.firebasedatabase.app";
    /** Set from Firebase Console → Authentication → Google → Web client ID */
    public static String GOOGLE_CLIENT_ID = "685546278406-YOUR_WEB_CLIENT_ID.apps.googleusercontent.com";

    // Firebase REST endpoints (routed through local SDK shim during development)
    private static final String AUTH_BASE = LocalFirebaseBackend.LOCAL + "/identitytoolkit.googleapis.com/v1";
    private static final String RTDB_BASE = LocalFirebaseBackend.LOCAL + "/rtdb";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String idToken;
    private String refreshToken;
    private String localId;
    private String lastAuthEmail;
    private Models.UserProfile currentUser;
    private boolean demoMode;

    public String getIdToken() { return idToken; }
    public String getLocalId() { return localId; }
    public String getLastAuthEmail() { return lastAuthEmail; }
    public Models.UserProfile getCurrentUser() { return currentUser; }

    public void setSession(String idToken, String refreshToken, String localId) {
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.localId = localId;
    }

    /** One-click demo login — in-memory data only, no Firebase or local gateway. */
    public CompletableFuture<Void> signInAsDemo(boolean teacher) {
        DemoData.Store.reset();
        demoMode = true;
        String uid   = teacher ? DemoData.TEACHER_UID   : DemoData.STUDENT_UID;
        String email = teacher ? DemoData.TEACHER_EMAIL : DemoData.STUDENT_EMAIL;
        idToken      = "local-" + uid;
        refreshToken = "refresh-" + uid;
        localId      = uid;
        lastAuthEmail = email;
        currentUser = teacher
            ? new Models.UserProfile(uid, email, DemoData.TEACHER_USERNAME,
                Models.Role.TEACHER, "Jane", "Smith", Models.Title.MS)
            : new Models.UserProfile(uid, email, DemoData.STUDENT_USERNAME,
                Models.Role.STUDENT, "Alex", "Johnson", null);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> signUpEmail(String email, String password) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        body.put("returnSecureToken", true);
        return postAuth("accounts:signUp", body).thenApply(resp -> {
            applyAuthResponse(resp);
            return null;
        });
    }

    public CompletableFuture<Void> signInEmail(String email, String password) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        body.put("returnSecureToken", true);
        return postAuth("accounts:signInWithPassword", body).thenApply(resp -> {
            applyAuthResponse(resp);
            return null;
        });
    }

    public CompletableFuture<Void> signInUsername(String username, String password) {
        return lookupUsername(username).thenCompose(email -> {
            if (email == null || email.isBlank()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Username not found"));
            }
            return signInEmail(email, password);
        });
    }

    public CompletableFuture<Void> signInWithGoogleIdToken(String googleIdToken) {
        String postBody = "id_token=" + URLEncoder.encode(googleIdToken, StandardCharsets.UTF_8) + "&providerId=google.com";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("postBody", postBody);
        body.put("requestUri", "http://localhost");
        body.put("returnSecureToken", true);
        body.put("returnIdpCredential", true);
        return postAuth("accounts:signInWithIdp", body).thenApply(resp -> {
            applyAuthResponse(resp);
            return null;
        });
    }

    public CompletableFuture<Void> loadCurrentUser() {
        return getJson("users/" + localId).thenApply(node -> {
            if (node == null || node.isNull()) {
                throw new IllegalStateException("Profile not found. Complete registration.");
            }
            currentUser = parseProfile(localId, node);
            return null;
        });
    }

    public CompletableFuture<Void> saveUserProfile(Models.UserProfile profile) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("email", profile.email());
        node.put("username", profile.username());
        node.put("role", profile.role().name());
        node.put("firstName", profile.firstName() != null ? profile.firstName() : "");
        node.put("lastName", profile.lastName() != null ? profile.lastName() : "");
        node.put("title", profile.title() != null ? profile.title().name() : "");

        ObjectNode index = MAPPER.createObjectNode();
        index.put("uid", profile.uid());
        index.put("email", profile.email());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("users/" + profile.uid(), node);
        updates.put("usernameIndex/" + profile.username().toLowerCase(), index);

        return patchJson("", updates).thenApply(v -> {
            currentUser = profile;
            return null;
        });
    }

    public CompletableFuture<Boolean> usernameTaken(String username) {
        if (idToken != null) {
            return getJson("usernameIndex/" + username.toLowerCase()).thenApply(n -> n != null && !n.isNull());
        }
        return getJsonPublic("usernameIndex/" + username.toLowerCase()).thenApply(n -> n != null && !n.isNull());
    }

    public CompletableFuture<String> lookupUsername(String username) {
        return getJsonPublic("usernameIndex/" + username.toLowerCase()).thenApply(n -> {
            if (n == null || n.isNull()) return null;
            return n.path("email").asText(null);
        });
    }

    public CompletableFuture<String> lookupUidByUsername(String username) {
        return getJsonPublic("usernameIndex/" + username.toLowerCase()).thenApply(n -> {
            if (n == null || n.isNull()) return null;
            return n.path("uid").asText(null);
        });
    }

    public CompletableFuture<List<Models.ClassInfo>> listTeacherClasses() {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().listTeacherClasses(localId));
        return getJson("teachers/" + localId + "/classes").thenApply(this::parseClasses);
    }

    public CompletableFuture<Models.ClassInfo> createClass(String name) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().createClass(localId, name));
        String classId = UUID.randomUUID().toString().substring(0, 8);
        String joinCode = randomJoinCode();
        ObjectNode cls = MAPPER.createObjectNode();
        cls.put("name", name);
        cls.put("joinCode", joinCode);
        cls.put("createdAt", System.currentTimeMillis());

        ObjectNode code = MAPPER.createObjectNode();
        code.put("teacherId", localId);
        code.put("classId", classId);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("teachers/" + localId + "/classes/" + classId, cls);
        updates.put("joinCodes/" + joinCode, code);

        return patchJson("", updates).thenApply(v -> new Models.ClassInfo(classId, name, joinCode, localId));
    }

    public CompletableFuture<List<Models.StudentInClass>> listStudentsInClass(String classId) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().listStudentsInClass(localId, classId));
        return getJson("teachers/" + localId + "/classes/" + classId + "/students").thenCompose(studentsNode -> {
            if (studentsNode == null || studentsNode.isNull()) return CompletableFuture.completedFuture(List.of());
            List<CompletableFuture<Models.StudentInClass>> futures = new ArrayList<>();
            studentsNode.fieldNames().forEachRemaining(studentUid -> {
                futures.add(buildStudentInClass(classId, studentUid));
            });
            return allOf(futures);
        });
    }

    public CompletableFuture<Void> inviteStudentByUsername(String classId, String username) {
        return lookupUidByUsername(username).thenCompose(uid -> {
            if (uid == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Student username not found"));
            return getJson("users/" + uid).thenCompose(userNode -> {
                if (userNode == null || !"STUDENT".equals(userNode.path("role").asText())) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("User is not a student account"));
                }
                return enrollStudent(classId, uid, userNode);
            });
        });
    }

    public CompletableFuture<Void> inviteStudentByEmail(String classId, String email) {
        return getJson("users").thenCompose(allUsers -> {
            if (allUsers == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Student email not found"));
            String uid = null;
            JsonNode userNode = null;
            for (Iterator<String> it = allUsers.fieldNames(); it.hasNext(); ) {
                String id = it.next();
                JsonNode n = allUsers.get(id);
                if (email.equalsIgnoreCase(n.path("email").asText()) && "STUDENT".equals(n.path("role").asText())) {
                    uid = id;
                    userNode = n;
                    break;
                }
            }
            if (uid == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Student email not found"));
            final String studentUid = uid;
            final JsonNode node = userNode;
            return enrollStudent(classId, studentUid, node);
        });
    }

    public CompletableFuture<Void> joinClassByCode(String joinCode) {
        if (demoMode) {
            try {
                DemoData.Store.get().joinClassByCode(localId, joinCode);
                return DemoData.Store.doneVoid();
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        return getJson("joinCodes/" + joinCode.toUpperCase()).thenCompose(codeNode -> {
            if (codeNode == null || codeNode.isNull()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid join code"));
            }
            String teacherId = codeNode.path("teacherId").asText();
            String classId = codeNode.path("classId").asText();
            return getJson("users/" + localId).thenCompose(self -> enrollStudent(classId, localId, self, teacherId));
        });
    }

    public CompletableFuture<List<Models.Assignment>> listAssignments(String classId) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().listAssignments(localId, classId));
        return getJson("teachers/" + localId + "/classes/" + classId + "/assignments").thenApply(node -> {
            if (node == null || node.isNull()) return List.of();
            List<Models.Assignment> list = new ArrayList<>();
            node.fieldNames().forEachRemaining(id -> {
                JsonNode a = node.get(id);
                list.add(new Models.Assignment(id, a.path("name").asText(), a.path("createdAt").asLong()));
            });
            list.sort(Comparator.comparingLong(Models.Assignment::createdAt).reversed());
            return list;
        });
    }

    public CompletableFuture<Models.Assignment> createAssignment(String classId, String name) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().createAssignment(localId, classId, name));
        String id = UUID.randomUUID().toString().substring(0, 8);
        ObjectNode a = MAPPER.createObjectNode();
        a.put("name", name);
        a.put("createdAt", System.currentTimeMillis());
        return putJson("teachers/" + localId + "/classes/" + classId + "/assignments/" + id, a)
                .thenApply(v -> new Models.Assignment(id, name, System.currentTimeMillis()));
    }

    public CompletableFuture<Void> deleteAssignment(String classId, String assignmentId) {
        if (demoMode) {
            DemoData.Store.get().deleteAssignment(localId, classId, assignmentId);
            return DemoData.Store.doneVoid();
        }
        return deletePath("teachers/" + localId + "/classes/" + classId + "/assignments/" + assignmentId);
    }

    public CompletableFuture<List<Models.AssignmentGrade>> getAssignmentGrades(String classId, String assignmentId) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().getAssignmentGrades(localId, classId, assignmentId));
        String base = "teachers/" + localId + "/classes/" + classId + "/assignments/" + assignmentId;
        return getJson(base + "/grades").thenCompose(gradesNode -> {
            return listStudentsInClass(classId).thenApply(students -> {
                Map<String, Models.StudentInClass> byUid = new HashMap<>();
                for (Models.StudentInClass s : students) byUid.put(s.uid(), s);
                List<Models.AssignmentGrade> out = new ArrayList<>();
                if (gradesNode != null && !gradesNode.isNull()) {
                    gradesNode.fieldNames().forEachRemaining(uid -> {
                        JsonNode g = gradesNode.get(uid);
                        Models.StudentInClass s = byUid.get(uid);
                        String name = s != null ? s.firstName() + " " + s.lastName() : uid;
                        out.add(new Models.AssignmentGrade(uid, name.trim(), g.path("score").asInt(), g.path("gradedAt").asLong()));
                    });
                }
                for (Models.StudentInClass s : students) {
                    if (out.stream().noneMatch(g -> g.studentUid().equals(s.uid()))) {
                        out.add(new Models.AssignmentGrade(s.uid(), s.firstName() + " " + s.lastName(), -1, 0));
                    }
                }
                out.sort(Comparator.comparing(Models.AssignmentGrade::studentName));
                return out;
            });
        });
    }

    public CompletableFuture<Void> saveGrade(String classId, String assignmentId, String studentUid, int score) {
        if (demoMode) {
            DemoData.Store.get().saveGrade(localId, classId, assignmentId, studentUid, score);
            return DemoData.Store.doneVoid();
        }
        ObjectNode g = MAPPER.createObjectNode();
        g.put("score", score);
        g.put("gradedAt", System.currentTimeMillis());
        return putJson("teachers/" + localId + "/classes/" + classId + "/assignments/" + assignmentId + "/grades/" + studentUid, g);
    }

    public CompletableFuture<Void> saveGradesBatch(String classId, String assignmentId, Map<String, Integer> grades) {
        if (demoMode) {
            DemoData.Store.get().saveGradesBatch(localId, classId, assignmentId, grades);
            return DemoData.Store.doneVoid();
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (var e : grades.entrySet()) {
            ObjectNode g = MAPPER.createObjectNode();
            g.put("score", e.getValue());
            g.put("gradedAt", now);
            updates.put("teachers/" + localId + "/classes/" + classId + "/assignments/" + assignmentId + "/grades/" + e.getKey(), g);
        }
        return patchJson("", updates);
    }

    public CompletableFuture<List<Models.StudentGradeRow>> getStudentGradesInClass(String teacherId, String classId, String studentUid) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().getStudentGradesInClass(teacherId, classId, studentUid));
        return getJson("teachers/" + teacherId + "/classes/" + classId + "/assignments").thenCompose(assignmentsNode -> {
            if (assignmentsNode == null || assignmentsNode.isNull()) return CompletableFuture.completedFuture(List.of());
            List<CompletableFuture<Models.StudentGradeRow>> futures = new ArrayList<>();
            assignmentsNode.fieldNames().forEachRemaining(aid -> {
                JsonNode a = assignmentsNode.get(aid);
                futures.add(getJson("teachers/" + teacherId + "/classes/" + classId + "/assignments/" + aid + "/grades/" + studentUid)
                        .thenApply(g -> {
                            if (g == null || g.isNull()) return null;
                            return new Models.StudentGradeRow(aid, a.path("name").asText(), g.path("score").asInt(), g.path("gradedAt").asLong(), false);
                        }));
            });
            return allOfNullable(futures).thenApply(list -> {
                list.removeIf(Objects::isNull);
                list.sort(Comparator.comparingLong(Models.StudentGradeRow::gradedAt).reversed());
                return list;
            });
        });
    }

    public CompletableFuture<List<Models.StudentClassSummary>> listStudentClasses() {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().listStudentClasses(localId));
        return getJson("students/" + localId + "/classes").thenCompose(node -> {
            if (node == null || node.isNull()) return CompletableFuture.completedFuture(List.of());
            List<CompletableFuture<Models.StudentClassSummary>> futures = new ArrayList<>();
            node.fieldNames().forEachRemaining(classId -> {
                JsonNode c = node.get(classId);
                String teacherId = c.path("teacherId").asText();
                String className = c.path("className").asText();
                futures.add(getStudentGradesInClass(teacherId, classId, localId).thenApply(grades -> {
                    List<Integer> scores = grades.stream().map(Models.StudentGradeRow::score).toList();
                    double avg = Models.average(scores);
                    return new Models.StudentClassSummary(classId, className, avg, Models.letterGrade(avg));
                }));
            });
            return allOf(futures);
        });
    }

    public CompletableFuture<String> getTeacherIdForStudentClass(String classId) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().getTeacherIdForStudentClass(localId, classId));
        return getJson("students/" + localId + "/classes/" + classId).thenApply(n ->
                n != null && !n.isNull() ? n.path("teacherId").asText(null) : null);
    }

    public CompletableFuture<String> getClassNameForStudent(String classId) {
        if (demoMode) return DemoData.Store.done(DemoData.Store.get().getClassNameForStudent(localId, classId));
        return getJson("students/" + localId + "/classes/" + classId).thenApply(n ->
                n != null && !n.isNull() ? n.path("className").asText("") : "");
    }

    // --- helpers ---

    private CompletableFuture<Void> enrollStudent(String classId, String studentUid, JsonNode userNode) {
        return enrollStudent(classId, studentUid, userNode, localId);
    }

    private CompletableFuture<Void> enrollStudent(String classId, String studentUid, JsonNode userNode, String teacherId) {
        return getJson("teachers/" + teacherId + "/classes/" + classId).thenCompose(cls -> {
            if (cls == null || cls.isNull()) return CompletableFuture.failedFuture(new IllegalArgumentException("Class not found"));
            String className = cls.path("name").asText();
            ObjectNode studentRef = MAPPER.createObjectNode();
            studentRef.put("joinedAt", System.currentTimeMillis());

            ObjectNode studentClass = MAPPER.createObjectNode();
            studentClass.put("teacherId", teacherId);
            studentClass.put("className", className);
            studentClass.put("joinedAt", System.currentTimeMillis());

            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("teachers/" + teacherId + "/classes/" + classId + "/students/" + studentUid, studentRef);
            updates.put("students/" + studentUid + "/classes/" + classId, studentClass);
            return patchJson("", updates);
        });
    }

    private CompletableFuture<Models.StudentInClass> buildStudentInClass(String classId, String studentUid) {
        return getJson("users/" + studentUid).thenCompose(user -> {
            return getStudentGradesInClass(localId, classId, studentUid).thenApply(grades -> {
                List<Integer> scores = grades.stream().map(Models.StudentGradeRow::score).toList();
                double avg = Models.average(scores);
                return new Models.StudentInClass(
                        studentUid,
                        user.path("username").asText(),
                        user.path("firstName").asText(),
                        user.path("lastName").asText(),
                        avg,
                        Models.letterGrade(avg)
                );
            });
        });
    }

    private List<Models.ClassInfo> parseClasses(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        List<Models.ClassInfo> list = new ArrayList<>();
        node.fieldNames().forEachRemaining(id -> {
            JsonNode c = node.get(id);
            list.add(new Models.ClassInfo(id, c.path("name").asText(), c.path("joinCode").asText(), localId));
        });
        list.sort(Comparator.comparing(Models.ClassInfo::name));
        return list;
    }

    private Models.UserProfile parseProfile(String uid, JsonNode n) {
        Models.Title title = null;
        String t = n.path("title").asText("");
        if (!t.isBlank()) title = Models.Title.valueOf(t);
        return new Models.UserProfile(
                uid,
                n.path("email").asText(),
                n.path("username").asText(),
                Models.Role.valueOf(n.path("role").asText("STUDENT")),
                n.path("firstName").asText(),
                n.path("lastName").asText(),
                title
        );
    }

    private void applyAuthResponse(JsonNode resp) {
        demoMode = false;
        idToken = resp.path("idToken").asText();
        refreshToken = resp.path("refreshToken").asText();
        localId = resp.path("localId").asText();
        lastAuthEmail = resp.path("email").asText("");
    }

    private CompletableFuture<JsonNode> postAuth(String endpoint, ObjectNode body) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(AUTH_BASE + "/" + endpoint + "?key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
    }

    private CompletableFuture<JsonNode> getJson(String path) {
        return send(HttpRequest.newBuilder().uri(URI.create(dbUrl(path))).GET().build());
    }

    private CompletableFuture<JsonNode> getJsonPublic(String path) {
        return send(HttpRequest.newBuilder().uri(URI.create(rtdbUrl(path, false))).GET().build());
    }

    private String rtdbUrl(String path, boolean withAuth) {
        String url = RTDB_BASE + "/" + path + ".json";
        if (withAuth && idToken != null) url += "?auth=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        return url;
    }

    private String dbUrl(String path) {
        return rtdbUrl(path, true);
    }

    private CompletableFuture<Void> putJson(String path, ObjectNode body) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(dbUrl(path)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()).thenApply(v -> null);
    }

    private CompletableFuture<Void> patchJson(String path, Map<String, Object> updates) {
        String url = RTDB_BASE + "/" + (path.isEmpty() ? "" : path + "/") + ".json";
        if (idToken != null) url += "?auth=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        try {
            String json = MAPPER.writeValueAsString(updates);
            return send(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .build()).thenApply(v -> null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> deletePath(String path) {
        return send(HttpRequest.newBuilder().uri(URI.create(dbUrl(path))).DELETE().build()).thenApply(v -> null);
    }

    private CompletableFuture<JsonNode> send(HttpRequest request) {
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            try {
                String body = resp.body();
                JsonNode node = MAPPER.readTree(body.isBlank() ? "{}" : body);
                if (resp.statusCode() >= 400 || node.has("error")) {
                    JsonNode err = node.get("error");
                    String msg = err == null ? body
                        : err.isTextual() ? err.asText()
                        : err.path("message").asText(body);
                    if (msg.toLowerCase().contains("permission denied")) {
                        msg = "Firebase permission denied. Check Realtime Database rules in Firebase Console.";
                    }
                    throw new RuntimeException(msg);
                }
                return node;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String randomJoinCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    private static <T> CompletableFuture<List<T>> allOfNullable(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> new ArrayList<>(futures.stream().map(CompletableFuture::join).toList()));
    }
}
