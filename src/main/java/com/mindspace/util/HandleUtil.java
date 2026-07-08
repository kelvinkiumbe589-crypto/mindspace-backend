package com.mindspace.util;

import java.util.function.Predicate;

/**
 * Rules and helpers for the public messaging username ("handle"). A handle is
 * distinct from a user's real name: it's lowercase, unique, and what other members
 * search by — so real names stay private.
 */
public final class HandleUtil {

    private HandleUtil() {}

    public static final String RULES =
            "Username must be 3–20 characters, using only lowercase letters, numbers, or underscores.";

    /** Validate a user-chosen handle; returns the normalized (lowercased) value or throws. */
    public static String requireValid(String raw) {
        if (raw == null) throw new IllegalArgumentException(RULES);
        String h = raw.trim().toLowerCase();
        if (!h.matches("[a-z0-9_]{3,20}")) throw new IllegalArgumentException(RULES);
        if (!h.matches(".*[a-z].*")) throw new IllegalArgumentException("Username must contain at least one letter.");
        return h;
    }

    public static boolean isValid(String raw) {
        try { requireValid(raw); return true; } catch (Exception e) { return false; }
    }

    /** Build a base handle from a real name (or email) for auto-generation. */
    public static String fromName(String name, String email) {
        String base = (name != null && !name.isBlank()) ? name
                : (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "";
        base = base.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.length() < 3) base = "user";
        if (!base.matches(".*[a-z].*")) base = "user" + base;
        if (base.length() > 20) base = base.substring(0, 20);
        return base;
    }

    /** Append a numeric suffix until the handle is free, per the supplied "is taken" test. */
    public static String makeUnique(String base, Predicate<String> taken) {
        if (!taken.test(base)) return base;
        for (int i = 2; ; i++) {
            String suffix = String.valueOf(i);
            String candidate = (base.length() + suffix.length() > 20
                    ? base.substring(0, 20 - suffix.length()) : base) + suffix;
            if (!taken.test(candidate)) return candidate;
        }
    }
}
