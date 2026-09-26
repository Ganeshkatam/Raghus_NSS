package edu.college.nss.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TokenHashUtilTest {

    @Test
    void sha256_withValidString_shouldReturn64CharHexDigest() {
        String input = "sample-refresh-token";
        String hash = TokenHashUtil.sha256(input);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[a-f0-9]{64}$"));
    }

    @Test
    void sha256_withSameString_shouldProduceDeterministicHash() {
        String token = "another-token-value-12345";
        String hash1 = TokenHashUtil.sha256(token);
        String hash2 = TokenHashUtil.sha256(token);

        assertEquals(hash1, hash2);
    }

    @Test
    void sha256_withNullInput_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TokenHashUtil.sha256(null));
    }
}
