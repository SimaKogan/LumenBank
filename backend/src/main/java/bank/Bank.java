package bank;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Business logic + SQL layer. Amounts are stored in cents. PINs are PBKDF2-hashed. */
public class Bank implements AutoCloseable {
    public static class BankException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public BankException(String message) { super(message); }
    }
    public record Tx(String kind, long amount, String counterparty, String at) {}
    public record Overview(String number, long balance, String createdAt, List<Tx> history) {}

    private interface Work { void run() throws SQLException; }

    private final Connection c;
    private final SecureRandom rnd = new SecureRandom();

    public Bank(String jdbcUrl) throws SQLException {
        c = DriverManager.getConnection(jdbcUrl);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("""
                CREATE TABLE IF NOT EXISTS accounts(
                  id INTEGER PRIMARY KEY,
                  number TEXT NOT NULL UNIQUE,
                  pin_hash TEXT NOT NULL,
                  salt TEXT NOT NULL,
                  balance INTEGER NOT NULL DEFAULT 0 CHECK(balance >= 0),
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS tx(
                  id INTEGER PRIMARY KEY,
                  account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                  kind TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  counterparty TEXT,
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)""");
        }
    }

    private String hash(String pin, byte[] salt) {
        try {
            var spec = new PBEKeySpec(pin.toCharArray(), salt, 65_536, 256);
            return HexFormat.of().formatHex(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private int exec(String sql, Object... args) throws SQLException {
        try (var p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p.executeUpdate();
        }
    }

    /** Runs work as a single DB transaction: all or nothing. */
    private void atomic(Work w) {
        try {
            c.setAutoCommit(false);
            try { w.run(); c.commit(); }
            catch (Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    /** Creates an account; returns {cardNumber, pin}. The PIN is never stored in clear text. */
    public synchronized String[] register() {
        for (int i = 0; i < 20; i++) {
            String number = Luhn.generate(rnd);
            String pin = String.format("%04d", rnd.nextInt(10_000));
            byte[] salt = new byte[16];
            rnd.nextBytes(salt);
            try {
                exec("INSERT INTO accounts(number, pin_hash, salt) VALUES(?,?,?)",
                        number, hash(pin, salt), HexFormat.of().formatHex(salt));
                return new String[]{number, pin};
            } catch (SQLException e) {
                if (!String.valueOf(e.getMessage()).contains("UNIQUE")) throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Could not allocate a card number");
    }

    /** Returns the account id if number + PIN match. */
    public synchronized long login(String number, String pin) {
        try (var p = c.prepareStatement("SELECT id, pin_hash, salt FROM accounts WHERE number=?")) {
            p.setString(1, number == null ? "" : number);
            var r = p.executeQuery();
            if (r.next()) {
                String h = hash(pin == null ? "" : pin, HexFormat.of().parseHex(r.getString(3)));
                if (MessageDigest.isEqual(h.getBytes(), r.getString(2).getBytes())) return r.getLong(1);
            }
            throw new BankException("Wrong card number or PIN");
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public synchronized void deposit(long id, long cents) {
        if (cents <= 0) throw new BankException("Amount must be positive");
        atomic(() -> {
            exec("UPDATE accounts SET balance = balance + ? WHERE id=?", cents, id);
            exec("INSERT INTO tx(account_id, kind, amount) VALUES(?, 'deposit', ?)", id, cents);
        });
    }

    public synchronized void transfer(long fromId, String toNumber, long cents) {
        if (cents <= 0) throw new BankException("Amount must be positive");
        if (!Luhn.valid(toNumber)) throw new BankException("Invalid card number, please check it");
        atomic(() -> {
            String fromNumber; long balance;
            try (var p = c.prepareStatement("SELECT number, balance FROM accounts WHERE id=?")) {
                p.setLong(1, fromId);
                var r = p.executeQuery();
                r.next();
                fromNumber = r.getString(1);
                balance = r.getLong(2);
            }
            if (fromNumber.equals(toNumber)) throw new BankException("You can't transfer money to the same account");
            long toId;
            try (var p = c.prepareStatement("SELECT id FROM accounts WHERE number=?")) {
                p.setString(1, toNumber);
                var r = p.executeQuery();
                if (!r.next()) throw new BankException("Such a card does not exist");
                toId = r.getLong(1);
            }
            if (balance < cents) throw new BankException("Not enough money");
            exec("UPDATE accounts SET balance = balance - ? WHERE id=?", cents, fromId);
            exec("UPDATE accounts SET balance = balance + ? WHERE id=?", cents, toId);
            exec("INSERT INTO tx(account_id, kind, amount, counterparty) VALUES(?, 'transfer_out', ?, ?)", fromId, cents, toNumber);
            exec("INSERT INTO tx(account_id, kind, amount, counterparty) VALUES(?, 'transfer_in', ?, ?)", toId, cents, fromNumber);
        });
    }

    public synchronized Overview overview(long id) {
        try {
            String number; long balance; String created;
            try (var p = c.prepareStatement("SELECT number, balance, created_at FROM accounts WHERE id=?")) {
                p.setLong(1, id);
                var r = p.executeQuery();
                if (!r.next()) throw new BankException("Account not found");
                number = r.getString(1); balance = r.getLong(2); created = r.getString(3);
            }
            List<Tx> history = new ArrayList<>();
            try (var p = c.prepareStatement(
                    "SELECT kind, amount, counterparty, created_at FROM tx WHERE account_id=? ORDER BY id DESC LIMIT 50")) {
                p.setLong(1, id);
                var r = p.executeQuery();
                while (r.next()) history.add(new Tx(r.getString(1), r.getLong(2), r.getString(3), r.getString(4)));
            }
            return new Overview(number, balance, created, history);
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public synchronized void closeAccount(long id) {
        try { exec("DELETE FROM accounts WHERE id=?", id); }
        catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override public void close() throws SQLException { c.close(); }
}
