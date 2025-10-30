/* ReservationApp.java
 *
 * Award-worthy Online Reservation System (single-file Swing application)
 * - Dark/Light theme
 * - Book tickets with auto PNR
 * - View / Search / Cancel by PNR
 * - CSV persistence (reservations.csv)
 * - Ticket preview & export
 *
 * Compile:
 *   javac ReservationApp.java
 * Run:
 *   java ReservationApp
 *
 * (Works with JDK 8+; no external libraries required)
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;
import java.util.function.Supplier;


public class ReservationApp {
    // file where reservations are stored
    private static final Path DATA_FILE = Paths.get("reservations.csv");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // In-memory reservation list
    private final List<Reservation> reservations = new ArrayList<>();

    // Trains sample (trainNo -> trainName)
    private final Map<String, String> trains = new LinkedHashMap<>();

    // Swing components (fields shared between methods)
    private JFrame frame;
    private JPanel root;
    private CardLayout cardLayout;
    private JTextField bookNameField, bookAgeField, bookFromField, bookToField, bookTrainNoField;
    private JComboBox<String> bookClassCombo, bookTrainSelect;
    private JSpinner bookDateSpinner;
    private JLabel bookPNRLabel, fareLabel;
    private JTextField searchPNRField;
    private JTable reservationsTable;
    private DefaultTableModel reservationsTableModel;
    private boolean darkTheme = true; // default

    // fonts & style
    private final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 22);
    private final Font HEADER_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    // Entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ReservationApp().start();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Startup error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // APPLICATION SETUP
    private void start() {
        seedTrains();
        loadReservationsFromFile();
        buildUI();
        applyTheme(); // set initial theme
        frame.setVisible(true);
    }

    private void seedTrains() {
        // sample trains, can be expanded; trainNo keys used
        trains.put("12001", "Vande Bharat Express");
        trains.put("12627", "Howrah Rajdhani");
        trains.put("11077", "Shatabdi Express");
        trains.put("12834", "Garib Rath Express");
        trains.put("16345", "Duronto Express");
        trains.put("TrainX", "Special Demo Local");
    }

    // DATA MODEL
    static class Reservation {
        final String pnr;
        final String name;
        final int age;
        final String trainNo;
        final String trainName;
        final String classType;
        final String from;
        final String to;
        final LocalDateTime bookingTime;
        final LocalDate travelDate;
        final double fare;
        final String status; // Booked or Cancelled
        final String cancelReason; // empty if not cancelled

        Reservation(String pnr, String name, int age, String trainNo, String trainName, String classType,
                    String from, String to, LocalDateTime bookingTime, LocalDate travelDate, double fare,
                    String status, String cancelReason) {
            this.pnr = pnr;
            this.name = name;
            this.age = age;
            this.trainNo = trainNo;
            this.trainName = trainName;
            this.classType = classType;
            this.from = from;
            this.to = to;
            this.bookingTime = bookingTime;
            this.travelDate = travelDate;
            this.fare = fare;
            this.status = status;
            this.cancelReason = cancelReason;
        }

        String toCSV() {
            return String.join(",",
                    escape(pnr),
                    escape(name),
                    String.valueOf(age),
                    escape(trainNo),
                    escape(trainName),
                    escape(classType),
                    escape(from),
                    escape(to),
                    bookingTime.format(DF),
                    travelDate.toString(),
                    String.valueOf(fare),
                    escape(status),
                    escape(cancelReason)
            );
        }

        static Reservation fromCSV(String line) {
            // naive CSV split (we didn't include commas in fields)
            String[] a = line.split(",", -1);
            if (a.length < 13) return null;
            try {
                return new Reservation(
                        a[0],
                        a[1],
                        Integer.parseInt(a[2]),
                        a[3],
                        a[4],
                        a[5],
                        a[6],
                        a[7],
                        LocalDateTime.parse(a[8], DF),
                        LocalDate.parse(a[9]),
                        Double.parseDouble(a[10]),
                        a[11],
                        a[12]
                );
            } catch (Exception e) {
                return null;
            }
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\n", " ").replace(",", " ");
        }
    }

    // UI BUILD
    private void buildUI() {
        frame = new JFrame("Venkat — Online Reservation System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(980, 640);
        frame.setLocationRelativeTo(null);

        root = new JPanel();
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        cardLayout = new CardLayout();
        root.setLayout(cardLayout);

        JPanel home = createHomePanel();
        JPanel book = createBookingPanel();
        JPanel view = createViewPanel();

        root.add(home, "home");
        root.add(book, "book");
        root.add(view, "view");

        frame.setContentPane(root);

        // top toolbar
        frame.setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBorder(new EmptyBorder(6, 8, 6, 8));

        JLabel title = new JLabel("Venkat - Reservation");
        title.setFont(TITLE_FONT);
        title.setBorder(new EmptyBorder(0, 6, 0, 20));
        mb.add(title);

        mb.add(Box.createHorizontalGlue());

        JButton homeBtn = makeToolbarButton("Home");
        homeBtn.addActionListener(e -> cardLayout.show(root, "home"));
        mb.add(homeBtn);

        JButton bookBtn = makeToolbarButton("Book Ticket");
        bookBtn.addActionListener(e -> { resetBookingForm(); cardLayout.show(root, "book"); });
        mb.add(bookBtn);

        JButton viewBtn = makeToolbarButton("View / Cancel");
        viewBtn.addActionListener(e -> { refreshReservationsTable(); cardLayout.show(root, "view"); });
        mb.add(viewBtn);

        JToggleButton themeToggle = new JToggleButton("Dark Theme");
        themeToggle.setSelected(darkTheme);
        themeToggle.addActionListener(e -> {
            darkTheme = themeToggle.isSelected();
            themeToggle.setText(darkTheme ? "Dark Theme" : "Light Theme");
            applyTheme();
        });
        mb.add(Box.createHorizontalStrut(12));
        mb.add(themeToggle);

        return mb;
    }

    private JButton makeToolbarButton(String text) {
        JButton b = new JButton(text);
        b.setFont(HEADER_FONT);
        b.setFocusPainted(false);
        b.setBorder(new CompoundBorder(new LineBorder(new Color(0,0,0,40)), new EmptyBorder(6,8,6,8)));
        return b;
    }

    // HOME panel with quick stats
    private JPanel createHomePanel() {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setOpaque(false);

        JLabel header = new JLabel("Dashboard");
        header.setFont(TITLE_FONT);

        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 12));
        stats.setOpaque(false);

        JPanel card1 = bigStatCard("Total Bookings", () -> String.valueOf(reservations.size()));
        JPanel card2 = bigStatCard("Active Tickets", () -> String.valueOf(reservations.stream().filter(r -> r.status.equals("Booked")).count()));
        JPanel card3 = bigStatCard("Cancelled", () -> String.valueOf(reservations.stream().filter(r -> r.status.equals("Cancelled")).count()));

        stats.add(card1); stats.add(card2); stats.add(card3);

        JTextArea info = new JTextArea("Welcome to your internship-ready Reservation System.\nUse the toolbar to Book tickets, Search PNR and Cancel.\nAll data stored in reservations.csv in the working folder.");
        info.setEditable(false);
        info.setFont(HEADER_FONT);
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(12,12,12,12));

        p.add(header, BorderLayout.NORTH);
        p.add(stats, BorderLayout.CENTER);
        p.add(info, BorderLayout.SOUTH);

        return p;
    }

    private JPanel bigStatCard(String title, Supplier<String> valueSupplier) {
        JPanel c = new JPanel(new BorderLayout());
        c.setBorder(new CompoundBorder(new LineBorder(new Color(0,0,0,30),1,true), new EmptyBorder(12,12,12,12)));
        c.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(HEADER_FONT);

        JLabel v = new JLabel(valueSupplier.get());
        v.setFont(new Font("Segoe UI", Font.BOLD, 24));
        v.setBorder(new EmptyBorder(6,0,0,0));

        // refresh label periodically when added to UI
      javax.swing.Timer timer = new javax.swing.Timer(700, e -> v.setText(valueSupplier.get()));
        timer.start();

        c.add(t, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    // BOOKING panel
    private JPanel createBookingPanel() {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setOpaque(false);

        JLabel header = new JLabel("Book a Ticket");
        header.setFont(TITLE_FONT);

        JPanel form = new JPanel();
        form.setLayout(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row=0;
        c.gridx=0; c.gridy=row; form.add(new JLabel("Passenger Name:"), c);
        bookNameField = new JTextField(); bookNameField.setToolTipText("Full name of passenger");
        c.gridx=1; c.gridy=row++; c.weightx=1; form.add(bookNameField, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Age:"), c);
        bookAgeField = new JTextField(); bookAgeField.setToolTipText("Age in years");
        c.gridx=1; c.gridy=row++; form.add(bookAgeField, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Train (select):"), c);
        bookTrainSelect = new JComboBox<>(trains.keySet().toArray(new String[0]));
        bookTrainSelect.setEditable(false);
        bookTrainSelect.addActionListener(e -> {
            String tn = (String) bookTrainSelect.getSelectedItem();
            bookTrainNoField.setText(tn);
            calculateFarePreview();
        });
        c.gridx=1; c.gridy=row++; form.add(bookTrainSelect, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Train No (auto):"), c);
        bookTrainNoField = new JTextField(); bookTrainNoField.setEditable(true);
        bookTrainNoField.setToolTipText("You can type a train number too; train name will show if found");
        bookTrainNoField.addFocusListener(new FocusAdapter(){
            public void focusLost(FocusEvent e) { calculateFarePreview(); }
        });
        c.gridx=1; c.gridy=row++; form.add(bookTrainNoField, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Class:"), c);
        bookClassCombo = new JComboBox<>(new String[] {"AC 1st", "AC 2-tier", "AC 3-tier", "Sleeper", "General"});
        c.gridx=1; c.gridy=row++; form.add(bookClassCombo, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("From:"), c);
        bookFromField = new JTextField(); c.gridx=1; c.gridy=row++; form.add(bookFromField, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("To:"), c);
        bookToField = new JTextField(); c.gridx=1; c.gridy=row++; form.add(bookToField, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Date of Journey:"), c);
        bookDateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor de = new JSpinner.DateEditor(bookDateSpinner, "yyyy-MM-dd");
        bookDateSpinner.setEditor(de);
        c.gridx=1; c.gridy=row++; form.add(bookDateSpinner, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Fare (preview):"), c);
        fareLabel = new JLabel("—");
        fareLabel.setFont(MONO);
        c.gridx=1; c.gridy=row++; form.add(fareLabel, c);

        // PNR display after booking
        c.gridx=0; c.gridy=row; form.add(new JLabel("Generated PNR:"), c);
        bookPNRLabel = new JLabel("—");
        bookPNRLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.gridx=1; c.gridy=row++; form.add(bookPNRLabel, c);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton calcFare = new JButton("Calculate Fare");
        calcFare.addActionListener(e -> calculateFarePreview());
        JButton bookBtn = new JButton("Book & Generate PNR");
        bookBtn.addActionListener(e -> doBooking());
        JButton exportBtn = new JButton("Export Sample CSV");
        exportBtn.addActionListener(e -> exportSampleCSV());
        actions.add(calcFare); actions.add(bookBtn); actions.add(exportBtn);

        p.add(header, BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);

        return p;
    }

    // VIEW / CANCEL panel
    private JPanel createViewPanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setOpaque(false);

        JLabel header = new JLabel("View / Cancel Reservations");
        header.setFont(TITLE_FONT);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(new JLabel("Search by PNR: "));
        searchPNRField = new JTextField(16);
        top.add(searchPNRField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> searchByPNR());
        top.add(searchBtn);

        JButton refresh = new JButton("Refresh Table");
        refresh.addActionListener(e -> refreshReservationsTable());
        top.add(refresh);

        // table
        reservationsTableModel = new DefaultTableModel(new Object[]{"PNR","Name","Train","Class","Date","Fare","Status"}, 0) {
            public boolean isCellEditable(int r,int c){return false;}
        };
        reservationsTable = new JTable(reservationsTableModel);
        reservationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationsTable.setRowHeight(28);
        JScrollPane scroll = new JScrollPane(reservationsTable);

        // table actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton viewBtn = new JButton("View Details");
        viewBtn.addActionListener(e -> viewSelectedReservation());
        JButton cancelBtn = new JButton("Cancel Selected");
        cancelBtn.addActionListener(e -> cancelSelectedReservation());
        bottom.add(viewBtn); bottom.add(cancelBtn);

        p.add(header, BorderLayout.NORTH);
        p.add(top, BorderLayout.AFTER_LINE_ENDS);
        p.add(scroll, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    // THEME
    private void applyTheme() {
        Color bg = darkTheme ? new Color(34,34,38) : Color.WHITE;
        Color fg = darkTheme ? Color.WHITE : Color.DARK_GRAY;
        Color panel = darkTheme ? new Color(44,44,48) : new Color(245,245,245);
        Color accent = darkTheme ? new Color(75,135,255) : new Color(20,110,230);

        frame.getContentPane().setBackground(bg);
        for (Component comp : getAllComponents(frame.getContentPane())) {
            styleComponent(comp, bg, fg, panel, accent);
        }
        // repaint
        frame.repaint();
    }

    private void styleComponent(Component comp, Color bg, Color fg, Color panel, Color accent) {
        comp.setFont(HEADER_FONT);
        if (comp instanceof JPanel) {
    comp.setBackground(panel);
    if (comp instanceof JComponent) {
        Border b = ((JComponent) comp).getBorder();
        if (b instanceof EmptyBorder) {
            // keep existing empty-border behavior (no-op)
        }
    }
}
 else if (comp instanceof JLabel) {
            comp.setForeground(fg);
        } else if (comp instanceof JButton) {
            JButton b = (JButton) comp;
            b.setBackground(accent);
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setBorder(new EmptyBorder(6,10,6,10));
        } else if (comp instanceof JTextField || comp instanceof JSpinner || comp instanceof JComboBox || comp instanceof JTable || comp instanceof JTextArea) {
            comp.setBackground(darkTheme ? new Color(60,60,64) : Color.WHITE);
            comp.setForeground(fg);
            if (comp instanceof JTextArea) ((JTextArea)comp).setOpaque(false);
            if (comp instanceof JTable) {
                JTable t = (JTable) comp;
                t.setBackground(darkTheme ? new Color(50,50,54) : Color.WHITE);
                t.setForeground(fg);
                t.getTableHeader().setBackground(panel);
                t.getTableHeader().setForeground(fg);
            }
        }
        if (comp instanceof Container) {
            for (Component c : ((Container)comp).getComponents()) styleComponent(c, bg, fg, panel, accent);
        }
    }

    private List<Component> getAllComponents(Container c) {
        List<Component> list = new ArrayList<>();
        for (Component comp : c.getComponents()) {
            list.add(comp);
            if (comp instanceof Container) list.addAll(getAllComponents((Container) comp));
        }
        return list;
    }

    // BOOKING LOGIC
    private void resetBookingForm() {
        bookNameField.setText("");
        bookAgeField.setText("");
        bookFromField.setText("");
        bookToField.setText("");
        bookTrainNoField.setText((String) bookTrainSelect.getSelectedItem());
        bookClassCombo.setSelectedIndex(0);
        bookPNRLabel.setText("—");
        fareLabel.setText("—");
    }

    private void calculateFarePreview() {
        try {
            String cls = (String) bookClassCombo.getSelectedItem();
            String trainNo = bookTrainNoField.getText().trim();
            // base fare by distance heuristics (demo): 100-1000 depending on train code and class
            int base = 300;
            if (trainNo.matches("\\d+")) base = 200 + (trainNo.hashCode() & 0xFF) % 800;
            double clsMultiplier = 1.0;
            switch (cls) {
                case "AC 1st": clsMultiplier = 3.2; break;
                case "AC 2-tier": clsMultiplier = 2.2; break;
                case "AC 3-tier": clsMultiplier = 1.6; break;
                case "Sleeper": clsMultiplier = 1.0; break;
                case "General": clsMultiplier = 0.6; break;
            }
            double fare = Math.round(base * clsMultiplier / 10.0) * 10.0;
            fareLabel.setText("₹ " + new DecimalFormat("#,##0").format(fare));
        } catch (Exception ex) {
            fareLabel.setText("—");
        }
    }

    private void doBooking() {
        String name = bookNameField.getText().trim();
        String ageStr = bookAgeField.getText().trim();
        String from = bookFromField.getText().trim();
        String to = bookToField.getText().trim();
        String trainNo = bookTrainNoField.getText().trim();
        String cls = (String) bookClassCombo.getSelectedItem();
        Date dt = (Date) bookDateSpinner.getValue();
        LocalDate travelDate = Instant.ofEpochMilli(dt.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();

        // validations
        if (name.length() < 2) { JOptionPane.showMessageDialog(frame, "Enter a valid name."); return; }
        int age = 0;
        try { age = Integer.parseInt(ageStr); if (age <= 0) throw new Exception(); } catch (Exception e) { JOptionPane.showMessageDialog(frame, "Enter a valid age."); return; }
        if (from.isEmpty() || to.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter from and to places."); return; }
        if (trainNo.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter train number or select from list."); return; }
        String trainName = trains.getOrDefault(trainNo, trains.getOrDefault((String)bookTrainSelect.getSelectedItem(), "Unknown Express"));

        calculateFarePreview();
        double fare;
        try {
            fare = Double.parseDouble(fareLabel.getText().replaceAll("[^0-9.]", ""));
        } catch (Exception ex) {
            fare = 350;
        }

        // generate pnr
        String pnr = generatePNR();
        LocalDateTime now = LocalDateTime.now();
        Reservation r = new Reservation(pnr, name, age, trainNo, trainName, cls, from, to, now, travelDate, fare, "Booked", "");
        reservations.add(r);
        saveReservationsToFile();

        bookPNRLabel.setText(pnr);
        JOptionPane.showMessageDialog(frame, "Booked! PNR: " + pnr + "\nYou can view/cancel from View tab.", "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // PNR generator (short human-friendly)
    private String generatePNR() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder b = new StringBuilder("PNR");
        for (int i=0;i<6;i++) b.append(chars.charAt(rnd.nextInt(chars.length())));
        // ensure uniqueness
        String s = b.toString();
        boolean exists = reservations.stream().anyMatch(r -> r.pnr.equals(s));
        if (exists) return generatePNR();
        return s;
    }

    // VIEW / CANCEL handlers
    private void refreshReservationsTable() {
        SwingUtilities.invokeLater(() -> {
            reservationsTableModel.setRowCount(0);
            for (Reservation r : reservations) {
                reservationsTableModel.addRow(new Object[]{
                        r.pnr, r.name, r.trainName + " (" + r.trainNo + ")", r.classType, r.travelDate.toString(), "₹" + (int)r.fare, r.status
                });
            }
        });
    }

    private void searchByPNR() {
        String q = searchPNRField.getText().trim().toUpperCase();
        if (q.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter PNR to search."); return; }
        List<Reservation> found = reservations.stream().filter(r -> r.pnr.equalsIgnoreCase(q)).collect(Collectors.toList());
        if (found.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "PNR not found: " + q, "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showReservationDialog(found.get(0));
    }

    private void viewSelectedReservation() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a row first."); return; }
        String pnr = (String) reservationsTableModel.getValueAt(row, 0);
        reservations.stream().filter(r -> r.pnr.equals(pnr)).findFirst().ifPresent(this::showReservationDialog);
    }

    private void showReservationDialog(Reservation r) {
        StringBuilder sb = new StringBuilder();
        sb.append("PNR: ").append(r.pnr).append("\n");
        sb.append("Name: ").append(r.name).append(" (Age ").append(r.age).append(")\n");
        sb.append("Train: ").append(r.trainName).append(" (").append(r.trainNo).append(")\n");
        sb.append("Class: ").append(r.classType).append("\n");
        sb.append("From → To: ").append(r.from).append(" → ").append(r.to).append("\n");
        sb.append("Journey Date: ").append(r.travelDate.toString()).append("\n");
        sb.append("Booked At: ").append(r.bookingTime.format(DF)).append("\n");
        sb.append("Fare: ₹").append((int)r.fare).append("\n");
        sb.append("Status: ").append(r.status).append("\n");
        if ("Cancelled".equalsIgnoreCase(r.status)) sb.append("Cancel Reason: ").append(r.cancelReason).append("\n");

        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(MONO);
        ta.setBorder(new EmptyBorder(8,8,8,8));
        int opt = JOptionPane.showOptionDialog(frame, new JScrollPane(ta), "Reservation Details",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[] {"Export Ticket", "Close"}, "Close");

        if (opt == 0) exportTicketToFile(r);
    }

    private void cancelSelectedReservation() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a reservation to cancel."); return; }
        String pnr = (String) reservationsTableModel.getValueAt(row, 0);
        Optional<Reservation> opt = reservations.stream().filter(rr -> rr.pnr.equals(pnr)).findFirst();
        if (!opt.isPresent()) { JOptionPane.showMessageDialog(frame, "Reservation not found."); return; }
        Reservation r = opt.get();
        if ("Cancelled".equalsIgnoreCase(r.status)) { JOptionPane.showMessageDialog(frame, "Already cancelled."); return; }
        String reason = JOptionPane.showInputDialog(frame, "Enter reason for cancellation (required):");
        if (reason == null || reason.trim().length() < 3) { JOptionPane.showMessageDialog(frame, "Cancellation aborted (reason required)."); return; }

        // replace reservation with cancelled copy
        Reservation cancelled = new Reservation(r.pnr, r.name, r.age, r.trainNo, r.trainName, r.classType, r.from, r.to, r.bookingTime, r.travelDate, r.fare, "Cancelled", reason.trim());
        int idx = reservations.indexOf(r);
        reservations.set(idx, cancelled);
        saveReservationsToFile();
        refreshReservationsTable();
        JOptionPane.showMessageDialog(frame, "Reservation cancelled and saved.");
    }

    // EXPORT ticket text
    private void exportTicketToFile(Reservation r) {
        try {
            String fname = r.pnr + "_ticket.txt";
            Path p = Paths.get(fname);
            List<String> lines = Arrays.asList(
                    "==================== VENKAT RAILWAYS ====================",
                    "PNR: " + r.pnr,
                    "Passenger: " + r.name + " (Age " + r.age + ")",
                    "Train: " + r.trainName + " (" + r.trainNo + ")",
                    "Class: " + r.classType,
                    "From: " + r.from + "   To: " + r.to,
                    "Journey Date: " + r.travelDate.toString(),
                    "Booked At: " + r.bookingTime.format(DF),
                    "Fare: ₹" + (int)r.fare,
                    "Status: " + r.status,
                    "Cancel Reason: " + (r.cancelReason == null ? "" : r.cancelReason),
                    "========================================================",
                    "Generated by Venkat's Reservation System"
            );
            Files.write(p, lines);
            Desktop.getDesktop().open(p.toFile()); // open with default text viewer
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
        }
    }

    private void exportSampleCSV() {
        try {
            Path p = Paths.get("reservations_sample_template.csv");
            List<String> lines = Arrays.asList(
                    "PNR,Name,Age,TrainNo,TrainName,Class,From,To,BookingTime,TravelDate,Fare,Status,CancelReason",
                    "PNREXAMPLE1,John Doe,30,12001,Vande Bharat Express,AC 3-tier,CityA,CityB,2025-10-30 19:46,2025-11-15,1200,Booked,",
                    "PNREXAMPLE2,Anita,28,12627,Howrah Rajdhani,Sleeper,CityX,CityY,2025-10-30 19:46,2025-12-01,550,Cancelled,User requested"
            );
            Files.write(p, lines);
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
        }
    }

    // FILE PERSISTENCE
    private void loadReservationsFromFile() {
        reservations.clear();
        if (!Files.exists(DATA_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(DATA_FILE);
            for (String ln : lines) {
                Reservation r = Reservation.fromCSV(ln);
                if (r != null) reservations.add(r);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not load reservations: " + e.getMessage());
        }
    }

    private void saveReservationsToFile() {
        try {
            List<String> lines = reservations.stream().map(Reservation::toCSV).collect(Collectors.toList());
            Files.write(DATA_FILE, lines);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not save reservations: " + e.getMessage());
        }
    }
}
