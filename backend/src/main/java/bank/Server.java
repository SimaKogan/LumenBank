package bank;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny JSON-over-HTTP API using only the JDK (no frameworks). */
public class Server {
    private static final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Bank bank = new Bank("jdbc:sqlite:lumenbank.db");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/api/", ex -> {
            try { handle(bank, ex); }
            catch (Bank.BankException e) { send(ex, 400, obj("error", e.getMessage())); }
            catch (Exception e) { e.printStackTrace(); send(ex, 500, obj("error", "Server error")); }
        });
        server.start();
        System.out.println("LumenBank API running on http://localhost:8080");
    }

    static void handle(Bank bank, HttpExchange ex) throws IOException {
        String route = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (route.equals("POST /api/register")) {
            String[] card = bank.register();
            send(ex, 200, obj("number", card[0], "pin", card[1]));
            return;
        }
        if (route.equals("POST /api/login")) {
            long id = bank.login(field(body, "number"), field(body, "pin"));
            String token = UUID.randomUUID().toString();
            sessions.put(token, id);
            send(ex, 200, obj("token", token));
            return;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        Long id = auth == null ? null : sessions.get(auth.replace("Bearer ", ""));
        if (id == null) { send(ex, 401, obj("error", "Not authorized")); return; }

        switch (route) {
            case "GET /api/me" -> send(ex, 200, json(bank.overview(id)));
            case "POST /api/deposit" -> { bank.deposit(id, cents(field(body, "amount"))); send(ex, 200, "{\"ok\":true}"); }
            case "POST /api/transfer" -> {
                bank.transfer(id, field(body, "to"), cents(field(body, "amount")));
                send(ex, 200, "{\"ok\":true}");
            }
            case "DELETE /api/account" -> {
                bank.closeAccount(id);
                sessions.values().remove(id);
                send(ex, 200, "{\"ok\":true}");
            }
            default -> send(ex, 404, obj("error", "Not found"));
        }
    }

    /** "12.34" -> 1234 cents. Rejects junk, negative values and more than 2 decimals. */
    static long cents(String s) {
        try {
            long v = new BigDecimal(s.trim()).movePointRight(2).longValueExact();
            if (v > 100_000_000_00L) throw new Bank.BankException("Amount is too large");
            return v;
        } catch (RuntimeException e) {
            if (e instanceof Bank.BankException be) throw be;
            throw new Bank.BankException("Invalid amount");
        }
    }

    static String field(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]*)\"?").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    static String obj(String... kv) {
        var sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(esc(kv[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    static String json(Bank.Overview o) {
        var sb = new StringBuilder("{\"number\":\"").append(o.number()).append("\",\"balance\":").append(o.balance())
                .append(",\"createdAt\":\"").append(esc(o.createdAt())).append("\",\"history\":[");
        for (int i = 0; i < o.history().size(); i++) {
            Bank.Tx t = o.history().get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"kind\":\"").append(t.kind()).append("\",\"amount\":").append(t.amount())
              .append(",\"counterparty\":").append(t.counterparty() == null ? "null" : "\"" + esc(t.counterparty()) + "\"")
              .append(",\"at\":\"").append(esc(t.at())).append("\"}");
        }
        return sb.append("]}").toString();
    }

    static void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }
}
