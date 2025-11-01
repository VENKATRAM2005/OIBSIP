/* OnlineExamApp.java
 *
 * Award-worthy, professional single-file Online Examination System (Task-4)
 * - Visible Theme Toggle Button (light / polished dark) with high-contrast palette
 * - Modern UI touches: header gradient, progress bar, styled buttons, tooltips
 * - Robust features: register/login, profile update, start/resume exam, autosave, countdown timer, auto-submit
 * - Persistent storage: examdata.dat (serialization)
 *
 * Compile:
 *   javac OnlineExamApp.java
 * Run:
 *   java OnlineExamApp
 *
 * Note: For production, use secure password storage and a database.
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

// Public class name must match filename: OnlineExamApp.java
public class OnlineExamApp {
    public static void main(String[] args) {
        // Attempt Nimbus L&F for consistency, otherwise default
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ExamService service = new ExamService("examdata.dat");
            service.seedDemoIfEmpty();
            ExamUI ui = new ExamUI(service);
            ui.show();
        });
    }

    // Utility to create short unique ids
    public static String uid(String prefix) {
        return prefix + "-" + Math.abs(new Random().nextInt());
    }
}

/* -------------------- Models -------------------- */

class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String fullName;
    private String password;
    private final java.util.Map<String, UserExamState> attempts = new HashMap<>();

    public User(String username, String fullName, String password) {
        this.username = username; this.fullName = fullName; this.password = password;
    }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public void setFullName(String s) { fullName = s; }
    public boolean checkPassword(String p) { return Objects.equals(password, p); }
    public void setPassword(String p) { password = p; }
    public java.util.Map<String, UserExamState> getAttempts() { return attempts; }
    @Override public String toString(){ return username + " (" + fullName + ")"; }
}

class Question implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, text, explanation;
    private final java.util.List<String> options;
    private final int correctIndex;
    public Question(String id, String text, java.util.List<String> options, int correctIndex, String explanation){
        this.id=id; this.text=text; this.options=options; this.correctIndex=correctIndex; this.explanation=explanation;
    }
    public String getId(){ return id; } public String getText(){ return text; }
    public java.util.List<String> getOptions(){ return options; } public int getCorrectIndex(){ return correctIndex; }
    public String getExplanation(){ return explanation; }
}

class Exam implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, title, description;
    private final int durationMinutes;
    private final java.util.List<Question> questions;
    public Exam(String id, String title, String description, int durationMinutes, java.util.List<Question> questions){
        this.id=id; this.title=title; this.description=description; this.durationMinutes=durationMinutes; this.questions=questions;
    }
    public String getId(){ return id; } public String getTitle(){ return title; } public String getDescription(){ return description; }
    public int getDurationMinutes(){ return durationMinutes; } public java.util.List<Question> getQuestions(){ return questions; }
}

class UserExamState implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String examId;
    public final java.util.Map<String,Integer> answers = new HashMap<>();
    public long timeRemainingMillis;
    public boolean submitted = false;
    public Date submittedAt = null;
    public UserExamState(String examId, long timeRemainingMillis){ this.examId = examId; this.timeRemainingMillis = timeRemainingMillis; }
}

/* -------------------- Service -------------------- */

class ExamService {
    private final File storage;
    private java.util.Map<String, User> users = new HashMap<>();
    private java.util.Map<String, Exam> exams = new LinkedHashMap<>();

    public ExamService(String path){ storage = new File(path); load(); }

    @SuppressWarnings("unchecked")
    private synchronized void load(){
        if (!storage.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storage))){
            Object obj = ois.readObject();
            if (obj instanceof java.util.Map){
                java.util.Map<String,Object> root = (java.util.Map<String,Object>) obj;
                users = (java.util.Map<String,User>) root.getOrDefault("users", new HashMap<>());
                exams = (java.util.Map<String,Exam>) root.getOrDefault("exams", new LinkedHashMap<>());
            }
        } catch (Exception ex){
            System.err.println("Failed to load data file: " + ex.getMessage());
            users = new HashMap<>(); exams = new LinkedHashMap<>();
        }
    }

    private synchronized void save(){
        java.util.Map<String,Object> root = new HashMap<>();
        root.put("users", users); root.put("exams", exams);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(storage))) { oos.writeObject(root); }
        catch (IOException e){ e.printStackTrace(); }
    }

    public synchronized void seedDemoIfEmpty(){
        if (!users.isEmpty() || !exams.isEmpty()) return;
        User demo = new User("venkat","Venkat R.","1234"); users.put(demo.getUsername(), demo);

        java.util.List<Question> qs = new ArrayList<>();
        qs.add(new Question("q1","HTTP status code for OK?", Arrays.asList("200","404","500","301"),0,"200 = OK"));
        qs.add(new Question("q2","Which uses LIFO? ", Arrays.asList("Queue","Stack","Heap","Graph"),1,"Stack uses LIFO"));
        qs.add(new Question("q3","Routing layer is?", Arrays.asList("App","Transport","Network","Link"),2,"Network layer routes"));
        qs.add(new Question("q4","JVM stands for?", Arrays.asList("Java Variable Machine","Java Virtual Machine","Just VM","None"),1,"JVM = Java Virtual Machine"));
        qs.add(new Question("q5","Binary search complexity?", Arrays.asList("O(n)","O(log n)","O(n log n)","O(1)"),1,"Binary search is O(log n)"));
        qs.add(new Question("q6","Drop table command?", Arrays.asList("DELETE","DROP","REMOVE","TRUNCATE"),1,"DROP removes table"));
        qs.add(new Question("q7","Create thread in Java with?", Arrays.asList("implements","extends","synchronized","new Thread"),3,"new Thread(...)"));
        qs.add(new Question("q8","Idempotent HTTP method?", Arrays.asList("POST","PUT","PATCH","CONNECT"),1,"PUT is idempotent"));

        Exam sample = new Exam("exam-01","Fundamentals of Computing","Short demo MCQ exam", 10, qs);
        exams.put(sample.getId(), sample);
        save();
    }

    public synchronized boolean registerUser(String username, String fullName, String password){
        if (users.containsKey(username)) return false;
        users.put(username, new User(username, fullName, password)); save(); return true;
    }

    public synchronized User authenticate(String username, String password){
        User u = users.get(username); if (u!=null && u.checkPassword(password)) return u; return null;
    }

    public synchronized java.util.Collection<Exam> listExams(){ return new ArrayList<>(exams.values()); }
    public synchronized Exam getExam(String id){ return exams.get(id); }
    public synchronized void updateUser(User u){ users.put(u.getUsername(), u); save(); }
    public synchronized void saveState(){ save(); }
}

/* -------------------- UI -------------------- */

class ThemeManager {
    // Polished dark palette and light palette; central switch sets UI defaults used by Swing components.
    private static final Color DARK_BG = new Color(28, 33, 41);
    private static final Color DARK_PANEL = new Color(38, 45, 54);
    private static final Color DARK_ACCENT = new Color(88, 160, 255);
    private static final Color DARK_TEXT = new Color(230, 230, 235);

    private static final Color LIGHT_BG = new Color(245,245,249);
    private static final Color LIGHT_PANEL = new Color(255, 255, 255);
    private static final Color LIGHT_ACCENT = new Color(44, 117, 255);
    private static final Color LIGHT_TEXT = new Color(20, 20, 20);

    private boolean dark = false;

    public boolean isDark(){ return dark; }

    public void applyTheme(boolean darkMode){
        dark = darkMode;
        if (dark) {
            UIManager.put("control", DARK_PANEL);
            UIManager.put("info", DARK_PANEL);
            UIManager.put("nimbusBase", DARK_BG);
            UIManager.put("nimbusFocus", DARK_ACCENT);
            UIManager.put("text", DARK_TEXT);
            UIManager.put("Button.background", DARK_ACCENT);
            UIManager.put("Table.background", DARK_PANEL);
            UIManager.put("Table.foreground", DARK_TEXT);
            UIManager.put("Label.foreground", DARK_TEXT);
            UIManager.put("ProgressBar.foreground", DARK_ACCENT);
        } else {
            UIManager.put("control", LIGHT_PANEL);
            UIManager.put("info", LIGHT_PANEL);
            UIManager.put("nimbusBase", LIGHT_BG);
            UIManager.put("nimbusFocus", LIGHT_ACCENT);
            UIManager.put("text", LIGHT_TEXT);
            UIManager.put("Button.background", LIGHT_ACCENT);
            UIManager.put("Table.background", LIGHT_PANEL);
            UIManager.put("Table.foreground", LIGHT_TEXT);
            UIManager.put("Label.foreground", LIGHT_TEXT);
            UIManager.put("ProgressBar.foreground", LIGHT_ACCENT);
        }
    }

    // Helper to style a panel's background for consistent look
    public Color panelBg(){ return dark ? DARK_PANEL : LIGHT_PANEL; }
    public Color bg(){ return dark ? DARK_BG : LIGHT_BG; }
    public Color text(){ return dark ? DARK_TEXT : LIGHT_TEXT; }
    public Color accent(){ return dark ? DARK_ACCENT : LIGHT_ACCENT; }
}

/* Main UI class */
class ExamUI {
    private final ExamService service;
    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final ThemeManager theme = new ThemeManager();

    private User currentUser;

    // shared components
    private DefaultTableModel examsModel;
    private JLabel lblUser = new JLabel();
    private JProgressBar progressBar = new JProgressBar();

    // exam runtime
    private Exam currentExam;
    private UserExamState currentState;
    private javax.swing.Timer timer;
    private long[] remainingRef = new long[1];

    // question UI
    private JLabel lblTimer = new JLabel("00:00:00");
    private JLabel lblQIndex = new JLabel("Question 0/0");
    private JPanel questionPanel = new JPanel(new BorderLayout());
    private int currentQuestionIndex = 0;

    // Save indicator
    private JLabel lblSaved = new JLabel("\u2713 Saved"); // check mark
    private final DecimalFormat pctFormat = new DecimalFormat("#0.0");

    public ExamUI(ExamService service){
        this.service = service;
        frame = new JFrame("OasisInfoByte — Online Examination (Pro)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);

        // Build root cards
        root.add(buildLoginPanel(), "login");
        root.add(buildDashboardPanel(), "dashboard");
        root.add(buildExamPanel(), "exam");
        root.add(buildResultPanel(), "result");

        frame.setContentPane(root);

        // initial theme
        theme.applyTheme(false);
        SwingUtilities.updateComponentTreeUI(frame);
    }

    public void show(){
        frame.setVisible(true);
        cards.show(root, "login");
    }

    /* ---- Login Panel ----- */
    private JPanel buildLoginPanel(){
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(theme.bg());
        p.setBorder(new EmptyBorder(30,30,30,30));

        // header with gradient
        JPanel header = createHeader("Oasis Online Examination", "Secure, fast and polished exam experience");
        p.add(header, BorderLayout.NORTH);

        // center form
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(theme.panelBg());
        center.setBorder(new CompoundBorder(new LineBorder(new Color(0,0,0,20),1), new EmptyBorder(18,18,18,18)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel l1 = new JLabel("Username:"); l1.setForeground(theme.text());
        gbc.gridx=0; gbc.gridy=0; gbc.weightx=0.0; center.add(l1, gbc);
        gbc.gridx=1; gbc.weightx=1.0; JTextField tfUser = new JTextField(); tfUser.setToolTipText("Enter your username"); center.add(tfUser, gbc);

        JLabel l2 = new JLabel("Password:"); l2.setForeground(theme.text());
        gbc.gridx=0; gbc.gridy++; gbc.weightx=0.0; center.add(l2, gbc);
        gbc.gridx=1; gbc.weightx=1.0; JPasswordField pf = new JPasswordField(); pf.setToolTipText("Enter your password"); center.add(pf, gbc);

        gbc.gridx=0; gbc.gridy++; gbc.gridwidth=2;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton btnLogin = createPrimaryButton("\u27A4  Login"); // unicode arrow
        JButton btnRegister = createSecondaryButton("\u2605 Register");
        actions.add(btnRegister); actions.add(btnLogin);
        center.add(actions, gbc);

        // tip row
        gbc.gridy++; JLabel tip = new JLabel("<html><i>Demo user: <b>venkat</b> / <b>1234</b> &nbsp; • Use the Register button to create new user.</i></html>");
        tip.setForeground(theme.text()); center.add(tip, gbc);

        p.add(center, BorderLayout.CENTER);

        // footer with theme toggle
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JButton themeToggle = new JButton("\u263C Dark"); themeToggle.setToolTipText("Toggle dark / light theme");
        themeToggle.setFocusPainted(false); styleToggle(themeToggle);
        footer.add(themeToggle, BorderLayout.EAST);
        p.add(footer, BorderLayout.SOUTH);

        // Actions
        btnLogin.addActionListener(e -> {
            String u = tfUser.getText().trim(); String pw = new String(pf.getPassword()).trim();
            if (u.isEmpty() || pw.isEmpty()){ JOptionPane.showMessageDialog(frame, "Please enter username and password."); return; }
            User user = service.authenticate(u, pw);
            if (user == null){ JOptionPane.showMessageDialog(frame, "Invalid credentials."); return; }
            currentUser = user; onLogin();
        });

        btnRegister.addActionListener(e -> {
            JTextField ru = new JTextField(); JTextField rn = new JTextField(); JPasswordField rpw = new JPasswordField();
            Object[] fields = {"Choose username:", ru, "Full name:", rn, "Password:", rpw};
            int r = JOptionPane.showConfirmDialog(frame, fields, "Create new user", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            String uu = ru.getText().trim(), nn = rn.getText().trim(), pp = new String(rpw.getPassword()).trim();
            if (uu.length() < 3 || pp.length() < 3){ JOptionPane.showMessageDialog(frame, "Username and password must be at least 3 characters."); return; }
            boolean ok = service.registerUser(uu, nn.isEmpty()?uu:nn, pp);
            if (!ok) JOptionPane.showMessageDialog(frame, "Username already exists."); else { JOptionPane.showMessageDialog(frame, "Registered successfully — you can now login."); }
        });

        themeToggle.addActionListener(e -> {
            boolean newDark = !theme.isDark();
            theme.applyTheme(newDark);
            // Update label & icon
            themeToggle.setText(newDark ? "\u2600 Light" : "\u263C Dark");
            // Repaint whole UI
            SwingUtilities.updateComponentTreeUI(frame);
        });

        return p;
    }

    /* ---- Dashboard Panel ---- */
    private JPanel buildDashboardPanel(){
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setBorder(new EmptyBorder(12,12,12,12));
        p.setBackground(theme.bg());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        lblUser.setFont(lblUser.getFont().deriveFont(Font.BOLD, 14f)); lblUser.setForeground(theme.text());
        header.add(lblUser, BorderLayout.WEST);

        // header actions: profile, logout, theme toggle button
        JPanel hdrActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); hdrActions.setOpaque(false);
        JButton bProfile = createSecondaryButton("\u270E Profile"); JButton bLogout = createSecondaryButton("\u23FB Logout");
        JButton bTheme = createToggleButton(theme.isDark() ? "\u2600 Light" : "\u263C Dark");
        hdrActions.add(bProfile); hdrActions.add(bLogout); hdrActions.add(bTheme);
        header.add(hdrActions, BorderLayout.EAST);
        p.add(header, BorderLayout.NORTH);

        // center: table
        String[] cols = {"Exam ID","Title","Duration","Description"};
        examsModel = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r,int c){ return false; } };
        JTable tbl = new JTable(examsModel);
        tbl.setFillsViewportHeight(true);
        tbl.getTableHeader().setReorderingAllowed(false);
        JScrollPane sp = new JScrollPane(tbl);
        sp.setBorder(new CompoundBorder(new LineBorder(new Color(0,0,0,20),1), new EmptyBorder(8,8,8,8)));
        p.add(sp, BorderLayout.CENTER);

        // bottom with start button and progress bar
        JPanel bottom = new JPanel(new BorderLayout(8,8));
        bottom.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT)); left.setOpaque(false);
        JButton bStart = createPrimaryButton("\u25B6 Start Exam");
        JButton bRefresh = createSecondaryButton("\u21BB Refresh");
        left.add(bStart); left.add(bRefresh);
        bottom.add(left, BorderLayout.WEST);

        progressBar.setStringPainted(true); progressBar.setValue(0);
        bottom.add(progressBar, BorderLayout.CENTER);

        p.add(bottom, BorderLayout.SOUTH);

        // actions
        bLogout.addActionListener(e -> { currentUser = null; cards.show(root, "login"); });
        bProfile.addActionListener(e -> showProfileDialog());
        bRefresh.addActionListener(e -> refreshExamsTable());
        bStart.addActionListener(e -> {
            int row = tbl.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(frame, "Select an exam first."); return; }
            String examId = (String) tbl.getValueAt(row, 0);
            startExam(examId);
        });
        bTheme.addActionListener(e -> {
            boolean newDark = !theme.isDark();
            theme.applyTheme(newDark);
            bTheme.setText(newDark ? "\u2600 Light" : "\u263C Dark");
            SwingUtilities.updateComponentTreeUI(frame);
        });

        return p;
    }

    /* ---- Exam Panel ---- */
    private JPanel buildExamPanel(){
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setBorder(new EmptyBorder(12,12,12,12));
        p.setBackground(theme.bg());

        // top header: title, timer, saved indicator
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        lblQIndex.setFont(lblQIndex.getFont().deriveFont(Font.BOLD, 14f)); lblQIndex.setForeground(theme.text());
        lblTimer.setFont(lblTimer.getFont().deriveFont(Font.BOLD, 16f)); lblTimer.setForeground(theme.text());
        lblSaved.setFont(lblSaved.getFont().deriveFont(Font.PLAIN, 12f));
        lblSaved.setForeground(new Color(0x2E8B57));
        top.add(lblQIndex, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0)); right.setOpaque(false);
        right.add(lblSaved); right.add(lblTimer);
        top.add(right, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // main area: questionPanel center, quick-jump right
        questionPanel = new JPanel(new BorderLayout(8,8));
        questionPanel.setBackground(theme.panelBg());
        questionPanel.setBorder(new LineBorder(new Color(0,0,0,20),1));
        p.add(questionPanel, BorderLayout.CENTER);

        // bottom navigation
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        nav.setOpaque(false);
        JButton bPrev = createSecondaryButton("\u25C0 Prev"); JButton bNext = createSecondaryButton("Next \u25B6");
        JButton bSubmit = createPrimaryButton("\u2714 Submit");
        nav.add(bPrev); nav.add(bNext); nav.add(bSubmit);
        p.add(nav, BorderLayout.SOUTH);

        bPrev.addActionListener(e -> navigateQuestion(-1));
        bNext.addActionListener(e -> navigateQuestion(1));
        bSubmit.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame, "Submit exam now?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) finishAndShowResult();
        });

        return p;
    }

    /* ---- Result Panel ---- */
    private JPanel buildResultPanel(){
        JPanel rp = new JPanel(new BorderLayout(12,12));
        rp.setBorder(new EmptyBorder(12,12,12,12));
        rp.setBackground(theme.bg());
        return rp;
    }

    /* ---------- Actions & helpers ---------- */

    private JPanel createHeader(String title, String sub){
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(100,110));
        header.setBorder(new EmptyBorder(10,10,10,10));
        header.setOpaque(false);

        // gradient panel
        JPanel g = new JPanel(){
            @Override protected void paintComponent(Graphics g2){
                super.paintComponent(g2);
                Graphics2D g3 = (Graphics2D) g2;
                Color c1 = theme.accent();
                Color c2 = theme.bg();
                GradientPaint gp = new GradientPaint(0,0,c1, getWidth(), getHeight(), c2);
                g3.setPaint(gp);
                g3.fillRect(0,0,getWidth(),getHeight());
            }
        };
        g.setLayout(new BorderLayout());
        g.setBorder(new EmptyBorder(12,12,12,12));
        JLabel hTitle = new JLabel(title); hTitle.setFont(hTitle.getFont().deriveFont(Font.BOLD, 22f));
        hTitle.setForeground(Color.WHITE);
        JLabel hSub = new JLabel("<html><i>" + sub + "</i></html>"); hSub.setForeground(Color.WHITE);
        g.add(hTitle, BorderLayout.NORTH); g.add(hSub, BorderLayout.SOUTH);
        header.add(g, BorderLayout.CENTER);

        return header;
    }

    private void onLogin(){
        lblUser.setText("Welcome, " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");
        lblUser.setForeground(theme.text());
        refreshExamsTable();
        updateProgressBar();
        cards.show(root, "dashboard");
    }

    private void refreshExamsTable(){
        DefaultTableModel model = examsModel;
        model.setRowCount(0);
        for (Exam e : service.listExams()) model.addRow(new Object[]{ e.getId(), e.getTitle(), e.getDurationMinutes() + " min", e.getDescription() });
    }

    private void showProfileDialog(){
        JTextField name = new JTextField(currentUser.getFullName());
        JPasswordField oldp = new JPasswordField(); JPasswordField newp = new JPasswordField();
        Object[] fields = {"Full name:", name, "Old password (leave blank):", oldp, "New password:", newp};
        int r = JOptionPane.showConfirmDialog(frame, fields, "Profile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String nn = name.getText().trim(), old = new String(oldp.getPassword()).trim(), nw = new String(newp.getPassword()).trim();
        if (!old.isEmpty()){
            if (!currentUser.checkPassword(old)){ JOptionPane.showMessageDialog(frame, "Old password incorrect"); return; }
            if (nw.length() < 3) { JOptionPane.showMessageDialog(frame, "New password too short"); return; }
            currentUser.setPassword(nw);
        }
        currentUser.setFullName(nn.isEmpty() ? currentUser.getUsername() : nn);
        service.updateUser(currentUser);
        JOptionPane.showMessageDialog(frame, "Profile updated.");
        lblUser.setText("Welcome, " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");
    }

    private void startExam(String examId){
        Exam e = service.getExam(examId);
        if (e == null){ JOptionPane.showMessageDialog(frame, "Exam not found."); return; }
        currentExam = e;

        UserExamState st = currentUser.getAttempts().get(examId);
        if (st == null){
            long millis = e.getDurationMinutes() * 60L * 1000L;
            st = new UserExamState(examId, millis);
            currentUser.getAttempts().put(examId, st);
            service.updateUser(currentUser);
        }
        if (st.submitted){ JOptionPane.showMessageDialog(frame, "You already submitted this exam on " + st.submittedAt); return; }
        currentState = st;
        currentQuestionIndex = 0;
        openExamUI();
    }

    private void openExamUI(){
        // start timer and render
        startTimer(currentState.timeRemainingMillis);
        renderQuestion();
        cards.show(root, "exam");
    }

    private void startTimer(long millis){
        if (timer != null) timer.stop();
        remainingRef[0] = millis;
        lblTimer.setText(formatMillis(remainingRef[0]));
        timer = new javax.swing.Timer(500, e -> {
            remainingRef[0] -= 500;
            if (remainingRef[0] <= 0){
                timer.stop();
                currentState.timeRemainingMillis = 0;
                service.saveState();
                JOptionPane.showMessageDialog(frame, "Time is up. Your exam will be submitted.");
                finishAndShowResult();
            } else {
                lblTimer.setText(formatMillis(remainingRef[0]));
                // auto-save approximately every 10s
                if ((remainingRef[0] / 1000) % 10 == 0){
                    currentState.timeRemainingMillis = remainingRef[0];
                    service.saveState();
                    pulseSavedIndicator();
                }
            }
        });
        timer.setInitialDelay(0); timer.start();
    }

    private void pulseSavedIndicator(){
        lblSaved.setText("\u2713 Saved " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        // fade back after short delay
        new javax.swing.Timer(1800, e -> lblSaved.setText("\u2713 Saved")).start();
    }

    private String formatMillis(long m){
        long s = Math.max(0, m / 1000);
        long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private void renderQuestion(){
        questionPanel.removeAll();
        java.util.List<Question> qs = currentExam.getQuestions();
        int total = qs.size();
        currentQuestionIndex = Math.max(0, Math.min(currentQuestionIndex, total - 1));
        Question q = qs.get(currentQuestionIndex);

        lblQIndex.setText("Question " + (currentQuestionIndex + 1) + " / " + total + "  —  " + currentExam.getTitle());

        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        JLabel qtext = new JLabel("<html><div style='font-size:12pt;'>" + (currentQuestionIndex + 1) + ". " + q.getText() + "</div></html>");
        qtext.setBorder(new EmptyBorder(8,8,8,8)); qtext.setForeground(theme.text());
        top.add(qtext, BorderLayout.CENTER);
        questionPanel.add(top, BorderLayout.NORTH);

        JPanel opts = new JPanel();
        opts.setLayout(new BoxLayout(opts, BoxLayout.Y_AXIS));
        opts.setBackground(theme.panelBg());

        ButtonGroup bg = new ButtonGroup();
        int selected = currentState.answers.getOrDefault(q.getId(), -1);

        for (int i = 0; i < q.getOptions().size(); i++){
            String opt = q.getOptions().get(i);
            JRadioButton rb = new JRadioButton(opt);
            rb.setBackground(theme.panelBg());
            rb.setAlignmentX(Component.LEFT_ALIGNMENT);
            rb.setForeground(theme.text());
            bg.add(rb); opts.add(rb);
            if (i == selected) rb.setSelected(true);
            int idx = i;
            rb.addActionListener(ae -> {
                currentState.answers.put(q.getId(), idx);
                currentState.timeRemainingMillis = remainingRef[0];
                service.saveState();
                updateProgressBar();
                pulseSavedIndicator();
            });
            opts.add(Box.createVerticalStrut(6));
        }

        JScrollPane sp = new JScrollPane(opts);
        sp.setBorder(null);
        sp.getViewport().setBackground(theme.panelBg());
        questionPanel.add(sp, BorderLayout.CENTER);

        // quick jump panel
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(220, 0));
        JPanel quick = new JPanel(new GridLayout(0,5,6,6));
        quick.setOpaque(false);

        for (int i=0;i<total;i++){
            JButton b = new JButton(String.valueOf(i+1));
            String qid = qs.get(i).getId();
            boolean answered = currentState.answers.containsKey(qid);
            b.setBackground(answered ? new Color(0xDFF0D8) : new Color(0xF0F0F0));
            b.setToolTipText(answered ? "Answered" : "Not answered");
            final int qi = i;
            b.addActionListener(e -> { currentQuestionIndex = qi; renderQuestion(); });
            quick.add(b);
        }
        right.add(new JLabel("Jump to:"), BorderLayout.NORTH);
        right.add(new JScrollPane(quick), BorderLayout.CENTER);
        questionPanel.add(right, BorderLayout.EAST);

        questionPanel.revalidate(); questionPanel.repaint();
    }

    private void navigateQuestion(int delta){
        currentQuestionIndex += delta;
        if (currentQuestionIndex < 0) currentQuestionIndex = 0;
        if (currentQuestionIndex >= currentExam.getQuestions().size()) currentQuestionIndex = currentExam.getQuestions().size() - 1;
        renderQuestion();
    }

    private void finishAndShowResult(){
        if (timer != null) timer.stop();

        int correct = 0; int total = currentExam.getQuestions().size();
        java.util.List<String> review = new ArrayList<>();
        for (Question q : currentExam.getQuestions()){
            int sel = currentState.answers.getOrDefault(q.getId(), -1);
            if (sel == q.getCorrectIndex()) correct++;
            String selTxt = sel >=0 && sel < q.getOptions().size() ? q.getOptions().get(sel) : "-";
            review.add(q.getText() + "\nSelected: " + selTxt + "\nCorrect: " + q.getOptions().get(q.getCorrectIndex()) + "\nExplanation: " + q.getExplanation());
        }
        currentState.submitted = true; currentState.submittedAt = new Date(); currentState.timeRemainingMillis = remainingRef[0];
        service.updateUser(currentUser);

        // build result UI
        JPanel rp = (JPanel) root.getComponent(3);
        rp.removeAll(); rp.setBackground(theme.bg());
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        JLabel t = new JLabel("Result — " + currentExam.getTitle()); t.setFont(t.getFont().deriveFont(Font.BOLD, 20f)); t.setForeground(theme.text());
        top.add(t, BorderLayout.WEST);

        double pct = (100.0 * correct) / total; String verdict = pct >= 50.0 ? "PASS" : "FAIL";
        JLabel score = new JLabel(String.format("%d / %d  ( %s ) ", correct, total, pctFormat.format(pct) + "%"));
        score.setFont(score.getFont().deriveFont(Font.BOLD, 16f));
        score.setForeground(pct >= 50.0 ? new Color(0x2E8B57) : new Color(0xB22222));
        top.add(score, BorderLayout.EAST);

        rp.add(top, BorderLayout.NORTH);

        JTextArea ta = new JTextArea(String.join("\n\n", review));
        ta.setEditable(false); ta.setCaretPosition(0);
        ta.setBackground(theme.panelBg()); ta.setForeground(theme.text());
        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(new EmptyBorder(8,8,8,8));
        rp.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.setOpaque(false);
        JButton back = createPrimaryButton("\u21A9 Back to Dashboard");
        bottom.add(back); rp.add(bottom, BorderLayout.SOUTH);
        back.addActionListener(e -> { currentExam = null; currentState = null; updateProgressBar(); cards.show(root, "dashboard"); });

        rp.revalidate(); rp.repaint();
        cards.show(root, "result");
    }

    private void updateProgressBar(){
        // progress = number of submitted exams / total exams (simple metric)
        int totalExams = service.listExams().size();
        if (totalExams == 0) { progressBar.setValue(0); progressBar.setString("No exams available"); return; }
        int submitted = 0;
        for (Exam e : service.listExams()){
            UserExamState st = currentUser.getAttempts().get(e.getId());
            if (st != null && st.submitted) submitted++;
        }
        int pct = (int) ((submitted * 100.0f) / totalExams);
        progressBar.setValue(pct);
        progressBar.setString("Progress: " + pct + "% (" + submitted + "/" + totalExams + " completed)");
    }

    /* ---------- UI factories ---------- */

    private JButton createPrimaryButton(String text){
        JButton b = new JButton(text);
        b.setBackground(theme.accent()); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setBorder(new EmptyBorder(8,12,8,12));
        return b;
    }
    private JButton createSecondaryButton(String text){
        JButton b = new JButton(text);
        b.setBackground(new Color(0xF2F4F8)); b.setForeground(theme.text());
        b.setFocusPainted(false); b.setBorder(new EmptyBorder(6,10,6,10));
        return b;
    }
    private JButton createToggleButton(String text){
        JButton b = new JButton(text);
        b.setBackground(new Color(0xEAEFF7)); b.setFocusPainted(false);
        b.setBorder(new LineBorder(new Color(0xCCCCCC), 1, true));
        return b;
    }
    private void styleToggle(JButton b){
        b.setBackground(new Color(0xEEEFF3)); b.setFocusPainted(false); b.setBorder(new EmptyBorder(6,10,6,10));
    }

    /* ---- Utilities for components ---- */

    private void pulse(Component c){
        Color orig = c.getBackground();
        c.setBackground(orig.brighter());
        new javax.swing.Timer(180, e -> c.setBackground(orig)).start();
    }
}
