package bank;

import java.util.Scanner;

/** Command-line version of LumenBank. Uses the same Bank logic and database as the web app. */
public class Console {
    private static final class Quit extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Bank bank;
    private final Scanner in = new Scanner(System.in);

    Console(Bank bank) { this.bank = bank; }

    public static void main(String[] args) throws Exception {
        try (Bank bank = new Bank("jdbc:sqlite:lumenbank.db")) {
            new Console(bank).run();
        }
    }

    private String ask(String label) {
        System.out.print(label + "> ");
        if (!in.hasNextLine()) throw new Quit();   // Ctrl+D / end of input
        return in.nextLine().trim();
    }

    private static String money(long cents) { return String.format("$%d.%02d", cents / 100, cents % 100); }
    private static String groups(String n) { return n.replaceAll("(\\d{4})(?=\\d)", "$1 "); }

    void run() {
        try {
            while (true) {
                System.out.println("\n=== LumenBank ===\n1. Create an account\n2. Log into account\n0. Exit");
                switch (ask("")) {
                    case "1" -> create();
                    case "2" -> login();
                    case "0" -> throw new Quit();
                    default -> System.out.println("Invalid choice!");
                }
            }
        } catch (Quit q) {
            System.out.println("\nBye!");
        }
    }

    private void create() {
        String[] card = bank.register();
        System.out.println("\nYour card has been created");
        System.out.println("Your card number: " + groups(card[0]));
        System.out.println("Your card PIN:    " + card[1] + "   (save it, it is shown only once)");
    }

    private void login() {
        String number = ask("Enter your card number ").replace(" ", "");
        String pin = ask("Enter your PIN ");
        try {
            long id = bank.login(number, pin);
            System.out.println("You have successfully logged in!");
            account(id);
        } catch (Bank.BankException e) {
            System.out.println(e.getMessage() + "!");
        }
    }

    private void account(long id) {
        while (true) {
            System.out.println("\n1. Balance\n2. Add income\n3. Do transfer\n4. History\n5. Close account\n6. Log out\n0. Exit");
            try {
                switch (ask("")) {
                    case "1" -> System.out.println("Balance: " + money(bank.overview(id).balance()));
                    case "2" -> {
                        bank.deposit(id, Server.cents(ask("Enter income ")));
                        System.out.println("Income was added!");
                    }
                    case "3" -> {
                        String to = ask("Enter card number ").replace(" ", "");
                        bank.transfer(id, to, Server.cents(ask("Enter how much money you want to transfer ")));
                        System.out.println("Success!");
                    }
                    case "4" -> history(id);
                    case "5" -> {
                        if (ask("Type YES to close the account ").equals("YES")) {
                            bank.closeAccount(id);
                            System.out.println("The account has been closed!");
                            return;
                        }
                    }
                    case "6" -> { System.out.println("You have successfully logged out!"); return; }
                    case "0" -> throw new Quit();
                    default -> System.out.println("Invalid choice!");
                }
            } catch (Bank.BankException e) {
                System.out.println(e.getMessage() + "!");
            }
        }
    }

    private void history(long id) {
        var txs = bank.overview(id).history();
        if (txs.isEmpty()) { System.out.println("No operations yet."); return; }
        for (Bank.Tx t : txs) {
            boolean out = t.kind().equals("transfer_out");
            System.out.printf("%s  %-8s %10s  %s%n", t.at(), out ? "SENT" : t.kind().equals("deposit") ? "DEPOSIT" : "RECEIVED",
                    (out ? "-" : "+") + money(t.amount()), t.counterparty() == null ? "" : groups(t.counterparty()));
        }
    }
}
