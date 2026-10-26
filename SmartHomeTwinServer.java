import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class SmartHomeTwinServer {

   
private static final int PORT = Integer.parseInt(
    System.getenv().getOrDefault("PORT", "8787")
);

    private static final int SAFETY_POWER_LIMIT_WATTS = 1500;

    private static final Path ROOT =
        Path.of(".").toAbsolutePath().normalize();

    private static final Path DATA =
        Path.of(System.getProperty("user.home"), ".smart-home-twin-web");

    private static final Path USERS = DATA.resolve("users.txt");
    private static final Path HISTORY_FILE =
        DATA.resolve("activity-history.txt");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, User> sessions =
        new ConcurrentHashMap<>();

    private static final Map<String, User> users =
        new ConcurrentHashMap<>();

    private static final List<Device> devices = new ArrayList<>();
    private static final List<String> history = new ArrayList<>();

    private static volatile boolean safetyAlertActive = false;

    private static class User {
        String name;
        String email;
        String salt;
        String passwordHash;

        User(String name, String email, String salt, String hash) {
            this.name = name;
            this.email = email;
            this.salt = salt;
            this.passwordHash = hash;
        }
    }

    private static class Device {
        String id;
        String name;
        String room;
        int power;
        boolean on;

        Device(String id, String name, String room, int power) {
            this.id = id;
            this.name = name;
            this.room = room;
            this.power = power;
            this.on = false;
        }
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DATA);

        loadUsers();
        loadHistory();
        initialiseDevices();

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", PORT), 0
        );

        server.createContext("/", SmartHomeTwinServer::handleRequest);
        server.setExecutor(null);
        server.start();

        System.out.println("Smart Home Twin server started.");
        System.out.println("Open http://localhost:" + PORT);
        System.out.println("Activity history: " + HISTORY_FILE);
    }

    private static void initialiseDevices() {
        devices.add(new Device(
            "light", "Living Room Light", "Living Room", 12
        ));
        devices.add(new Device(
            "fan", "Bedroom Fan", "Bedroom", 60
        ));
        devices.add(new Device(
            "ac", "Air Conditioner", "Bedroom", 1500
        ));
        devices.add(new Device(
            "tv", "Television", "Living Room", 100
        ));
        devices.add(new Device(
            "kitchen-light", "Kitchen Light", "Kitchen", 10
        ));
        devices.add(new Device(
            "fridge", "Refrigerator", "Kitchen", 180
        ));
    }

    private static void handleRequest(HttpExchange ex)
        throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (path.startsWith("/api/")) {
                handleApi(ex, path, method);
            } else {
                serveStatic(ex, path, method);
            }
        } catch (Exception e) {
            e.printStackTrace();

            if (ex.getResponseCode() == -1) {
                json(ex, 500,
                    "{\"message\":\"Internal server error\"}");
            }
        } finally {
            ex.close();
        }
    }

    private static void handleApi(
        HttpExchange ex, String path, String method
    ) throws IOException {

        if (method.equals("OPTIONS")) {
            ex.getResponseHeaders().set(
                "Allow", "GET, POST, OPTIONS"
            );
            ex.sendResponseHeaders(204, -1);
            return;
        }

        Map<String, String> body = readJson(ex);
        User user;

        // SIGNUP
        if (path.equals("/api/signup") && method.equals("POST")) {
            String name = body.getOrDefault("name", "").trim();
            String email = body.getOrDefault("email", "")
                .trim().toLowerCase(Locale.ROOT);
            String password = body.getOrDefault("password", "");

            if (name.isEmpty() || name.length() > 80 ||
                !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                email.length() > 254 ||
                password.length() < 8 || password.length() > 128) {

                json(ex, 400,
                    "{\"message\":\"Enter a valid name, email and password of 8–128 characters.\"}");
                return;
            }

            synchronized (users) {
                if (users.containsKey(email)) {
                    json(ex, 409,
                        "{\"message\":\"An account with this email already exists.\"}");
                    return;
                }

                String salt = randomToken(16);
                String hash = hashPassword(password, salt);
                User newUser = new User(name, email, salt, hash);

                users.put(email, newUser);

                try {
                    saveUsers();
                } catch (IOException e) {
                    users.remove(email);
                    json(ex, 500,
                        "{\"message\":\"Could not save account.\"}");
                    return;
                }
            }

            json(ex, 201,
                "{\"message\":\"Account created. Please log in.\"}");
            return;
        }

        // LOGIN
        if (path.equals("/api/login") && method.equals("POST")) {
            String email = body.getOrDefault("email", "")
                .trim().toLowerCase(Locale.ROOT);
            String password = body.getOrDefault("password", "");

            User found = users.get(email);

            if (found == null ||
                !MessageDigest.isEqual(
                    hashPassword(password, found.salt)
                        .getBytes(StandardCharsets.UTF_8),
                    found.passwordHash.getBytes(StandardCharsets.UTF_8)
                )) {

                json(ex, 401,
                    "{\"message\":\"Invalid email or password.\"}");
                return;
            }

            String token = randomToken(32);
            sessions.put(token, found);

            ex.getResponseHeaders().add(
                "Set-Cookie",
                "SHT_SESSION=" + token +
                "; HttpOnly; SameSite=Strict; Path=/"
            );

            json(ex, 200, "{\"user\":" + userJson(found) + "}");
            return;
        }

        // CURRENT USER
        if (path.equals("/api/me") && method.equals("GET")) {
            user = getUser(ex);

            if (user == null) {
                json(ex, 200, "{\"authenticated\":false}");
            } else {
                json(ex, 200,
                    "{\"authenticated\":true,\"user\":" +
                    userJson(user) + "}");
            }
            return;
        }

        // LOGOUT
        if (path.equals("/api/logout") && method.equals("POST")) {
            String token = cookie(ex, "SHT_SESSION");

            if (token != null) {
                sessions.remove(token);
            }

            ex.getResponseHeaders().add(
                "Set-Cookie",
                "SHT_SESSION=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0"
            );

            json(ex, 200, "{\"message\":\"Logged out\"}");
            return;
        }

        // AUTHENTICATION REQUIRED
        user = getUser(ex);

        if (user == null) {
            json(ex, 401,
                "{\"message\":\"Please log in first.\"}");
            return;
        }

        // GET DEVICE STATE AND HISTORY
        if (path.equals("/api/state") && method.equals("GET")) {
            json(ex, 200, stateJson());
            return;
        }

        // TOGGLE ONE DEVICE
        if (path.equals("/api/toggle") && method.equals("POST")) {
            String id = body.getOrDefault("id", "");

            synchronized (devices) {
                Device device = findDevice(id);

                if (device == null) {
                    json(ex, 404,
                        "{\"message\":\"Appliance not found.\"}");
                    return;
                }

                device.on = !device.on;

                addHistory(device.name + " switched " +
                    (device.on ? "on" : "off"));

                updateSafetyStatus();
            }

            json(ex, 200, stateJson());
            return;
        }

        // TURN ALL DEVICES OFF
        if (path.equals("/api/all-off") && method.equals("POST")) {
            synchronized (devices) {
                boolean changed = false;

                for (Device d : devices) {
                    if (d.on) {
                        d.on = false;
                        changed = true;
                    }
                }

                if (changed) {
                    addHistory("All appliances switched off");
                }

                updateSafetyStatus();
            }

            json(ex, 200, stateJson());
            return;
        }

        json(ex, 404,
            "{\"message\":\"API endpoint not found.\"}");
    }

    // SERVE HTML, CSS AND JAVASCRIPT
    private static void serveStatic(
        HttpExchange ex, String path, String method
    ) throws IOException {

        if (!method.equals("GET") && !method.equals("HEAD")) {
            json(ex, 405,
                "{\"message\":\"Method not allowed\"}");
            return;
        }

        if (path.equals("/")) {
            path = "/index.html";
        }

        String decoded;

        try {
            decoded = URLDecoder.decode(
                path, StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException e) {
            json(ex, 400, "{\"message\":\"Invalid path\"}");
            return;
        }

        Path file = ROOT.resolve(decoded.substring(1)).normalize();

        if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) {
            json(ex, 404, "{\"message\":\"File not found\"}");
            return;
        }

        String type = "application/octet-stream";

        if (decoded.endsWith(".html")) {
            type = "text/html; charset=UTF-8";
        } else if (decoded.endsWith(".css")) {
            type = "text/css; charset=UTF-8";
        } else if (decoded.endsWith(".js")) {
            type = "application/javascript; charset=UTF-8";
        }

        byte[] bytes = Files.readAllBytes(file);

        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        ex.sendResponseHeaders(
            200, method.equals("HEAD") ? -1 : bytes.length
        );

        if (method.equals("GET")) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // AUTHENTICATION HELPERS
    private static User getUser(HttpExchange ex) {
        String token = cookie(ex, "SHT_SESSION");
        return token == null ? null : sessions.get(token);
    }

    private static String cookie(HttpExchange ex, String key) {
        List<String> values = ex.getRequestHeaders().get("Cookie");

        if (values == null) {
            return null;
        }

        for (String header : values) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);

                if (pair.length == 2 && pair[0].equals(key)) {
                    return pair[1];
                }
            }
        }

        return null;
    }

    // READ JSON REQUEST BODY
    private static Map<String, String> readJson(HttpExchange ex)
        throws IOException {

        Map<String, String> result = new HashMap<>();
        byte[] bytes = ex.getRequestBody().readNBytes(16385);

        if (bytes.length > 16384) {
            throw new IOException("Request too large");
        }

        String json = new String(bytes, StandardCharsets.UTF_8);

        Pattern p = Pattern.compile(
            "\"([^\"\\\\]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );

        Matcher m = p.matcher(json);

        while (m.find()) {
            result.put(m.group(1), unescape(m.group(2)));
        }

        return result;
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);

                switch (n) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    default: out.append(n);
                }
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    // PASSWORD HASHING
    private static String hashPassword(String password, String salt) {
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            Base64.getUrlDecoder().decode(salt),
            210000,
            256
        );

        try {
            SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return Base64.getEncoder().encodeToString(
                factory.generateSecret(spec).getEncoded()
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Password hashing failed", e
            );
        } finally {
            spec.clearPassword();
        }
    }

    private static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(data);
    }

    // LOAD SAVED USERS
    private static void loadUsers() throws IOException {
        if (!Files.exists(USERS)) {
            return;
        }

        for (String line :
            Files.readAllLines(USERS, StandardCharsets.UTF_8)) {

            String[] parts = line.split("\\t", -1);

            if (parts.length != 4) {
                continue;
            }

            try {
                String email = decode(parts[0]);

                User user = new User(
                    decode(parts[1]), email, parts[2], parts[3]
                );

                users.put(email, user);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed stored records.
            }
        }
    }

    // SAVE USERS
    private static void saveUsers() throws IOException {
        List<String> lines = new ArrayList<>();

        for (User u : users.values()) {
            lines.add(
                encode(u.email) + "\t" +
                encode(u.name) + "\t" +
                u.salt + "\t" +
                u.passwordHash
            );
        }

        Path temp = DATA.resolve("users.tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);

        try {
            Files.move(
                temp, USERS,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            Files.move(
                temp, USERS,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    // ACTIVITY LOG: KEEP LAST 50 ENTRIES AND SAVE TO DISK
    private static synchronized void addHistory(String message) {
        synchronized (history) {
            history.add(0, message);

            while (history.size() > 50) {
                history.remove(history.size() - 1);
            }

            try {
                saveHistory();
            } catch (IOException e) {
                System.err.println(
                    "Could not save activity history: " +
                    e.getMessage()
                );
            }
        }
    }

    // LOAD ACTIVITY HISTORY AT STARTUP
    private static void loadHistory() throws IOException {
        if (!Files.exists(HISTORY_FILE)) {
            return;
        }

        List<String> saved = Files.readAllLines(
            HISTORY_FILE, StandardCharsets.UTF_8
        );

        synchronized (history) {
            history.clear();

            for (String line : saved) {
                if (!line.isBlank()) {
                    history.add(line);
                }

                if (history.size() >= 50) {
                    break;
                }
            }
        }

        System.out.println(
            "Loaded " + history.size() + " activity entries."
        );
    }

    // SAVE HISTORY ATOMICALLY
    private static void saveHistory() throws IOException {
        List<String> snapshot;

        synchronized (history) {
            snapshot = new ArrayList<>(history);
        }

        Path temp = DATA.resolve("activity-history.tmp");

        Files.write(temp, snapshot, StandardCharsets.UTF_8);

        try {
            Files.move(
                temp, HISTORY_FILE,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            Files.move(
                temp, HISTORY_FILE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(
            s.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decode(String s) {
        return new String(
            Base64.getDecoder().decode(s),
            StandardCharsets.UTF_8
        );
    }

    private static Device findDevice(String id) {
        for (Device d : devices) {
            if (d.id.equals(id)) {
                return d;
            }
        }

        return null;
    }

    // CALCULATE CURRENT POWER
    private static int getCurrentPower() {
        int totalPower = 0;

        synchronized (devices) {
            for (Device d : devices) {
                if (d.on) {
                    totalPower += d.power;
                }
            }
        }

        return totalPower;
    }

    private static boolean isPowerLimitExceeded() {
        return getCurrentPower() > SAFETY_POWER_LIMIT_WATTS;
    }

    // RECORD SAFETY EVENTS ONLY WHEN STATUS CHANGES
    private static void updateSafetyStatus() {
        boolean exceeded = isPowerLimitExceeded();

        if (exceeded && !safetyAlertActive) {
            safetyAlertActive = true;

            addHistory(
                "SAFETY WARNING: Combined appliance load is " +
                getCurrentPower() + " W, above the demo limit of " +
                SAFETY_POWER_LIMIT_WATTS + " W."
            );

        } else if (!exceeded && safetyAlertActive) {
            safetyAlertActive = false;

            addHistory(
                "Safety status normal: appliance load is within the demo limit."
            );
        }
    }

    // RETURN DEVICE STATE, HISTORY AND SAFETY INFORMATION
    private static String stateJson() {
        StringBuilder out = new StringBuilder("{\"devices\":[");

        synchronized (devices) {
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);

                if (i > 0) {
                    out.append(",");
                }

                out.append("{")
                    .append("\"id\":\"").append(escape(d.id)).append("\",")
                    .append("\"name\":\"").append(escape(d.name)).append("\",")
                    .append("\"room\":\"").append(escape(d.room)).append("\",")
                    .append("\"power\":").append(d.power).append(",")
                    .append("\"on\":").append(d.on)
                    .append("}");
            }
        }

        int currentPower = getCurrentPower();
        boolean warning = currentPower > SAFETY_POWER_LIMIT_WATTS;

        out.append("],\"history\":[");

        synchronized (history) {
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) {
                    out.append(",");
                }

                out.append("{\"message\":\"")
                    .append(escape(history.get(i)))
                    .append("\"}");
            }
        }

        out.append("],")
            .append("\"currentPower\":").append(currentPower).append(",")
            .append("\"safetyLimit\":").append(SAFETY_POWER_LIMIT_WATTS).append(",")
            .append("\"safetyWarning\":").append(warning)
            .append("}");

        return out.toString();
    }

    private static String userJson(User u) {
        return "{\"name\":\"" + escape(u.name) +
            "\",\"email\":\"" + escape(u.email) + "\"}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static void json(
        HttpExchange ex, int status, String content
    ) throws IOException {

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );
        ex.getResponseHeaders().set("Cache-Control", "no-store");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}