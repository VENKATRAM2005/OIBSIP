/* VenkatATMConsole.java
 *
 * Corrected single-file ATM Interface (Task-3) for OIBSIP.
 * - Matches public class name to filename.
 * - Includes required imports for Swing/AWT classes.
 * - Uses java.util.List explicitly where needed to avoid ambiguity.
 * - Modern Swing UI with theme toggle, persistence, transaction history.
 *
 * Compile:
 *   javac VenkatATMConsole.java
 * Run:
 *   java VenkatATMConsole
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/* ========================= Main program ========================= */
public class VenkatATMConsole {
    public static void main(String[] args) {
        // Try Nimbus L&F (modern) if available
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ATMService service = new ATMService("accounts.dat");
            service.seedDemoIfEmpty(); // create demo accounts if none exist
            ATMUI ui = new ATMUI(service);
            ui.show();
        });
    }
}

/* ========================= Model: UserAccount ========================= */
class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String accountNumber;
    private String name;
    private String pin; // NOTE: store hashed PINs in production
    private double balance;
    private final LinkedList<Transaction> history = new LinkedList<>();

    public UserAccount(String accountNumber, String name, String pin, double initialBalance) {
        this.accountNumber = accountNumber;
        this.name = name;
        this.pin = pin;
        this.balance = initialBalance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean checkPin(String pin) { return Objects.equals(this.pin, pin); }
    public void setPin(String newPin) { this.pin = newPin; }

    public synchronized double getBalance() { return balance; }

    public synchronized void deposit(double amount, String note) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        balance += amount;
        addTransaction(new Transaction(new Date(), Transaction.Type.DEPOSIT, amount, note));
    }

    public synchronized void withdraw(double amount, String note) {
        if (amount <= 0) throw new IllegalArgumentException("Withdraw amount must be positive");
        if (amount > balance) throw new IllegalArgumentException("Insufficient funds");
        balance -= amount;
        addTransaction(new Transaction(new Date(), Transaction.Type.WITHDRAW, amount, note));
    }

    public synchronized void addTransaction(Transaction tx) {
        history.addFirst(tx);
        if (history.size() > 200) history.removeLast();
    }

    // Explicit package for List to avoid collision with java.awt.List
    public synchronized java.util.List<Transaction> getHistory() {
        return new ArrayList<>(history);
    }

    @Override
    public String toString() {
        return accountNumber + " - " + name + " (₹" + String.format("%.2f", balance) + ")";
    }
}

/* ========================= Model: Transaction ========================= */
class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { DEPOSIT, WITHDRAW, TRANSFER }

    private final Date timestamp;
    private final Type type;
    private final double amount;
    private final String note;

    public Transaction(Date timestamp, Type type, double amount, String note) {
        this.timestamp = timestamp;
        this.type = type;
        this.amount = amount;
        this.note = note;
    }

    public Date getTimestamp() { return timestamp; }
    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public String getNote() { return note; }
}

/* ========================= Service: ATMService ========================= */
class ATMService {
    private final File storageFile;
    private Map<String, UserAccount> accounts = new HashMap<>();

    public ATMService(String storageFilePath) {
        this.storageFile = new File(storageFilePath);
        load();
    }

    public synchronized void seedDemoIfEmpty() {
        if (accounts.isEmpty()) {
            UserAccount a1 = new UserAccount("10001", "Venkat R.", "1234", 15000.00);
            a1.addTransaction(new Transaction(new Date(), Transaction.Type.DEPOSIT, 15000.00, "Initial Demo Deposit"));
            UserAccount a2 = new UserAccount("10002", "Anjali K.", "4321", 8000.00);
            a2.addTransaction(new Transaction(new Date(), Transaction.Type.DEPOSIT, 8000.00, "Initial Demo Deposit"));
            accounts.put(a1.getAccountNumber(), a1);
            accounts.put(a2.getAccountNumber(), a2);
            save();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (!storageFile.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storageFile))) {
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                accounts = (Map<String, UserAccount>) obj;
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to load accounts.dat — starting fresh. " + e.getMessage());
            accounts = new HashMap<>();
        }
    }

    public synchronized void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(storageFile))) {
            oos.writeObject(accounts);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized UserAccount authenticate(String accountNumber, String pin) {
        UserAccount acc = accounts.get(accountNumber);
        if (acc != null && acc.checkPin(pin)) return acc;
        return null;
    }

    public synchronized UserAccount getAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }

    public synchronized void createAccount(String accountNumber, String name, String pin, double initial) {
        if (accounts.containsKey(accountNumber)) throw new IllegalArgumentException("Account exists");
        UserAccount acc = new UserAccount(accountNumber, name, pin, initial);
        acc.addTransaction(new Transaction(new Date(), Transaction.Type.DEPOSIT, initial, "Initial credit"));
        accounts.put(accountNumber, acc);
        save();
    }

    public synchronized void deposit(UserAccount acc, double amt, String note) {
        acc.deposit(amt, note);
        save();
    }

    public synchronized void withdraw(UserAccount acc, double amt, String note) {
        acc.withdraw(amt, note);
        save();
    }

    public synchronized void transfer(UserAccount from, String toAccountNumber, double amt) {
        if (from.getAccountNumber().equals(toAccountNumber)) throw new IllegalArgumentException("Cannot transfer to same account");
        UserAccount to = accounts.get(toAccountNumber);
        if (to == null) throw new IllegalArgumentException("Beneficiary not found");
        from.withdraw(amt, "Transfer to " + toAccountNumber);
        to.deposit(amt, "Transfer from " + from.getAccountNumber());
        Date now = new Date();
        from.addTransaction(new Transaction(now, Transaction.Type.TRANSFER, amt, "Transfer to " + toAccountNumber));
        to.addTransaction(new Transaction(now, Transaction.Type.TRANSFER, amt, "Transfer from " + from.getAccountNumber()));
        save();
    }

    public synchronized Collection<UserAccount> listAccounts() {
        return new ArrayList<>(accounts.values());
    }
}

/* ========================= UI: ATMUI ========================= */
class ATMUI {
    private final ATMService service;
    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private UserAccount currentUser;

    // Common components
    private JLabel lblWelcome = new JLabel();
    private JLabel lblBalance = new JLabel();
    private DefaultTableModel historyModel;
    private boolean darkMode = false;

    public ATMUI(ATMService service) {
        this.service = service;
        frame = new JFrame("OasisInfoByte — ATM Interface (Task-3)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(920, 600);
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(800, 520));

        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(buildLoginPanel(), "login");
        root.add(buildDashboardPanel(), "dashboard");
        frame.setContentPane(root);

        // Keyboard shortcut: Ctrl+T toggles theme
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "toggle-theme");
        frame.getRootPane().getActionMap().put("toggle-theme", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleTheme(); }
        });
    }

    public void show() {
        setTheme(darkMode); // initial theme (false -> light)
        frame.setVisible(true);
        cards.show(root, "login");
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        JPanel card = new JPanel(new BorderLayout(12, 12));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY, 1), new EmptyBorder(18, 18, 18, 18)));

        JLabel title = new JLabel("Welcome to Oasis ATM", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints f = new GridBagConstraints();
        f.insets = new Insets(8, 6, 8, 6);
        f.gridx = 0; f.gridy = 0; f.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("Account Number:"), f);
        f.gridx = 1; f.weightx = 1.0; f.fill = GridBagConstraints.HORIZONTAL;
        JTextField tfAccount = new JTextField();
        form.add(tfAccount, f);

        f.gridx = 0; f.gridy = 1; f.weightx = 0; f.fill = GridBagConstraints.NONE;
        form.add(new JLabel("PIN:"), f);
        f.gridx = 1; f.fill = GridBagConstraints.HORIZONTAL;
        JPasswordField pfPin = new JPasswordField();
        form.add(pfPin, f);

        f.gridx = 0; f.gridy = 2; f.gridwidth = 2; f.fill = GridBagConstraints.NONE;
        JCheckBox cbShow = new JCheckBox("Show PIN");
        cbShow.addActionListener(e -> pfPin.setEchoChar(cbShow.isSelected() ? (char)0 : '*'));
        form.add(cbShow, f);

        f.gridy = 3;
        JButton btnLogin = new JButton("Login");
        btnLogin.setPreferredSize(new Dimension(160, 36));
        form.add(btnLogin, f);

        f.gridy = 4;
        JButton btnCreate = new JButton("Create Account");
        btnCreate.setPreferredSize(new Dimension(160, 34));
        form.add(btnCreate, f);

        JLabel hint = new JLabel("<html><i>Demo accounts: 10001 / 1234 &nbsp; | &nbsp; 10002 / 4321</i></html>", SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(8, 0, 0, 0));
        card.add(form, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 1.0;
        p.add(card, gbc);

        // Actions
        btnLogin.addActionListener(e -> {
            String acc = tfAccount.getText().trim();
            String pin = new String(pfPin.getPassword()).trim();
            if (acc.isEmpty() || pin.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter account number and PIN.", "Missing", JOptionPane.WARNING_MESSAGE);
                return;
            }
            UserAccount user = service.authenticate(acc, pin);
            if (user == null) {
                JOptionPane.showMessageDialog(frame, "Invalid credentials. Try demo accounts 10001/1234 or 10002/4321.", "Auth Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            this.currentUser = user;
            onLoginSuccess();
        });

        btnCreate.addActionListener(e -> {
            JPanel cp = new JPanel(new GridLayout(0,1,8,8));
            JTextField tAcc = new JTextField();
            JTextField tName = new JTextField();
            JPasswordField tPin = new JPasswordField();
            JTextField tInit = new JTextField();
            cp.add(new JLabel("Account Number (e.g., 10003):")); cp.add(tAcc);
            cp.add(new JLabel("Full Name:")); cp.add(tName);
            cp.add(new JLabel("PIN (4 digits):")); cp.add(tPin);
            cp.add(new JLabel("Initial deposit (number):")); cp.add(tInit);

            int r = JOptionPane.showConfirmDialog(frame, cp, "Create Account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            String na = tAcc.getText().trim(), nm = tName.getText().trim(), pi = new String(tPin.getPassword()).trim();
            double ini = 0;
            try { ini = Double.parseDouble(tInit.getText().trim()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Invalid initial amount.", "Error", JOptionPane.ERROR_MESSAGE); return; }
            if (na.isEmpty() || nm.isEmpty() || pi.isEmpty()) { JOptionPane.showMessageDialog(frame, "Fill all fields.", "Error", JOptionPane.ERROR_MESSAGE); return; }
            try {
                service.createAccount(na, nm, pi, ini);
                JOptionPane.showMessageDialog(frame, "Account created. You can now login.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to create: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        pfPin.addActionListener(e -> btnLogin.doClick()); // Enter triggers login

        return p;
    }

    private JPanel buildDashboardPanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));

        // Top bar
        JPanel top = new JPanel(new BorderLayout());
        JPanel info = new JPanel(new GridLayout(2,1));
        lblWelcome.setFont(lblWelcome.getFont().deriveFont(Font.BOLD, 16f));
        lblBalance.setFont(lblBalance.getFont().deriveFont(Font.PLAIN, 14f));
        info.add(lblWelcome); info.add(lblBalance);
        top.add(info, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnTheme = new JButton("Toggle Theme");
        JButton btnLogout = new JButton("Logout");
        JButton btnQuit = new JButton("Quit");
        controls.add(btnTheme); controls.add(btnLogout); controls.add(btnQuit);
        top.add(controls, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);

        // Center split: actions left, history right
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.36);

        JPanel ops = new JPanel();
        ops.setLayout(new BoxLayout(ops, BoxLayout.Y_AXIS));
        ops.setBorder(new EmptyBorder(10, 12, 10, 12));
        ops.add(Box.createVerticalStrut(6));

        JButton bDeposit = new JButton("Deposit");
        JButton bWithdraw = new JButton("Withdraw");
        JButton bTransfer = new JButton("Transfer");
        JButton bHistoryRefresh = new JButton("Refresh History");
        JButton bExport = new JButton("Export History (CSV)");
        JButton bAccountsList = new JButton("View All Accounts (Admin)");

        for (JButton b : Arrays.asList(bDeposit, bWithdraw, bTransfer, bHistoryRefresh, bExport, bAccountsList)) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            ops.add(b);
            ops.add(Box.createVerticalStrut(10));
        }
        split.setLeftComponent(new JScrollPane(ops));

        String[] cols = new String[] {"Date","Type","Amount","Note"};
        historyModel = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c){ return false; } };
        JTable tbl = new JTable(historyModel);
        tbl.setFillsViewportHeight(true);
        JScrollPane sp = new JScrollPane(tbl);
        split.setRightComponent(sp);

        p.add(split, BorderLayout.CENTER);

        // Bottom quick actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton bRefreshBalance = new JButton("Refresh Balance");
        bottom.add(bRefreshBalance);
        p.add(bottom, BorderLayout.SOUTH);

        // Actions wiring
        btnLogout.addActionListener(e -> { currentUser = null; cards.show(root, "login"); });
        btnQuit.addActionListener(e -> { if (JOptionPane.showConfirmDialog(frame, "Quit application?","Confirm",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) System.exit(0); });

        btnTheme.addActionListener(e -> toggleTheme());

        bRefreshBalance.addActionListener(e -> refreshBalance());
        bHistoryRefresh.addActionListener(e -> refreshHistory());
        bDeposit.addActionListener(e -> showDepositDialog());
        bWithdraw.addActionListener(e -> showWithdrawDialog());
        bTransfer.addActionListener(e -> showTransferDialog());
        bExport.addActionListener(e -> exportHistory());
        bAccountsList.addActionListener(e -> showAccountsList());

        return p;
    }

    private void onLoginSuccess() {
        lblWelcome.setText("Hello, " + currentUser.getName() + " — Acc: " + currentUser.getAccountNumber());
        refreshBalance(); refreshHistory();
        cards.show(root, "dashboard");
    }

    private void refreshBalance() {
        if (currentUser == null) return;
        lblBalance.setText("Available Balance: ₹ " + String.format("%.2f", currentUser.getBalance()));
    }

    private void refreshHistory() {
        if (currentUser == null) return;
        historyModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Transaction t : currentUser.getHistory()) {
            historyModel.addRow(new Object[]{ sdf.format(t.getTimestamp()), t.getType().name(), String.format("%.2f", t.getAmount()), t.getNote() });
        }
    }

    private void showDepositDialog() {
        if (currentUser == null) return;
        String s = JOptionPane.showInputDialog(frame, "Enter amount to deposit:", "Deposit", JOptionPane.PLAIN_MESSAGE);
        if (s == null) return;
        try {
            double amt = Double.parseDouble(s);
            service.deposit(currentUser, amt, "Self deposit");
            JOptionPane.showMessageDialog(frame, "Deposited ₹" + String.format("%.2f", amt));
            refreshBalance(); refreshHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Deposit failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showWithdrawDialog() {
        if (currentUser == null) return;
        String s = JOptionPane.showInputDialog(frame, "Enter amount to withdraw:", "Withdraw", JOptionPane.PLAIN_MESSAGE);
        if (s == null) return;
        try {
            double amt = Double.parseDouble(s);
            service.withdraw(currentUser, amt, "Cash withdrawal");
            JOptionPane.showMessageDialog(frame, "Withdrawal successful. (Simulated cash dispenser)");
            refreshBalance(); refreshHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Withdrawal failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showTransferDialog() {
        if (currentUser == null) return;
        JPanel p = new JPanel(new GridLayout(0,1,8,8));
        JTextField tfTo = new JTextField();
        JTextField tfAmt = new JTextField();
        p.add(new JLabel("Beneficiary Account Number:")); p.add(tfTo);
        p.add(new JLabel("Amount to Transfer:")); p.add(tfAmt);

        int r = JOptionPane.showConfirmDialog(frame, p, "Transfer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            String to = tfTo.getText().trim();
            double amt = Double.parseDouble(tfAmt.getText().trim());
            service.transfer(currentUser, to, amt);
            JOptionPane.showMessageDialog(frame, "Transferred ₹" + String.format("%.2f", amt) + " to " + to);
            refreshBalance(); refreshHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Transfer failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportHistory() {
        if (currentUser == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("history_" + currentUser.getAccountNumber() + ".csv"));
        int r = fc.showSaveDialog(frame);
        if (r != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Date,Type,Amount,Note");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (Transaction t : currentUser.getHistory()) {
                pw.printf("\"%s\",%s,%.2f,\"%s\"%n", sdf.format(t.getTimestamp()), t.getType().name(), t.getAmount(), t.getNote());
            }
            JOptionPane.showMessageDialog(frame, "Exported to " + f.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showAccountsList() {
        Collection<UserAccount> all = service.listAccounts();
        StringBuilder sb = new StringBuilder();
        for (UserAccount a : all) sb.append(a.toString()).append("\n");
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(560, 300));
        JOptionPane.showMessageDialog(frame, sp, "All Accounts", JOptionPane.PLAIN_MESSAGE);
    }

    /* ========== Theme helpers ========== */
    private void toggleTheme() {
        darkMode = !darkMode;
        setTheme(darkMode);
        SwingUtilities.updateComponentTreeUI(frame);
    }

    private void setTheme(boolean dark) {
        if (dark) {
            UIManager.put("control", new Color(45,45,45));
            UIManager.put("info", new Color(60,60,60));
            UIManager.put("nimbusBase", new Color(35,35,35));
            UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
            UIManager.put("nimbusDisabledText", new Color(130,130,130));
            UIManager.put("nimbusFocus", new Color(115,164,209));
            UIManager.put("nimbusSelectionBackground", new Color(75,110,140));
            UIManager.put("text", new Color(230,230,230));
            UIManager.put("Table.background", new Color(50,50,50));
            UIManager.put("Table.foreground", new Color(230,230,230));
            UIManager.put("Table.gridColor", new Color(70,70,70));
            UIManager.put("ScrollPane.background", new Color(45,45,45));
        } else {
            UIManager.put("control", null);
            UIManager.put("info", null);
            UIManager.put("nimbusBase", null);
            UIManager.put("nimbusAlertYellow", null);
            UIManager.put("nimbusDisabledText", null);
            UIManager.put("nimbusFocus", null);
            UIManager.put("nimbusSelectionBackground", null);
            UIManager.put("text", null);
            UIManager.put("Table.background", null);
            UIManager.put("Table.foreground", null);
            UIManager.put("Table.gridColor", null);
            UIManager.put("ScrollPane.background", null);
        }
        Font base = UIManager.getFont("Label.font");
        if (base != null) {
            UIManager.put("Label.font", base.deriveFont(13f));
            UIManager.put("Button.font", base.deriveFont(13f));
            UIManager.put("Table.font", base.deriveFont(12f));
        }
    }
}
