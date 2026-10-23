
package smarthome;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class SmartHomeTwin extends JFrame {
    private static final Color BG = new Color(244, 247, 252);
    private static final Color BLUE = new Color(43, 96, 185);
    private static final Color GREEN = new Color(28, 145, 90);
    private static final Color RED = new Color(181, 63, 63);
    private static final Color ORANGE = new Color(202, 122, 26);
    private static final int DAILY_HOURS = 6;
    private static final double TARIFF = 8.0;
    private static final int HIGH_POWER_LIMIT = 1000;
    private static final int PBKDF2_ITERATIONS = 210000;

    private static final Path DATA_DIR = Paths.get(
        System.getProperty("user.home"), ".smart-home-twin"
    );
    private static final Path USERS_FILE = DATA_DIR.resolve("users.txt");
    private static final Path LOG_FILE = DATA_DIR.resolve("activity.log");
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String currentUser;
    private final List<Appliance> appliances = new ArrayList<>();
    private JPanel root;
    private JTextArea activityLog;
    private JLabel powerValue, energyValue, statusValue;
    private boolean detailedMode = false;

    private static class Appliance {
        final String name;
        final String room;
        final int watts;
        boolean on;

        Appliance(String name, String room, int watts) {
            this.name = name;
            this.room = room;
            this.watts = watts;
        }
    }

    public SmartHomeTwin() {
        super("Smart Home Twin");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(850, 650));
        setSize(1120, 820);
        setLocationRelativeTo(null);

        appliances.add(new Appliance("Living Room Light", "Living Room", 12));
        appliances.add(new Appliance("Bedroom Fan", "Bedroom", 60));
        appliances.add(new Appliance("Air Conditioner", "Bedroom", 1500));
        appliances.add(new Appliance("Television", "Living Room", 100));
        appliances.add(new Appliance("Kitchen Light", "Kitchen", 10));
        appliances.add(new Appliance("Refrigerator", "Kitchen", 180));

        showAuth();
    }

    // -------------------- LOGIN / SIGNUP --------------------

    private void showAuth() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BG);

        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(30, 34, 30, 34));
        card.setPreferredSize(new Dimension(440, 470));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = heading("Smart Home Twin", 28);
        JLabel subtitle = new JLabel("Manage your home in one place");
        subtitle.setForeground(Color.GRAY);

        JTextField email = new JTextField();
        JPasswordField password = new JPasswordField();
        styleField(email);
        styleField(password);

        JButton login = button("Log In", BLUE);
        JButton signup = button("Create Account", GREEN);

        card.add(title);
        card.add(Box.createVerticalStrut(7));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(28));
        card.add(label("Email address"));
        card.add(email);
        card.add(Box.createVerticalStrut(14));
        card.add(label("Password"));
        card.add(password);
        card.add(Box.createVerticalStrut(22));
        card.add(login);
        card.add(Box.createVerticalStrut(10));
        card.add(signup);
        card.add(Box.createVerticalStrut(18));

        JLabel note = new JLabel(
            "<html><center>New here? Create an account.<br>"
            + "Account data is stored locally on this computer.</center></html>"
        );
        note.setAlignmentX(Component.CENTER_ALIGNMENT);
        note.setForeground(Color.GRAY);
        card.add(note);

        login.addActionListener(e -> {
            String mail = normalizeEmail(email.getText());
            char[] pass = password.getPassword();
            try {
                if (authenticate(mail, pass)) {
                    currentUser = mail;
                    log("User logged in: " + mail);
                    showDashboard();
                } else {
                    message("Invalid email or password.", "Login failed",
                        JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                message("Login failed: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(pass, '\0');
                password.setText("");
            }
        });

        signup.addActionListener(e -> showSignup());

        page.add(card);
        setContentPane(page);
        revalidate();
        repaint();
    }

    private void showSignup() {
        JTextField nameField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        Object[] form = {
            "Full name (2–60 characters):", nameField,
            "Email address:", emailField,
            "Password (8–128 characters):", passwordField,
            "Confirm password:", confirmField
        };

        int result = JOptionPane.showConfirmDialog(
            this, form, "Create Account",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        String mail = normalizeEmail(emailField.getText());
        char[] pass = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();

        try {
            if (name.length() < 2 || name.length() > 60) {
                message("Name must contain 2–60 characters.",
                    "Invalid name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!mail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                message("Enter a valid email address.",
                    "Invalid email", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (pass.length < 8 || pass.length > 128) {
                message("Password must contain 8–128 characters.",
                    "Invalid password", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!Arrays.equals(pass, confirm)) {
                message("Passwords do not match.",
                    "Signup failed", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Files.createDirectories(DATA_DIR);
            Map<String, UserRecord> users = readUsers();

            if (users.containsKey(mail)) {
                message("An account with this email already exists.",
                    "Account exists", JOptionPane.WARNING_MESSAGE);
                return;
            }

            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String hash = hashPassword(pass, salt);

            String encodedName = Base64.getEncoder().encodeToString(
                name.getBytes(StandardCharsets.UTF_8)
            );
            String encodedSalt = Base64.getEncoder().encodeToString(salt);

            String line = encode(mail) + "|" + encodedName + "|"
                + encodedSalt + ":" + hash + System.lineSeparator();

            Files.write(USERS_FILE, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log("Account created: " + mail);
            message("Account created! You can now log in.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            message("Could not create account: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(pass, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private static class UserRecord {
        final String name;
        final byte[] salt;
        final byte[] hash;

        UserRecord(String name, byte[] salt, byte[] hash) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
        }
    }

    private Map<String, UserRecord> readUsers() throws IOException {
        Map<String, UserRecord> users = new HashMap<>();
        if (!Files.exists(USERS_FILE)) return users;

        for (String line : Files.readAllLines(
                USERS_FILE, StandardCharsets.UTF_8)) {
            try {
                String[] fields = line.split("\\|", -1);
                if (fields.length != 3) continue;

                String mail = decode(fields[0]).toLowerCase(Locale.ROOT);
                String name = decode(fields[1]);
                String[] credentials = fields[2].split(":", -1);
                if (credentials.length != 2) continue;

                byte[] salt = Base64.getDecoder().decode(credentials[0]);
                byte[] hash = Base64.getDecoder().decode(credentials[1]);
                if (salt.length < 16 || hash.length != 32) continue;

                users.put(mail, new UserRecord(name, salt, hash));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed account records.
            }
        }
        return users;
    }

    private boolean authenticate(String mail, char[] password)
            throws Exception {
        if (mail.isEmpty() || password.length == 0) return false;
        UserRecord user = readUsers().get(mail);
        if (user == null) return false;

        byte[] candidate = Base64.getDecoder().decode(
            hashPassword(password, user.salt)
        );
        return MessageDigest.isEqual(user.hash, candidate);
    }

    private String hashPassword(char[] password, byte[] salt)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
            password, salt, PBKDF2_ITERATIONS, 256
        );
        try {
            byte[] hash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(
            text.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decode(String text) {
        return new String(Base64.getDecoder().decode(text),
            StandardCharsets.UTF_8);
    }

    private static String normalizeEmail(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    // -------------------- DASHBOARD --------------------

    private void showDashboard() {
        root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(heading("Smart Home Twin", 28));

        JLabel welcome = new JLabel("Signed in as " + currentUser);
        welcome.setForeground(Color.GRAY);
        titles.add(welcome);

        JButton logout = button("Log Out", new Color(96, 101, 112));
        logout.addActionListener(e -> {
            log("User logged out: " + currentUser);
            currentUser = null;
            showAuth();
        });

        header.add(titles, BorderLayout.WEST);
        header.add(logout, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);

        JPanel summary = new JPanel(new GridLayout(1, 3, 12, 12));
        summary.setOpaque(false);
        powerValue = new JLabel();
        energyValue = new JLabel();
        statusValue = new JLabel();

        summary.add(summaryCard("Current power", powerValue));
        summary.add(summaryCard("Estimated daily energy / bill", energyValue));
        summary.add(summaryCard("Appliance status", statusValue));

        JPanel applianceGrid = new JPanel(new GridLayout(0, 3, 12, 12));
        applianceGrid.setOpaque(false);

        for (Appliance appliance : appliances) {
            applianceGrid.add(applianceCard(appliance));
        }

        JScrollPane scroll = new JScrollPane(applianceGrid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        center.add(summary, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        activityLog = new JTextArea(5, 40);
        activityLog.setEditable(false);
        activityLog.setLineWrap(true);
        activityLog.setWrapStyleWord(true);
        activityLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        activityLog.setText(loadActivityLog());
        activityLog.setCaretPosition(activityLog.getDocument().getLength());

        JScrollPane logScroll = new JScrollPane(activityLog);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity history"));

        JPanel actions = new JPanel(new GridLayout(2, 3, 8, 8));
        actions.setOpaque(false);

        JButton allOff = button("Turn All Off", RED);
        JButton safety = button("Safety Simulator", ORANGE);
        JButton lab = button("COA / DAA / FLAT Lab", BLUE);
        JButton optimizer = button("Energy Optimizer", GREEN);
        JButton details = button("Toggle Power Details", new Color(96, 101, 112));
        JButton reset = button("Reset Demo States", new Color(96, 101, 112));

        allOff.addActionListener(e -> turnAllOff());
        safety.addActionListener(e -> simulateSafety());
        lab.addActionListener(e -> showLearningLab());
        optimizer.addActionListener(e -> optimizeEnergy());

        details.addActionListener(e -> {
            detailedMode = !detailedMode;
            log("Power details " + (detailedMode ? "enabled" : "disabled"));
            showDashboard();
        });

        reset.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(
                this, "Reset all simulated appliances to OFF?",
                "Reset demo", JOptionPane.YES_NO_OPTION
            );
            if (answer == JOptionPane.YES_OPTION) {
                for (Appliance a : appliances) a.on = false;
                log("All simulated appliance states reset to OFF.");
                showDashboard();
            }
        });

        actions.add(allOff);
        actions.add(safety);
        actions.add(lab);
        actions.add(optimizer);
        actions.add(details);
        actions.add(reset);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        bottom.add(logScroll, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        refreshSummary();
        revalidate();
        repaint();
    }

    private JPanel applianceCard(Appliance a) {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(215, 222, 234)),
            new EmptyBorder(13, 13, 13, 13)
        ));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel name = heading(a.name, 16);
        JLabel room = new JLabel(a.room);
        room.setForeground(Color.GRAY);

        JLabel state = new JLabel(a.on ? "ON" : "OFF");
        state.setFont(new Font("Arial", Font.BOLD, 15));
        state.setForeground(a.on ? GREEN : Color.GRAY);

        int shownWatts = a.on ? a.watts : 0;
        JLabel power = new JLabel(detailedMode
            ? "Rated: " + a.watts + " W | Current: " + shownWatts + " W"
            : "Power: " + shownWatts + " W");

        JButton toggle = button(a.on ? "Turn OFF" : "Turn ON",
            a.on ? RED : BLUE);

        toggle.addActionListener(e -> {
            a.on = !a.on;
            log(a.name + " turned " + (a.on ? "ON" : "OFF")
                + " (" + (a.on ? a.watts : 0) + " W)");
            showDashboard();
            checkHighPower();
        });

        card.add(name);
        card.add(Box.createVerticalStrut(5));
        card.add(room);
        card.add(Box.createVerticalStrut(10));
        card.add(state);
        card.add(Box.createVerticalStrut(5));
        card.add(power);
        card.add(Box.createVerticalStrut(12));
        card.add(toggle);
        return card;
    }

    private JPanel summaryCard(String title, JLabel value) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel heading = new JLabel(title);
        heading.setForeground(Color.GRAY);
        value.setFont(new Font("Arial", Font.BOLD, 17));

        panel.add(heading);
        panel.add(value);
        return panel;
    }

    private int currentWatts() {
        return appliances.stream()
            .filter(a -> a.on)
            .mapToInt(a -> a.watts)
            .sum();
    }

    private long activeCount() {
        return appliances.stream().filter(a -> a.on).count();
    }

    private void refreshSummary() {
        int watts = currentWatts();
        double dailyKwh = watts * DAILY_HOURS / 1000.0;
        double estimatedBill = dailyKwh * TARIFF;

        powerValue.setText(watts + " W");
        energyValue.setText(String.format(Locale.US,
            "%.2f kWh / ₹%.2f", dailyKwh, estimatedBill));
        statusValue.setText(activeCount() + " / " + appliances.size() + " ON");

        energyValue.setToolTipText(
            "Assumes " + DAILY_HOURS + " hours/day for current load, tariff ₹"
            + TARIFF + "/kWh. Illustrative estimate, not actual meter data."
        );
    }

    // -------------------- ACTIVITY HISTORY --------------------

    private String loadActivityLog() {
        try {
            Files.createDirectories(DATA_DIR);
            if (!Files.exists(LOG_FILE)) {
                return "Activity history ready.\n";
            }

            List<String> lines = Files.readAllLines(
                LOG_FILE, StandardCharsets.UTF_8
            );

            // Keep the latest 500 entries in the UI.
            int start = Math.max(0, lines.size() - 500);
            StringBuilder out = new StringBuilder();
            for (int i = start; i < lines.size(); i++) {
                out.append(lines.get(i)).append('\n');
            }
            return out.length() == 0
                ? "Activity history ready.\n" : out.toString();
        } catch (IOException ex) {
            return "Could not load activity history.\n";
        }
    }

    private void log(String text) {
        String entry = "[" + LocalDateTime.now().format(TIME_FORMAT)
            + "] " + text;

        try {
            Files.createDirectories(DATA_DIR);
            Files.write(LOG_FILE,
                (entry + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (activityLog != null) {
                activityLog.append(entry + "\n");
                activityLog.setCaretPosition(
                    activityLog.getDocument().getLength()
                );
            }
        } catch (IOException ex) {
            System.err.println("Could not save activity log: " + ex.getMessage());
        }
    }

    // -------------------- ENERGY AND SAFETY --------------------

    private void turnAllOff() {
        int answer = JOptionPane.showConfirmDialog(
            this, "Turn off all simulated appliances?",
            "Confirm", JOptionPane.YES_NO_OPTION
        );
        if (answer != JOptionPane.YES_OPTION) return;

        for (Appliance a : appliances) a.on = false;
        log("All simulated appliances turned OFF.");
        showDashboard();
    }

    private void checkHighPower() {
        int watts = currentWatts();
        if (watts > HIGH_POWER_LIMIT) {
            log("High simulated load detected: " + watts + " W");
            message("High simulated power usage: " + watts + " W.\n"
                + "Demo threshold: " + HIGH_POWER_LIMIT + " W.\n"
                + "Consider switching off unused appliances.\n\n"
                + "This is not a real electrical safety detector.",
                "High power warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void simulateSafety() {
        String[] choices = {
            "Simulated smoke alert",
            "Simulated overheating alert",
            "Check high power usage"
        };

        String choice = (String) JOptionPane.showInputDialog(
            this, "Choose a demonstration event:", "Safety Simulator",
            JOptionPane.WARNING_MESSAGE, null, choices, choices[0]
        );
        if (choice == null) return;

        log("Safety simulation selected: " + choice);

        if (choice.equals(choices[2])) {
            int watts = currentWatts();
            if (watts > HIGH_POWER_LIMIT) {
                message("HIGH POWER USAGE: " + watts + " W.\n"
                    + "Demo threshold: " + HIGH_POWER_LIMIT + " W.",
                    "Power warning", JOptionPane.WARNING_MESSAGE);
            } else {
                message("Current simulated load: " + watts + " W.\n"
                    + "Below demo threshold: " + HIGH_POWER_LIMIT + " W.",
                    "Power check", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            message(choice + ".\n\nThis is only a simulation. "
                + "No physical sensor is connected.",
                "Safety demo", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void optimizeEnergy() {
        Appliance largest = appliances.stream()
            .filter(a -> a.on)
            .max(Comparator.comparingInt(a -> a.watts))
            .orElse(null);

        if (largest == null) {
            message("Turn on an appliance first to test the optimizer.",
                "Energy Optimizer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
            "Largest active load: " + largest.name + " (" + largest.watts
            + " W).\nTurn it off in this simulation?",
            "Energy Optimizer", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            largest.on = false;
            log("Energy optimizer turned OFF " + largest.name);
            showDashboard();
        }
    }

    // -------------------- COA / DAA / FLAT --------------------

    private void showLearningLab() {
        String[] topics = {
            "COA - Binary and hexadecimal",
            "DAA - Rank appliances by power",
            "FLAT - Finite-state machine"
        };

        String choice = (String) JOptionPane.showInputDialog(
            this, "Choose a learning module:", "Learning Lab",
            JOptionPane.PLAIN_MESSAGE, null, topics, topics[0]
        );
        if (choice == null) return;

        if (choice.startsWith("COA")) {
            runCOA();
        } else if (choice.startsWith("DAA")) {
            runDAA();
        } else {
            runFLAT();
        }
    }

    private void runCOA() {
        String input = JOptionPane.showInputDialog(
            this, "Enter a decimal integer from 0 to 65535:"
        );
        if (input == null) return;

        try {
            int number = Integer.parseInt(input.trim());
            if (number < 0 || number > 65535) {
                throw new NumberFormatException();
            }

            String binary = String.format("%16s",
                Integer.toBinaryString(number)).replace(' ', '0');
            String hex = Integer.toHexString(number)
                .toUpperCase(Locale.ROOT);

            message("Decimal: " + number
                + "\n16-bit binary: " + binary
                + "\nHexadecimal: 0x" + hex
                + "\n\nCOA connection: binary represents bit patterns; "
                + "hexadecimal is a compact way to write them.",
                "COA Number Representation", JOptionPane.INFORMATION_MESSAGE);
            log("COA conversion completed for decimal " + number);
        } catch (NumberFormatException ex) {
            message("Enter a whole number from 0 to 65535.",
                "Invalid input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runDAA() {
        List<Appliance> sorted = new ArrayList<>(appliances);
        sorted.sort(Comparator.comparingInt(
            (Appliance a) -> a.watts).reversed());

        StringBuilder out = new StringBuilder(
            "Appliances ranked by rated power:\n\n");
        for (int i = 0; i < sorted.size(); i++) {
            Appliance a = sorted.get(i);
            out.append(i + 1).append(". ").append(a.name)
                .append(" — ").append(a.watts).append(" W")
                .append(a.on ? " (ON)" : " (OFF)").append('\n');
        }

        out.append("\nAlgorithm: comparison-based sorting\n")
            .append("Time complexity: O(n log n) for Java's list sort.\n")
            .append("Space: O(n) for the copied list.");

        message(out.toString(), "DAA Power Ranking",
            JOptionPane.INFORMATION_MESSAGE);
        log("DAA appliance ranking displayed.");
    }

    private void runFLAT() {
        String[] states = {"OFF", "ON"};
        String from = (String) JOptionPane.showInputDialog(
            this, "Choose current state:", "FLAT State Machine",
            JOptionPane.PLAIN_MESSAGE, null, states, states[0]);
        if (from == null) return;

        String[] actions = {"Turn ON", "Turn OFF", "Safety shutdown"};
        String action = (String) JOptionPane.showInputDialog(
            this, "Choose an action:", "FLAT State Machine",
            JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        if (action == null) return;

        String next;
        if (action.equals("Safety shutdown") || action.equals("Turn OFF")) {
            next = "OFF";
        } else {
            next = "ON";
        }

        boolean valid = !(
            (from.equals("OFF") && action.equals("Turn OFF"))
            || (from.equals("ON") && action.equals("Turn ON"))
        );

        String explanation = valid
            ? "Transition accepted by the demo rules."
            : "Redundant action: appliance is already in that state.";

        message("Current state: " + from
            + "\nInput/action: " + action
            + "\nNext state: " + next
            + "\nResult: " + explanation
            + "\n\nFSM states: {OFF, ON}\n"
            + "This is an educational finite-state-machine demo.",
            "FLAT State Machine", JOptionPane.INFORMATION_MESSAGE);

        log("FLAT demo: " + from + " + " + action + " -> " + next);
    }

    // -------------------- UI HELPERS --------------------

    private JLabel heading(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, size));
        return label;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("Arial", Font.PLAIN, 15));
        field.setPreferredSize(new Dimension(330, 38));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setFont(new Font("Arial", Font.BOLD, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(10, 12, 10, 12));
        return b;
    }

    private void message(String text, String title, int type) {
        JOptionPane.showMessageDialog(this, text, title, type);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
                // Use Java's default look and feel.
            }
            new SmartHomeTwin().setVisible(true);
        });
    }
}
