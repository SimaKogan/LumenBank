package bank;

import java.security.SecureRandom;

/** Luhn checksum helpers for 16-digit card numbers. */
public final class Luhn {
    private Luhn() {}

    static int checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int d = body.charAt(i) - '0';
            if (i % 2 == 0) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean valid(String number) {
        return number != null && number.matches("\\d{16}")
                && checkDigit(number.substring(0, 15)) == number.charAt(15) - '0';
    }

    /** Generates a card number: BIN 400000 + 9 random digits + check digit. */
    public static String generate(SecureRandom rnd) {
        String body = "400000" + String.format("%09d", rnd.nextInt(1_000_000_000));
        return body + checkDigit(body);
    }
}
