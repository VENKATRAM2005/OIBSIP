// File: VenkatOnlineExamPro.java
// Polished Online Exam single file (login, profile, MCQ exam, timer, auto-submit).
// Compile: javac VenkatOnlineExamPro.java
// Run: java VenkatOnlineExamPro

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;

/**
 * VenkatOnlineExamPro - compact single-file exam app.
 * - File persistence in "exam_data" folder
 * - Login, profile edit, password change
 * - MCQ exam: question navigation, mark-for-review
 * - Timer with auto-submit, results saved
 */
public class VenkatOnlineExamPro {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ExamController().start());
    }

    // Controller + storage
    static class ExamController {
        final Path root = Paths.get("exam_data");
        final Path usersFile = root.resolve("users.ser");
        final Path questionsFile = root.resolve("questions.ser");
        final Path resultsFile = root.resolve("results.txt");
        Map<String, User> users;
        List<Question> questions;
        JFrame frame;
        User current;

        ExamController() {
            try { if (!Files.exists(root)) Files.createDirectories(root); } catch (Exception e) {}
            users = loadUsers();
            questions = loadQuestions();
            if (questions.isEmpty()) seedQuestions();
        }

        void start() {
            frame = new JFrame("Venkat Online Exam Pro");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900,600);
            frame.setLocationRelativeTo(null);
            showLogin();
            frame.setVisible(true);
        }

        Map<String, User> loadUsers() {
            if (!Files.exists(usersFile)) return new HashMap<>();
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(usersFile))) {
                return (Map<String,User>) ois.readObject();
            } catch (Exception e) { return new HashMap<>(); }
        }

        List<Question> loadQuestions() {
            if (!Files.exists(questionsFile)) return new ArrayList<>();
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(questionsFile))) {
                return (List<Question>) ois.readObject();
            } catch (Exception e) { return new ArrayList<>(); }
        }

        void saveUsers() {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(usersFile))) {
                oos.writeObject(users);
            } catch (Exception e) { e.printStackTrace(); }
        }

        void saveQuestions() {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(questionsFile))) {
                oos.writeObject(questions);
            } catch (Exception e) { e.printStackTrace(); }
        }

        void seedQuestions() {
            questions.add(new Question("Which keyword to inherit a class in Java?", Arrays.asList("implements","extends","inherits","uses"), 1));
            questions.add(new Question("Which collection preserves insertion order?", Arrays.asList("HashSet","LinkedHashSet","TreeSet","PriorityQueue"), 1));
            questions.add(new Question("Select the checked exception:", Arrays.asList("NullPointerException","IOException","ArrayIndexOutOfBoundsException","RuntimeException"), 1));
            saveQuestions();
        }

        void showLogin() {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(new LoginPanel(this));
            frame.revalidate(); frame.repaint();
        }

        void showDashboard() {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(new DashboardPanel(this));
            frame.revalidate(); frame.repaint();
        }

        void startExam(int durationSeconds) {
            ExamSession session = new ExamSession(current.username, questions, durationSeconds);
            frame.getContentPane().removeAll();
            frame.getContentPane().add(new ExamPanel(this, session));
            frame.revalidate(); frame.repaint();
        }

        void finishExam(ExamSession.Result result) {
            // append to results file
            String line = String.format("%s|%s|%d|%d|%s", result.username, result.timestamp.toString(), result.correct, result.percentScore, LocalDateTime.now().toString());
            try { Files.write(resultsFile, Collections.singletonList(line), StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception e) {}
            JOptionPane.showMessageDialog(frame, "Exam submitted!\nScore: " + result.percentScore + "%\nCorrect: " + result.correct);
            showDashboard();
        }
    }

    static class User implements Serializable {
        final String username; String fullname; String password;
        User(String u, String f, String p) { username=u; fullname=f; password=p; }
    }

    static class Question implements Serializable {
        final String text; final List<String> options; final int correct;
        Question(String t, List<String> o, int c) { text=t; options=new ArrayList<>(o); correct=c; }
    }

    static class ExamSession implements Serializable {
        final String username; final List<Question> questions; final long durationSeconds;
        final Map<Integer,Integer> answers = new HashMap<>();
        final LocalDateTime started;
        LocalDateTime submitted;
        ExamSession(String user, List<Question> q, long durationSeconds) { this.username=user; this.questions=new ArrayList<>(q); this.durationSeconds=durationSeconds; this.started=LocalDateTime.now(); }
        void answer(int qIndex, int optionIndex) { answers.put(qIndex, optionIndex); }
        int getAnswer(int qIndex) { return answers.getOrDefault(qIndex, -1); }
        ExamResult evaluate() {
            int correct=0;
            for (int i=0;i<questions.size();i++) if (getAnswer(i)==questions.get(i).correct) correct++;
            int pct = Math.max(0, (int)Math.round(100.0*correct/questions.size()));
            return new ExamResult(username, correct, questions.size()-correct, pct, LocalDateTime.now());
        }
        static class ExamResult { String username; int correct; int wrong; int percentScore; LocalDateTime timestamp; ExamResult(String u,int c,int w,int p,LocalDateTime ts){username=u;correct=c;wrong=w;percentScore=p;timestamp=ts;} }
    }

    // ---------- UI components ----------
    static class LoginPanel extends JPanel {
        LoginPanel(ExamController ctrl) {
            setLayout(new BorderLayout(12,12));
            setBorder(new EmptyBorder(20,20,20,20));
            JLabel h = new JLabel("<html><h1>Venkat Online Exam</h1></html>");
            add(h, BorderLayout.NORTH);
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(8,8,8,8); c.anchor = GridBagConstraints.WEST;
            c.gridx=0; c.gridy=0; form.add(new JLabel("Username:"), c);
            c.gridx=1; JTextField txtUser = new JTextField(16); form.add(txtUser, c);
            c.gridx=0; c.gridy=1; form.add(new JLabel("Password:"), c);
            c.gridx=1; JPasswordField pf = new JPasswordField(16); form.add(pf, c);
            c.gridy=2; c.gridx=0; c.gridwidth=2;
            JPanel btns = new JPanel();
            JButton login = new JButton("Login"), reg = new JButton("Register");
            btns.add(login); btns.add(reg);
            form.add(btns, c);
            add(form, BorderLayout.CENTER);

            login.addActionListener(e -> {
                String u = txtUser.getText().trim(); String p = new String(pf.getPassword());
                if (u.isEmpty()||p.isEmpty()) { JOptionPane.showMessageDialog(this,"Enter both"); return; }
                if (ctrl.users.containsKey(u) && ctrl.users.get(u).password.equals(p)) {
                    ctrl.current = ctrl.users.get(u);
                    ctrl.saveUsers();
                    ctrl.showDashboard();
                } else JOptionPane.showMessageDialog(this,"Invalid credentials");
            });

            reg.addActionListener(e -> {
                String u = JOptionPane.showInputDialog(this, "Choose username:");
                if (u==null) return; u=u.trim(); if (u.isEmpty()||ctrl.users.containsKey(u)){ JOptionPane.showMessageDialog(this,"Invalid or exists"); return;}
                String name = JOptionPane.showInputDialog(this, "Full name:"); if (name==null||name.trim().isEmpty()){ JOptionPane.showMessageDialog(this,"Invalid"); return; }
                String pass = JOptionPane.showInputDialog(this, "Password:"); if (pass==null||pass.length()<3){ JOptionPane.showMessageDialog(this,"Invalid"); return; }
                ctrl.users.put(u, new User(u, name.trim(), pass)); ctrl.saveUsers(); JOptionPane.showMessageDialog(this,"Registered");
            });
        }
    }

    static class DashboardPanel extends JPanel {
        DashboardPanel(ExamController ctrl) {
            setLayout(new BorderLayout(10,10));
            JLabel h = new JLabel("<html><h2>Welcome " + ctrl.current.fullname + "</h2></html>");
            add(h, BorderLayout.NORTH);
            JPanel center = new JPanel(new GridLayout(2,2,10,10));
            JButton start = new JButton("Start Exam (5 mins)"), profile = new JButton("Profile / Change Password"), logout = new JButton("Logout"), results = new JButton("View Results File");
            center.add(start); center.add(profile); center.add(results); center.add(logout);
            add(center, BorderLayout.CENTER);

            start.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this, "Start exam? Timer will auto-submit", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm==JOptionPane.YES_OPTION) ctrl.startExam(5*60);
            });
            profile.addActionListener(e -> {
                JPanel p = new JPanel(new GridBagLayout()); GridBagConstraints c = new GridBagConstraints(); c.insets=new Insets(6,6,6,6);
                c.gridx=0;c.gridy=0; p.add(new JLabel("Full name:"), c); c.gridx=1; JTextField tf = new JTextField(ctrl.current.fullname,16); p.add(tf,c);
                c.gridx=0;c.gridy=1; p.add(new JLabel("New password:"), c); c.gridx=1; JPasswordField pf = new JPasswordField(16); p.add(pf,c);
                int ok = JOptionPane.showConfirmDialog(this, p, "Profile", JOptionPane.OK_CANCEL_OPTION);
                if (ok==JOptionPane.OK_OPTION) { ctrl.current.fullname = tf.getText().trim(); String np = new String(pf.getPassword()); if (!np.isEmpty()) ctrl.current.password = np; ctrl.saveUsers(); JOptionPane.showMessageDialog(this,"Saved"); }
            });
            logout.addActionListener(e -> { ctrl.current = null; ctrl.showLogin(); });
            results.addActionListener(e -> {
                try { List<String> lines = Files.exists(ctrl.resultsFile)?Files.readAllLines(ctrl.resultsFile):Collections.emptyList();
                    JTextArea ta = new JTextArea(String.join("\n", lines));
                    ta.setEditable(false); JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Results", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error reading results"); }
            });
        }
    }

    static class ExamPanel extends JPanel {
        final ExamController ctrl; final ExamSession session;
        final JLabel lblTimer = new JLabel(); final JLabel lblQno = new JLabel();
        final JTextArea taQuestion = new JTextArea();
        final JRadioButton[] opts = new JRadioButton[4];
        final ButtonGroup bg = new ButtonGroup();
        int index = 0;
        Timer swingTimer; long remaining;

        ExamPanel(ExamController ctrl, ExamSession session) {
            this.ctrl = ctrl; this.session = session;
            setLayout(new BorderLayout(8,8));
            JPanel top = new JPanel(new BorderLayout()); top.add(lblQno, BorderLayout.WEST); lblTimer.setFont(new Font("Monospaced", Font.BOLD, 16)); top.add(lblTimer, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            taQuestion.setLineWrap(true); taQuestion.setWrapStyleWord(true); taQuestion.setEditable(false); add(new JScrollPane(taQuestion), BorderLayout.CENTER);
            JPanel left = new JPanel(new GridLayout(4,1,6,6));
            for (int i=0;i<opts.length;i++){ opts[i] = new JRadioButton(); bg.add(opts[i]); left.add(opts[i]); final int idx=i; opts[i].addActionListener(e -> session.answer(index, idx)); }
            add(left, BorderLayout.WEST);
            JPanel nav = new JPanel(); JButton prev=new JButton("Prev"), next=new JButton("Next"), mark=new JButton("Mark/Unmark"), submit=new JButton("Submit");
            nav.add(prev); nav.add(next); nav.add(mark); nav.add(submit); add(nav, BorderLayout.SOUTH);
            prev.addActionListener(e->loadQuestion(index-1)); next.addActionListener(e->loadQuestion(index+1));
            mark.addActionListener(e-> { /* mark-for-review simulation */ JOptionPane.showMessageDialog(this, "Marked question for review (demo)"); });
            submit.addActionListener(e->{ int c = JOptionPane.showConfirmDialog(this,"Submit now?","Confirm",JOptionPane.YES_NO_OPTION); if (c==JOptionPane.YES_OPTION) doSubmit(); });
            remaining = session.durationSeconds;
            loadQuestion(0); startTimer();
        }

        void loadQuestion(int idx) {
            if (idx<0 || idx>=session.questions.size()) return;
            index = idx;
            Question q = session.questions.get(index);
            lblQno.setText("Question " + (index+1) + " / " + session.questions.size());
            taQuestion.setText(q.text);
            for (int i=0;i<opts.length;i++) {
                if (i<q.options.size()) { opts[i].setVisible(true); opts[i].setText(q.options.get(i)); opts[i].setSelected(session.getAnswer(index)==i); }
                else opts[i].setVisible(false);
            }
        }

        void startTimer() {
            updateTimerLabel();
            swingTimer = new Timer(1000, e->{
                remaining--;
                if (remaining<=0) { swingTimer.stop(); JOptionPane.showMessageDialog(this,"Time up! Auto-submitting."); doSubmit(); }
                else updateTimerLabel();
            });
            swingTimer.start();
        }

        void updateTimerLabel() {
            long mins = remaining/60; long secs = remaining%60;
            lblTimer.setText(String.format("Time left: %02d:%02d", mins, secs));
        }

        void doSubmit() {
            session.submitted = LocalDateTime.now();
            ExamSession.ExamResult res = evaluateSession();
            ctrl.finishExam(new ExamSession.ResultWrapper(session.username,res.correct,res.wrong,res.percentScore,LocalDateTime.now()));
        }

        ExamSession.ExamResult evaluateSession() {
            int correct=0;
            for (int i=0;i<session.questions.size();i++) if (session.getAnswer(i)==session.questions.get(i).correct) correct++;
            int pct = (int)Math.round(100.0*correct/session.questions.size());
            return session.new ExamResult(session.username, correct, session.questions.size()-correct, pct);
        }
    }

    // small wrappers for serialization & results
    static class ExamSession implements Serializable {
        final String username; final List<Question> questions; final long durationSeconds; LocalDateTime submitted;
        final Map<Integer,Integer> answers = new HashMap<>();
        ExamSession(String u, List<Question> q, long d) { username=u; questions=q; durationSeconds=d; }
        void answer(int i,int opt) { answers.put(i,opt); }
        int getAnswer(int i) { return answers.getOrDefault(i,-1); }
        class ExamResult { String username; int correct, wrong, percentScore; ExamResult(String u,int c,int w,int p){username=u;correct=c;wrong=w;percentScore=p;} }
        class ResultWrapper { String u; int correct, wrong, percent; LocalDateTime ts; ResultWrapper(String u,int c,int w,int p, LocalDateTime ts){this.u=u;this.correct=c;this.wrong=w;this.percent=p;this.ts=ts;} }
    }

    // For controller.finishExam compatibility above
    static class ExamSession {
        static class ResultWrapper { String username; int correct, wrong, percent; LocalDateTime ts; ResultWrapper(String u,int c,int w,int p,LocalDateTime ts){username=u;correct=c;wrong=w;percent=p;this.ts=ts;} }
    }
}
