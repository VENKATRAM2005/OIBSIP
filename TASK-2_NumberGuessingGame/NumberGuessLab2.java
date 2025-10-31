/* NumberGuessLab2.java
 *
 * Stable, polished Number Guessing Game (single-file)
 * - Swing UI with Light/Dark theme toggle
 * - Difficulty levels, rounds, attempts, per-round timer
 * - Robust engine: fresh random secret per round, deterministic flow
 * - Give Up, Next Round, Timer expiry handled correctly
 * - Leaderboard persisted to 'task2_leaderboard.csv'
 *
 * Compile:
 *   javac NumberGuessLab2.java
 * Run:
 *   java NumberGuessLab2
 *
 * Works with JDK 8+
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;

public class NumberGuessLab2 {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NGGameUI().showUI());
    }
}

/* ---------------------------
   UI + Controller
   --------------------------- */
class NGGameUI {
    private final JFrame frame = new JFrame("Venkat's Guess Lab — Stable Edition");
    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainPanel = new JPanel(mainCards);

    // Home controls
    private JTextField playerNameField;
    private JComboBox<String> difficultyBox;
    private JSpinner roundsSpinner;
    private JSpinner attemptsSpinner;
    private JSpinner roundTimerSpinner; // seconds per round
    private JCheckBox themeToggle;

    // Game controls
    private JLabel roundInfoLabel;
    private JLabel rangeLabel;
    private JLabel attemptsLabel;
    private JLabel timerLabel;
    private JLabel hintLabel;
    private JTextField guessField;
    private JButton submitButton;
    private JButton giveUpButton;
    private JButton nextRoundButton;
    private JTextArea historyArea;
    private JProgressBar attemptProgress;
    private JLabel roundScoreLabel;
    private JLabel totalScoreLabel;

    // Leaderboard
    private JTable lbTable;
    private DefaultTableModel lbModel;
    private static final Path LEADERBOARD_FILE = Paths.get("task2_leaderboard.csv");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Theme
    private Theme currentTheme = Theme.DARK;

    // Engine
    private NGEngine engine;

    NGGameUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(920, 600);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(mainPanel);

        mainPanel.add(buildHomePanel(), "home");
        mainPanel.add(buildGamePanel(), "game");
        mainPanel.add(buildLeaderboardPanel(), "leaderboard");

        frame.setJMenuBar(buildMenuBar());
    }

    void showUI() {
        applyTheme();
        mainCards.show(mainPanel, "home");
        frame.setVisible(true);
    }

    /* --------- Build UI Panels --------- */
    private JPanel buildHomePanel() {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Venkat's Number Guess Lab");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        p.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(new CompoundBorder(new LineBorder(Color.LIGHT_GRAY, 1, true), new EmptyBorder(12, 12, 12, 12)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; center.add(new JLabel("Player name:"), c);
        playerNameField = new JTextField(System.getProperty("user.name", "Player"), 18);
        c.gridx = 1; center.add(playerNameField, c);

        c.gridx = 0; c.gridy = 1; center.add(new JLabel("Difficulty:"), c);
        difficultyBox = new JComboBox<>(new String[] {"Easy (1-50)", "Medium (1-100)", "Hard (1-500)"});
        difficultyBox.setSelectedIndex(1);
        c.gridx = 1; center.add(difficultyBox, c);

        c.gridx = 0; c.gridy = 2; center.add(new JLabel("Rounds:"), c);
        roundsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        c.gridx = 1; center.add(roundsSpinner, c);

        c.gridx = 0; c.gridy = 3; center.add(new JLabel("Attempts per round:"), c);
        attemptsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 100, 1));
        c.gridx = 1; center.add(attemptsSpinner, c);

        c.gridx = 0; c.gridy = 4; center.add(new JLabel("Seconds per round (timer):"), c);
        roundTimerSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 600, 5));
        c.gridx = 1; center.add(roundTimerSpinner, c);

        c.gridx = 0; c.gridy = 5; center.add(new JLabel("Theme:"), c);
        themeToggle = new JCheckBox("Light Theme (uncheck for Dark)");
        themeToggle.setSelected(false);
        themeToggle.addActionListener(e -> {
            currentTheme = themeToggle.isSelected() ? Theme.LIGHT : Theme.DARK;
            applyTheme();
        });
        c.gridx = 1; center.add(themeToggle, c);

        JPanel right = new JPanel(new GridLayout(4,1,10,10));
        JButton startBtn = new JButton("Start Game");
        startBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startBtn.addActionListener(e -> startGame());
        JButton viewLB = new JButton("View Leaderboard");
        viewLB.addActionListener(e -> {
            loadLeaderboard();
            mainCards.show(mainPanel, "leaderboard");
        });
        right.add(startBtn); right.add(viewLB);

        p.add(center, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel buildGamePanel() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top bar
        JPanel top = new JPanel(new BorderLayout());
        roundInfoLabel = new JLabel("Round 0 / 0");
        roundInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        top.add(roundInfoLabel, BorderLayout.WEST);

        timerLabel = new JLabel("Time: 00:00");
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        top.add(timerLabel, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);

        // Center: main input + hints
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(8,8,8,8);
        c.gridx=0; c.gridy=0; c.gridwidth=2;
        rangeLabel = new JLabel("Range: - ");
        rangeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        center.add(rangeLabel, c);

        c.gridy=1; hintLabel(center); // sets the field hintLabel

        c.gridy=2; c.gridwidth=1; center.add(new JLabel("Your guess:"), c);
        guessField = new JTextField(10); guessField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        c.gridx=1; center.add(guessField, c);

        c.gridx=0; c.gridy=3;
        submitButton = new JButton("Submit Guess");
        submitButton.addActionListener(e -> onSubmitGuess());
        center.add(submitButton, c);

        nextRoundButton = new JButton("Next Round");
        nextRoundButton.setEnabled(false);
        nextRoundButton.addActionListener(e -> onNextRound());
        c.gridx=1; center.add(nextRoundButton, c);

        p.add(center, BorderLayout.CENTER);

        // Right: history + progress
        JPanel right = new JPanel(new BorderLayout(6,6));
        right.setPreferredSize(new Dimension(340, 0));
        right.setBorder(new CompoundBorder(new LineBorder(Color.LIGHT_GRAY,1,true), new EmptyBorder(8,8,8,8)));
        right.add(new JLabel("Round History"), BorderLayout.NORTH);
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        JPanel progressPanel = new JPanel(new GridLayout(4,1,6,6));
        attemptsLabel = new JLabel("Attempts: 0 / 0");
        attemptProgress = new JProgressBar(0, 100);
        roundScoreLabel = new JLabel("Round Score: 0");
        totalScoreLabel = new JLabel("Total Score: 0");
        progressPanel.add(attemptsLabel); progressPanel.add(attemptProgress);
        progressPanel.add(roundScoreLabel); progressPanel.add(totalScoreLabel);
        right.add(progressPanel, BorderLayout.SOUTH);

        p.add(right, BorderLayout.EAST);

        // Bottom: actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        giveUpButton = new JButton("Give Up");
        giveUpButton.addActionListener(e -> onGiveUp());
        JButton quitBtn = new JButton("Quit to Home");
        quitBtn.addActionListener(e -> {
            int conf = JOptionPane.showConfirmDialog(frame, "Quit current game and return to home? Progress will be lost.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                if (engine != null) engine.stopTimers();
                mainCards.show(mainPanel, "home");
            }
        });
        bottom.add(giveUpButton); bottom.add(quitBtn);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private void hintLabel(JPanel container) {
        JPanel hintP = new JPanel(new BorderLayout());
        hintP.setOpaque(false);
        JLabel lbl = new JLabel("Hint: ");
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        hintP.add(lbl, BorderLayout.WEST);
        hintP.add(Box.createHorizontalStrut(8), BorderLayout.CENTER);
        JLabel val = new JLabel("Ready.");
        val.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        hintP.add(val, BorderLayout.EAST);
        // store in a field for updates
        hintLabel = val;
        container.add(hintP);
    }

    private JPanel buildLeaderboardPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(12,12,12,12));
        JLabel t = new JLabel("Leaderboard");
        t.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(t, BorderLayout.NORTH);

        lbModel = new DefaultTableModel(new Object[]{"Rank","Name","Score","Date"}, 0){
            public boolean isCellEditable(int r,int c){return false;}
        };
        lbTable = new JTable(lbModel);
        lbTable.setRowHeight(24);
        p.add(new JScrollPane(lbTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> mainCards.show(mainPanel, "home"));
        bottom.add(back);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu app = new JMenu("App");
        JMenuItem home = new JMenuItem("Home"); home.addActionListener(e -> mainCards.show(mainPanel, "home"));
        JMenuItem leaderboard = new JMenuItem("Leaderboard"); leaderboard.addActionListener(e -> { loadLeaderboard(); mainCards.show(mainPanel,"leaderboard");});
        JMenuItem exit = new JMenuItem("Exit"); exit.addActionListener(e -> System.exit(0));
        app.add(home); app.add(leaderboard); app.addSeparator(); app.add(exit);
        mb.add(app);
        return mb;
    }

    /* -------------------------
       Theme application
       ------------------------- */
    private void applyTheme() {
        Color bg = currentTheme == Theme.DARK ? new Color(34, 36, 40) : Color.WHITE;
        Color panel = currentTheme == Theme.DARK ? new Color(44, 46, 50) : new Color(245,245,245);
        Color fg = currentTheme == Theme.DARK ? Color.WHITE : Color.DARK_GRAY;
        Color accent = currentTheme == Theme.DARK ? new Color(80,160,255) : new Color(20,110,230);

        frame.getContentPane().setBackground(bg);
        for (Component comp : getAllComponents(frame.getContentPane())) {
            styleComponent(comp, bg, panel, fg, accent);
        }
        SwingUtilities.updateComponentTreeUI(frame);
    }

    private void styleComponent(Component comp, Color bg, Color panel, Color fg, Color accent) {
        if (comp instanceof JPanel) {
            comp.setBackground(panel);
        } else if (comp instanceof JLabel) {
            comp.setForeground(fg);
        } else if (comp instanceof JButton) {
            JButton b = (JButton) comp;
            b.setBackground(accent);
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setBorder(new EmptyBorder(6,10,6,10));
        } else if (comp instanceof JTextField || comp instanceof JTextArea || comp instanceof JTable) {
            comp.setBackground(currentTheme == Theme.DARK ? new Color(28,28,30) : Color.WHITE);
            comp.setForeground(fg);
            if (comp instanceof JTextArea) ((JTextArea)comp).setBorder(new EmptyBorder(6,6,6,6));
        } else if (comp instanceof JSpinner) {
            comp.setBackground(currentTheme == Theme.DARK ? new Color(46,46,48) : Color.WHITE);
            comp.setForeground(fg);
        }
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) styleComponent(c, bg, panel, fg, accent);
        }
    }

    private List<Component> getAllComponents(Container c) {
        List<Component> list = new ArrayList<>();
        for (Component comp : c.getComponents()) {
            list.add(comp);
            if (comp instanceof Container) list.addAll(getAllComponents((Container)comp));
        }
        return list;
    }

    /* -------------------------
       Game lifecycle handlers
       ------------------------- */
    private void startGame() {
        String player = playerNameField.getText().trim();
        if (player.length() < 2) { JOptionPane.showMessageDialog(frame, "Please enter your name (min 2 characters)."); return; }
        int diffIdx = difficultyBox.getSelectedIndex();
        int[] rng = difficultyRange(diffIdx);
        int rounds = (Integer) roundsSpinner.getValue();
        int attempts = (Integer) attemptsSpinner.getValue();
        int seconds = (Integer) roundTimerSpinner.getValue();
        engine = new NGEngine(player, rng[0], rng[1], rounds, attempts, seconds);
        engine.startRound(); // create first round
        updateGameUIForRound();
        mainCards.show(mainPanel, "game");
        startRoundTimer();
    }

    private int[] difficultyRange(int idx) {
        switch (idx) {
            case 0: return new int[]{1,50};
            case 2: return new int[]{1,500};
            default: return new int[]{1,100};
        }
    }

    private void updateGameUIForRound() {
        if (engine == null) return;
        roundInfoLabel.setText(String.format("Round %d / %d", engine.getCurrentRoundIndex()+1, engine.getTotalRounds()));
        rangeLabel.setText(String.format("Range: %d — %d", engine.getMin(), engine.getMax()));
        attemptsLabel.setText(String.format("Attempts: %d / %d", engine.getAttemptsUsed(), engine.getMaxAttempts()));
        attemptProgress.setValue((int)(engine.getAttemptsUsed() * 100.0 / engine.getMaxAttempts()));
        roundScoreLabel.setText("Round Score: " + engine.getCurrentRoundScore());
        totalScoreLabel.setText("Total Score: " + engine.getTotalScore());
        historyArea.setText(engine.getRoundHistoryText());
        timerLabel.setText("Time: " + formatSeconds(engine.getRemainingSeconds()));
        submitButton.setEnabled(!engine.isRoundFinished());
        nextRoundButton.setEnabled(engine.isRoundFinished() && engine.hasNextRound());
        giveUpButton.setEnabled(!engine.isRoundFinished());
        guessField.requestFocusInWindow();
        // update hint label to latest (if any)
        hintLabel.setText(engine.getLatestHint());
    }

    private void onSubmitGuess() {
        if (engine == null) return;
        String txt = guessField.getText().trim();
        if (txt.isEmpty()) { JOptionPane.showMessageDialog(frame, "Type a number to guess."); return; }
        int value;
        try { value = Integer.parseInt(txt); }
        catch (NumberFormatException e) { JOptionPane.showMessageDialog(frame, "Enter a valid integer."); return; }
        NGEngine.GuessOutcome outcome = engine.submitGuess(value);
        // update UI
        updateGameUIForRound();
        // append history message
        historyArea.append(outcome.log + "\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
        // show short feedback
        if (outcome.correct) {
            JOptionPane.showMessageDialog(frame, String.format("Correct! +%d pts for this round.", outcome.pointsAwarded));
            stopRoundTimer();
            if (engine.hasNextRound()) nextRoundButton.setEnabled(true); else {
                gameOverSequence();
            }
        } else if (outcome.roundFinished) {
            JOptionPane.showMessageDialog(frame, "Attempts exhausted or round finished. Secret: " + outcome.secret);
            stopRoundTimer();
            if (engine.hasNextRound()) nextRoundButton.setEnabled(true); else gameOverSequence();
        } else {
            // update hint label to show the hint
            hintLabel.setText(outcome.log);
        }
    }

    private void onGiveUp() {
        if (engine == null) return;
        int conf = JOptionPane.showConfirmDialog(frame, "Give up this round? It will count as 0 points for the round.", "Confirm", JOptionPane.YES_NO_OPTION);
        if (conf != JOptionPane.YES_OPTION) return;
        engine.giveUpCurrentRound();
        updateGameUIForRound();
        stopRoundTimer();
        if (engine.hasNextRound()) nextRoundButton.setEnabled(true); else gameOverSequence();
    }

    private void onNextRound() {
        if (engine == null) return;
        if (!engine.hasNextRound()) { gameOverSequence(); return; }
        engine.advanceToNextRound();
        updateGameUIForRound();
        startRoundTimer();
    }

    /* -------------------------
       Round Timer (Swing Timer)
       ------------------------- */
    private javax.swing.Timer roundTimer;
    private void startRoundTimer() {
        stopRoundTimer(); // ensure only one running
        if (engine == null) return;
        int tickDelay = 1000; // ms
        // set initial remaining to engine's per-round seconds
        engine.resetRoundTimer();
        roundTimer = new javax.swing.Timer(tickDelay, e -> {
            engine.decrementSecondsLeft();
            timerLabel.setText("Time: " + formatSeconds(engine.getRemainingSeconds()));
            if (engine.getRemainingSeconds() <= 0) {
                ((javax.swing.Timer)e.getSource()).stop();
                engine.handleRoundTimeout();
                updateGameUIForRound();
                historyArea.append("Time's up — round forfeited.\n");
                JOptionPane.showMessageDialog(frame, "Time's up for this round.");
                if (engine.hasNextRound()) nextRoundButton.setEnabled(true); else gameOverSequence();
            }
        });
        roundTimer.setInitialDelay(0);
        roundTimer.start();
    }

    private void stopRoundTimer() {
        if (roundTimer != null && roundTimer.isRunning()) {
            roundTimer.stop();
        }
    }

    /* -------------------------
       End of game and leaderboard
       ------------------------- */
    private void gameOverSequence() {
        stopRoundTimer();
        int total = engine.getTotalScore();
        String msg = String.format("Game Over!\nPlayer: %s\nTotal Score: %d\nRounds: %d", engine.getPlayerName(), total, engine.getTotalRounds());
        JOptionPane.showMessageDialog(frame, msg);
        saveLeaderboardEntry(engine.getPlayerName(), total);
        loadLeaderboard();
        mainCards.show(mainPanel, "leaderboard");
    }

    private void saveLeaderboardEntry(String name, int score) {
        try {
            String line = String.join(",", escape(name), String.valueOf(score), LocalDateTime.now().format(DATE_FMT));
            Files.write(LEADERBOARD_FILE, Collections.singletonList(line), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // ignore silently
        }
    }

    private void loadLeaderboard() {
        lbModel.setRowCount(0);
        if (!Files.exists(LEADERBOARD_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(LEADERBOARD_FILE);
            List<LEntry> entries = new ArrayList<>();
            for (String ln : lines) {
                String[] p = ln.split(",", 3);
                if (p.length >= 3) entries.add(new LEntry(unescape(p[0]), Integer.parseInt(p[1]), p[2]));
            }
            entries.sort((a,b)->Integer.compare(b.score, a.score));
            int rank = 1;
            for (LEntry e : entries) {
                lbModel.addRow(new Object[]{rank++, e.name, e.score, e.time});
            }
        } catch (IOException ex) {
            // ignore
        }
    }

    /* -------------------------
       Utilities
       ------------------------- */
    private String formatSeconds(int s) {
        int m = s / 60; int sec = s % 60; return String.format("%02d:%02d", m, sec);
    }

    private static String escape(String s) { return s.replace(",", " "); }
    private static String unescape(String s) { return s; }
}

/* ---------------------------
   Game Engine (pure logic)
   --------------------------- */
class NGEngine {
    private final String playerName;
    private final int min, max;
    private final int totalRounds;
    private final int maxAttempts;
    private final int secondsPerRound;

    private final Random rng = new Random();
    private final List<Round> rounds = new ArrayList<>();
    private int currentRoundIndex = -1;
    private int remainingSecondsThisRound = 0;

    NGEngine(String playerName, int min, int max, int totalRounds, int maxAttempts, int secondsPerRound) {
        this.playerName = playerName;
        this.min = min; this.max = max;
        this.totalRounds = totalRounds; this.maxAttempts = maxAttempts;
        this.secondsPerRound = secondsPerRound;
        for (int i=0;i<totalRounds;i++) {
            int secret = rng.nextInt(max - min + 1) + min;
            rounds.add(new Round(secret, maxAttempts));
        }
    }

    void startRound() {
        if (currentRoundIndex < 0) currentRoundIndex = 0;
        remainingSecondsThisRound = secondsPerRound;
        rounds.get(currentRoundIndex).start();
    }

    void resetRoundTimer() { remainingSecondsThisRound = secondsPerRound; }
    void decrementSecondsLeft() { remainingSecondsThisRound = Math.max(0, remainingSecondsThisRound - 1); }
    int getRemainingSeconds() { return remainingSecondsThisRound; }
    void handleRoundTimeout() { rounds.get(currentRoundIndex).forfeit(); }

    boolean hasNextRound() { return currentRoundIndex < totalRounds - 1; }
    int getCurrentRoundIndex() { return currentRoundIndex; }
    int getTotalRounds() { return totalRounds; }
    int getMin() { return min; }
    int getMax() { return max; }
    int getMaxAttempts() { return maxAttempts; }

    String getPlayerName() { return playerName; }

    int getAttemptsUsed() { return rounds.get(currentRoundIndex).attempts; }

    boolean isRoundFinished() { return rounds.get(currentRoundIndex).finished; }

    int getCurrentRoundScore() { return rounds.get(currentRoundIndex).points; }

    int getTotalScore() {
        return rounds.stream().mapToInt(r -> r.points).sum();
    }

    String getRoundHistoryText() {
        Round r = rounds.get(currentRoundIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("Secret is hidden\n");
        int i = 1;
        for (Guess g : r.history) {
            sb.append(String.format("Guess %d -> %d (%s)\n", i++, g.value, g.note));
        }
        return sb.toString();
    }

    void stopTimers() {
        // placeholder (UI timers handled in UI)
    }

    void giveUpCurrentRound() {
        rounds.get(currentRoundIndex).forfeit();
    }

    void advanceToNextRound() {
        if (hasNextRound()) {
            currentRoundIndex++;
            remainingSecondsThisRound = secondsPerRound;
            rounds.get(currentRoundIndex).start();
        }
    }

    void forfeitRound() { rounds.get(currentRoundIndex).forfeit(); }

    void startRoundExplicitly(int idx) {
        if (idx >= 0 && idx < rounds.size()) {
            currentRoundIndex = idx;
            rounds.get(currentRoundIndex).start();
            remainingSecondsThisRound = secondsPerRound;
        }
    }

    // Provide latest hint/message for UI
    String getLatestHint() {
        if (currentRoundIndex < 0 || currentRoundIndex >= rounds.size()) return "Ready";
        Round r = rounds.get(currentRoundIndex);
        if (r.history.isEmpty()) return "Ready";
        Guess last = r.history.get(r.history.size() - 1);
        if (r.finished && r.points > 0) return "Correct — round finished";
        if (r.finished) return "Round finished";
        return last.note;
    }

    private String hintForGuess(Round r, int guess) {
        int diff = Math.abs(r.secret - guess);
        if (diff == 0) return "Correct";
        if (diff <= Math.max(1, (max-min)/20)) return "Very close";
        if (diff <= Math.max(1, (max-min)/10)) return "Close";
        return diff > (max-min)/2 ? "Far" : "Moderate";
    }

    // submitGuess returns outcome object with details
    GuessOutcome submitGuess(int guess) {
        Round r = rounds.get(currentRoundIndex);
        if (r.finished) return new GuessOutcome(false, true, 0, r.secret, "Round already finished", 0);
        r.attempts++;
        String note;
        if (r.history.size() > 0) {
            int last = r.history.get(r.history.size()-1).value;
            note = Math.abs(r.secret - guess) < Math.abs(r.secret - last) ? "Warmer" : "Colder";
        } else note = "First try";

        if (guess == r.secret) {
            r.finished = true;
            int base = Math.max(100, 300 - (max-min)/2);
            int attemptPenalty = (r.attempts - 1) * 20;
            int timeBonus = Math.max(0, remainingSecondsThisRound / 2);
            r.points = Math.max(0, base - attemptPenalty + timeBonus);
            r.history.add(new Guess(guess, "Correct"));
            return new GuessOutcome(true, true, r.points, r.secret, "Correct", r.attempts);
        } else {
            r.history.add(new Guess(guess, note));
            if (r.attempts >= r.maxAttempts) {
                r.finished = true;
                r.points = 0;
                return new GuessOutcome(false, true, 0, r.secret, "Attempts exhausted", r.attempts);
            } else {
                return new GuessOutcome(false, false, 0, r.secret, (guess < r.secret ? "Higher" : "Lower") + " — " + note, r.attempts);
            }
        }
    }

    /* -------------------------
       Inner classes (Round state)
       ------------------------- */
    static class Round {
        final int secret;
        final int maxAttempts;
        int attempts = 0;
        int points = 0;
        boolean finished = false;
        List<Guess> history = new ArrayList<>();

        Round(int secret, int maxAttempts) { this.secret = secret; this.maxAttempts = maxAttempts; }
        void start() { this.finished = false; this.attempts = 0; this.points = 0; this.history.clear(); }
        void forfeit() { this.finished = true; this.points = 0; }
    }

    static class Guess {
        final int value;
        final String note;
        Guess(int value, String note) { this.value = value; this.note = note; }
    }

    static class GuessOutcome {
        final boolean correct;
        final boolean roundFinished;
        final int pointsAwarded;
        final int secret;
        final String log;
        final int attemptsUsed;
        GuessOutcome(boolean correct, boolean roundFinished, int pointsAwarded, int secret, String log, int attemptsUsed) {
            this.correct = correct; this.roundFinished = roundFinished; this.pointsAwarded = pointsAwarded; this.secret = secret; this.log = log; this.attemptsUsed = attemptsUsed;
        }
    }
}

/* ---------------------------
   Small types
   --------------------------- */
enum Theme { DARK, LIGHT; }

class LEntry { final String name; final int score; final String time; LEntry(String n,int s,String t){name=n;score=s;time=t;} }
