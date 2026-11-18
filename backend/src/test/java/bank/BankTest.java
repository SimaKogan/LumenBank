package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankTest {
    Bank bank; long a, b; String an, bn;

    @BeforeEach void setUp() throws Exception {
        bank = new Bank("jdbc:sqlite::memory:");
        String[] x = bank.register(), y = bank.register();
        an = x[0]; bn = y[0];
        a = bank.login(x[0], x[1]); b = bank.login(y[0], y[1]);
    }

    @Test void transferMovesAllMoney() {
        bank.deposit(a, 10_000);
        bank.transfer(a, bn, 10_000);           // exact balance must work
        assertEquals(0, bank.overview(a).balance());
        assertEquals(10_000, bank.overview(b).balance());
        assertEquals("transfer_in", bank.overview(b).history().get(0).kind());
    }
    @Test void overdraftIsRejectedAndNothingChanges() {
        bank.deposit(a, 100);
        assertThrows(Bank.BankException.class, () -> bank.transfer(a, bn, 101));
        assertEquals(100, bank.overview(a).balance());
        assertEquals(0, bank.overview(b).balance());
    }
    @Test void selfTransferIsRejected() {
        bank.deposit(a, 100);
        assertThrows(Bank.BankException.class, () -> bank.transfer(a, an, 50));
    }
    @Test void nonPositiveAmountsAreRejected() {
        assertThrows(Bank.BankException.class, () -> bank.deposit(a, -500));
        assertThrows(Bank.BankException.class, () -> bank.deposit(a, 0));
    }
    @Test void wrongPinIsRejected() {
        assertThrows(Bank.BankException.class, () -> bank.login(an, "abcd"));
    }
    @Test void unknownValidCardIsRejected() {
        bank.deposit(a, 100);
        String ghost = Luhn.generate(new java.security.SecureRandom());
        assertThrows(Bank.BankException.class, () -> bank.transfer(a, ghost, 10));
    }
}
