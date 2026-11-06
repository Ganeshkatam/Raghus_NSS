package edu.college.nss.security;

import java.nio.CharBuffer;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/]).{12,128}$";

    public static final String COMPLEXITY_MESSAGE =
        "Password must be between 12 and 128 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character.";

    public static final Pattern PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {
        // Utility class
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH || !PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(COMPLEXITY_MESSAGE);
        }
    }

    public static void validate(char[] password) {
        if (password == null || password.length < MIN_LENGTH || password.length > MAX_LENGTH) {
            throw new IllegalArgumentException(COMPLEXITY_MESSAGE);
        }
        CharBuffer buffer = CharBuffer.wrap(password);
        if (!PATTERN.matcher(buffer).matches()) {
            throw new IllegalArgumentException(COMPLEXITY_MESSAGE);
        }
    }
}

