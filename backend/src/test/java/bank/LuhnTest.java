package bank;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class LuhnTest {
    @Test void generatedNumbersAreValid() {
        var rnd = new SecureRandom();
        for (int i = 0; i < 200; i++) assertTrue(Luhn.valid(Luhn.generate(rnd)));
    }
    @Test void detectsTypos() {
        String n = Luhn.generate(new SecureRandom());
        char last = n.charAt(15);
        String bad = n.substring(0, 15) + (last == '9' ? '0' : (char) (last + 1));
        assertFalse(Luhn.valid(bad));
        assertFalse(Luhn.valid("abc"));
        assertFalse(Luhn.valid(null));
    }
}
