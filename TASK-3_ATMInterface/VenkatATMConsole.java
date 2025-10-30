// File: VenkatATMConsole.java
// Polished ATM console app - 5 core functions, persistence, mini-statement CSV export.
// Compile: javac VenkatATMConsole.java
// Run: java VenkatATMConsole

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

public class VenkatATMConsole {

    public static void main(String[] args) {
        Bank bank = new Bank("bank_store.dat");
        bank.seedDemo();
        new ATMApp(bank).run();
    }

    static class ATMApp {
        private final Bank bank;
        private final Scanner scanner = new Scanner(System.in);

        ATMApp(Bank bank) { this.bank = bank; }

        void run() {
            System.out.println("=== Venkat ATM Console ===");
            while (true) {
                System.out.println("\n1) Login  2) Exit");
                System.out.print("Choose: ");
                String choice = scanner.nextLine().trim();
                if ("1".equals(choice)) {
                    Optional<Account> accOpt = authenticate();
                    accOpt.ifPresent(this::session);
                } else if ("2".equals(choice) || "exit".equalsIgnoreCase(choice)) {
                    System.out.println("Goodbye.");
                    bank.save();
                    break;
                } else System.out.println("Invalid.");
            }
        }

        Optional<Account> authenticate() {
            System.out.print("Enter User ID: ");
            String uid = scanner.nextLine().trim();
            Optional<Account> opt = bank.find(uid);
            if (!opt.isPresent()) { System.out.println("No such user."); return Optional.empty(); }
            Account acc = opt.get();
            int attempts = 0;
            while (attempts < 3) {
                String pin = readMasked("Enter PIN: ");
                if (pin==null) return Optional.empty();
                if (acc.verifyPin(pin)) { System.out.println("Welcome " + acc.name); return Optional.of(acc); }
                else { attempts++; System.out.println("Invalid PIN (" + attempts + "/3)"); }
            }
            System.out.println("Too many failed attempts.");
            return Optional.empty();
        }

        String readMasked(String prompt) {
            Console console = System.console();
            if (console != null) {
                char[] pw = console.readPassword(prompt);
                return pw==null?null:new String(pw);
            } else {
                System.out.print(prompt);
                return scanner.nextLine();
            }
        }

        void session(Account acc) {
            while (true) {
                System.out.println("\n--- Menu ---");
                System.out.println("1) Transactions History\n2) Withdraw\n3) Deposit\n4) Transfer\n5) Mini-statement CSV\n6) Change PIN\n7) Logout");
                System.out.printf("Balance: ₹%.2f\nChoose: ", acc.balance);
                String sel = scanner.nextLine().trim();
                switch (sel) {
                    case "1": showHistory(acc); break;
                    case "2": doWithdraw(acc); break;
                    case "3": doDeposit(acc); break;
                    case "4": doTransfer(acc); break;
                    case "5": exportCSV(acc); break;
                    case "6": changePin(acc); break;
                    case "7": bank.save(); System.out.println("Logged out."); return;
                    default: System.out.println("Invalid option.");
                }
            }
        }

        void showHistory(Account acc) {
            if (acc.tx.isEmpty()) { System.out.println("No transactions yet."); return; }
            System.out.println("Transactions:");
            acc.tx.forEach(t -> System.out.println(t));
        }

        void doWithdraw(Account acc) {
            System.out.print("Amount to withdraw: ₹");
            String s = scanner.nextLine().trim();
            double amt;
            try { amt = Double.parseDouble(s); } catch (Exception e){ System.out.println("Invalid"); return; }
            if (amt<=0) { System.out.println("Must be >0"); return; }
            boolean ok = acc.withdraw(amt);
            if (ok) { System.out.printf("Withdrawn ₹%.2f. New balance ₹%.2f\n", amt, acc.balance); bank.save(); } else System.out.println("Failed (insufficient or invalid).");
        }

        void doDeposit(Account acc) {
            System.out.print("Amount to deposit: ₹");
            String s = scanner.nextLine().trim();
            double amt;
            try { amt = Double.parseDouble(s); } catch (Exception e){ System.out.println("Invalid"); return; }
            if (amt<=0) { System.out.println("Must be >0"); return; }
            acc.deposit(amt); bank.save();
            System.out.printf("Deposited ₹%.2f. New balance ₹%.2f\n", amt, acc.balance);
        }

        void doTransfer(Account acc) {
            System.out.print("Target User ID: ");
            String tgt = scanner.nextLine().trim();
            if (tgt.equals(acc.userId)) { System.out.println("Cannot transfer to self."); return; }
            Optional<Account> t = bank.find(tgt);
            if (!t.isPresent()) { System.out.println("Target not found."); return; }
            System.out.print("Amount to transfer: ₹");
            String s = scanner.nextLine().trim();
            double amt;
            try { amt = Double.parseDouble(s); } catch (Exception e){ System.out.println("Invalid"); return; }
            if (amt<=0) { System.out.println("Must be >0"); return; }
            if (acc.transferTo(t.get(), amt)) { bank.save(); System.out.printf("Transferred ₹%.2f to %s\n", amt, t.get().name); }
            else System.out.println("Transfer failed.");
        }

        void exportCSV(Account acc) {
            Path out = Paths.get("mini_statement_" + acc.userId + ".csv");
            try (BufferedWriter bw = Files.newBufferedWriter(out)) {
                bw.write("timestamp,type,amount,balance,note\n");
                for (String line : acc.tx) {
                    bw.write(line + "\n");
                }
                System.out.println("Mini-statement exported to " + out.toAbsolutePath());
            } catch (Exception e) { System.out.println("Export failed: " + e.getMessage()); }
        }

        void changePin(Account acc) {
            String oldp = readMasked("Enter current PIN: ");
            if (!acc.verifyPin(oldp)) { System.out.println("Incorrect current PIN"); return; }
            String newp = readMasked("Enter new PIN: ");
            if (newp==null || newp.length()<3) { System.out.println("Too short"); return; }
            acc.setPin(newp); bank.save(); System.out.println("PIN changed.");
        }
    }

    static class Bank {
        final Path file;
        Map<String, Account> accounts = new HashMap<>();
        Bank(String filename) {
            file = Paths.get(filename);
            load();
        }
        void load() {
            if (!Files.exists(file)) return;
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
                accounts = (Map<String, Account>) ois.readObject();
            } catch (Exception e) { accounts = new HashMap<>(); }
        }
        void save() {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file))) {
                oos.writeObject(accounts);
            } catch (Exception e) { System.out.println("Save failed: " + e.getMessage()); }
        }
        Optional<Account> find(String id) { return Optional.ofNullable(accounts.get(id)); }
        void put(Account a) { accounts.put(a.userId, a); save(); }
        void seedDemo() {
            if (!accounts.isEmpty()) return;
            Account a1 = new Account("1001","Venkat R","1234",5000);
            Account a2 = new Account("1002","Priya K","4321",3000);
            accounts.put(a1.userId,a1); accounts.put(a2.userId,a2);
            save();
        }
    }

    static class Account implements Serializable {
        final String userId;
        final String name;
        private String pinHash;
        double balance;
        final List<String> tx = new ArrayList<>(); // CSV lines for simplicity

        Account(String userId, String name, String pin, double initial) {
            this.userId=userId; this.name=name; setPin(pin); this.balance=initial;
            if (initial>0) record("DEPOSIT", initial, "Initial deposit");
        }

        boolean verifyPin(String pin) { return Objects.equals(pinHash, hash(pin)); }
        void setPin(String pin) { this.pinHash = hash(pin); }

        private static String hash(String s) {
            // simple hash for demo - not cryptographically secure
            return Integer.toHexString(Objects.hashCode(s));
        }

        synchronized void deposit(double amt) {
            balance += amt; record("DEPOSIT", amt, "");
        }
        synchronized boolean withdraw(double amt) {
            if (amt<=0 || amt>balance) return false;
            balance -= amt; record("WITHDRAW", amt, "");
            return true;
        }
        synchronized boolean transferTo(Account other, double amt) {
            if (withdraw(amt)) {
                other.deposit(amt);
                record("TRANSFER_OUT", amt, "To:"+other.userId);
                other.record("TRANSFER_IN", amt, "From:"+this.userId);
                return true;
            }
            return false;
        }

        void record(String type, double amt, String note) {
            String line = String.format("%s,%s,%.2f,%.2f,%s",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), type, amt, balance, note==null?"":note);
            tx.add(line);
        }
        @Override public String toString() { return userId + " - " + name + " (₹" + String.format("%.2f", balance) + ")"; }
    }
}
