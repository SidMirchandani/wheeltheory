package com.wheeltheory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Local Firebase Realtime Database + Auth gateway for offline development.
 * Binds to localhost only — not exposed to the network.
 */
final class LocalFirebaseBackend {
  static final int PORT = 9199;
  static final String LOCAL = "http://127.0.0.1:" + PORT;

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path DATA_FILE = Path.of(System.getProperty("user.home"), ".wheel-theory", "firebase-local.json");

  private static ObjectNode root = JsonNodeFactory.instance.objectNode();
  private static ObjectNode authRoot = JsonNodeFactory.instance.objectNode();
  private static volatile boolean started;

  private LocalFirebaseBackend() {}

  static void start() {
    if (started) return;
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
      server.createContext("/", LocalFirebaseBackend::handle);
      server.setExecutor(Executors.newCachedThreadPool());
      server.start();
      started = true;
    } catch (BindException e) {
      if (isAlreadyRunning()) {
        started = true;
        return;
      }
      throw new RuntimeException(
          "Port " + PORT + " is in use. Close the other Wheel Theory window, or run: "
              + "Get-Process -Name java | Stop-Process", e);
    } catch (IOException e) {
      throw new RuntimeException("Failed to start local Firebase gateway on port " + PORT, e);
    }
  }

  private static boolean isAlreadyRunning() {
    try {
      HttpResponse<String> resp = HttpClient.newHttpClient().send(
          HttpRequest.newBuilder().uri(URI.create(LOCAL + "/rtdb/.json")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      return resp.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  private static void handle(HttpExchange ex) throws IOException {
    try {
      String method = ex.getRequestMethod();
      String path = ex.getRequestURI().getPath();
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

      if (path.contains("/identitytoolkit.googleapis.com/v1/")) {
        handleAuth(ex, path, body);
        return;
      }
      if (path.startsWith("/rtdb/")) {
        handleDb(ex, method, path.substring("/rtdb/".length()), body);
        return;
      }
      respond(ex, 404, "{\"error\":\"Not found\"}");
    } catch (RuntimeException e) {
      respond(ex, 400, "{\"error\":{\"message\":\"" + esc(e.getMessage()) + "\"}}");
    }
  }

  private static void handleAuth(HttpExchange ex, String path, String body) throws IOException {
    synchronized (LocalFirebaseBackend.class) {
      load();
    }
    JsonNode req = MAPPER.readTree(body.isBlank() ? "{}" : body);
    if (path.endsWith("accounts:signUp")) {
      String email = req.path("email").asText("").toLowerCase();
      String password = req.path("password").asText("");
      if (email.isBlank() || password.isBlank()) throw new RuntimeException("MISSING_EMAIL");
      if (findUidByEmail(email) != null) throw new RuntimeException("EMAIL_EXISTS");
      String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 28);
      ObjectNode acct = authRoot.objectNode();
      acct.put("email", email);
      acct.put("password", password);
      authRoot.set(uid, acct);
      save();
      respond(ex, 200, authResponse(uid, email));
      return;
    }
    if (path.endsWith("accounts:signInWithPassword")) {
      String email = req.path("email").asText("").toLowerCase();
      String password = req.path("password").asText("");
      String uid = findUidByEmail(email);
      if (uid == null) throw new RuntimeException("EMAIL_NOT_FOUND");
      if (!authRoot.get(uid).path("password").asText().equals(password)) {
        throw new RuntimeException("INVALID_PASSWORD");
      }
      respond(ex, 200, authResponse(uid, email));
      return;
    }
    if (path.endsWith("accounts:signInWithIdp")) {
      String postBody = req.path("postBody").asText("");
      String email = "google.user@localhost";
      for (String part : postBody.split("&")) {
        if (part.startsWith("id_token=")) {
          email = emailFromGoogleToken(part.substring("id_token=".length()));
          break;
        }
      }
      email = email.toLowerCase();
      String uid = findUidByEmail(email);
      if (uid == null) {
        uid = UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        ObjectNode acct = authRoot.objectNode();
        acct.put("email", email);
        acct.put("password", "");
        authRoot.set(uid, acct);
        save();
      }
      respond(ex, 200, authResponse(uid, email));
      return;
    }
    respond(ex, 404, "{\"error\":\"Unknown auth endpoint\"}");
  }

  private static void handleDb(HttpExchange ex, String method, String path, String body) throws IOException {
    String jsonPath = path.endsWith(".json") ? path.substring(0, path.length() - 5) : path;
    synchronized (LocalFirebaseBackend.class) {
      load();
      switch (method) {
        case "GET" -> {
          JsonNode val = getAt(jsonPath);
          respond(ex, 200, val == null ? "null" : val.toString());
        }
        case "PUT" -> {
          JsonNode val = MAPPER.readTree(body.isBlank() ? "null" : body);
          setAt(jsonPath, val);
          save();
          respond(ex, 200, val.toString());
        }
        case "PATCH" -> {
          JsonNode patch = MAPPER.readTree(body.isBlank() ? "{}" : body);
          if (jsonPath.isEmpty()) {
            patch.fields().forEachRemaining(e -> setAt(e.getKey(), e.getValue()));
          } else {
            setAt(jsonPath, patch);
          }
          save();
          respond(ex, 200, "{}");
        }
        case "DELETE" -> {
          deleteAt(jsonPath);
          save();
          respond(ex, 200, "null");
        }
        default -> respond(ex, 405, "{\"error\":\"Method not allowed\"}");
      }
    }
  }

  private static String authResponse(String uid, String email) throws IOException {
    ObjectNode resp = MAPPER.createObjectNode();
    resp.put("idToken", "local-" + uid);
    resp.put("refreshToken", "refresh-" + uid);
    resp.put("localId", uid);
    resp.put("email", email);
    return resp.toString();
  }

  private static String findUidByEmail(String email) {
    for (Iterator<String> it = authRoot.fieldNames(); it.hasNext(); ) {
      String uid = it.next();
      if (email.equalsIgnoreCase(authRoot.get(uid).path("email").asText())) return uid;
    }
    return null;
  }

  private static String emailFromGoogleToken(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length >= 2) {
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonNode n = MAPPER.readTree(payload);
        if (n.has("email")) return n.path("email").asText("google.user@localhost");
      }
    } catch (Exception ignored) {}
    return "google.user@localhost";
  }

  private static JsonNode getAt(String path) {
    if (path == null || path.isBlank()) return root;
    String[] parts = path.split("/");
    JsonNode cur = root;
    for (String p : parts) {
      if (p.isBlank()) continue;
      cur = cur.path(p);
      if (cur.isMissingNode()) return null;
    }
    return cur.isMissingNode() ? null : cur;
  }

  private static void setAt(String path, JsonNode value) {
    if (path == null || path.isBlank()) {
      root = value.isObject() ? (ObjectNode) value.deepCopy() : root;
      return;
    }
    String[] parts = path.split("/");
    ObjectNode cur = root;
    for (int i = 0; i < parts.length - 1; i++) {
      String p = parts[i];
      if (p.isBlank()) continue;
      JsonNode next = cur.get(p);
      if (!(next instanceof ObjectNode)) {
        ObjectNode created = JsonNodeFactory.instance.objectNode();
        cur.set(p, created);
        next = created;
      }
      cur = (ObjectNode) next;
    }
    String leaf = parts[parts.length - 1];
    if (value.isNull()) cur.remove(leaf);
    else cur.set(leaf, value.deepCopy());
  }

  private static void deleteAt(String path) {
    if (path == null || path.isBlank()) {
      root = JsonNodeFactory.instance.objectNode();
      return;
    }
    String[] parts = path.split("/");
    ObjectNode cur = root;
    for (int i = 0; i < parts.length - 1; i++) {
      String p = parts[i];
      if (p.isBlank()) continue;
      JsonNode next = cur.get(p);
      if (!(next instanceof ObjectNode)) return;
      cur = (ObjectNode) next;
    }
    cur.remove(parts[parts.length - 1]);
  }

  private static void load() {
    try {
      if (Files.exists(DATA_FILE)) {
        JsonNode file = MAPPER.readTree(Files.readString(DATA_FILE));
        root = file.path("db").isObject() ? (ObjectNode) file.get("db") : JsonNodeFactory.instance.objectNode();
        authRoot = file.path("auth").isObject() ? (ObjectNode) file.get("auth") : JsonNodeFactory.instance.objectNode();
      }
    } catch (IOException e) {
      root = JsonNodeFactory.instance.objectNode();
      authRoot = JsonNodeFactory.instance.objectNode();
    }
  }

  private static void save() {
    try {
      Files.createDirectories(DATA_FILE.getParent());
      ObjectNode file = MAPPER.createObjectNode();
      file.set("db", root);
      file.set("auth", authRoot);
      Files.writeString(DATA_FILE, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(file));
    } catch (IOException e) {
      throw new RuntimeException("Failed to save local Firebase data", e);
    }
  }

  private static void respond(HttpExchange ex, int code, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json");
    ex.sendResponseHeaders(code, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static String esc(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
