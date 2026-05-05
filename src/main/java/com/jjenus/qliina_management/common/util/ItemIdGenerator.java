
// ./src/main/java/com/jjenus/qliina_management/common/util/ItemIdGenerator.java
package com.jjenus.qliina_management.common.util;

import java.security.SecureRandom;

/**
 * Utility for generating short, human-readable, URL-safe identifiers
 * for order items suitable for QR codes and manual entry.
 *
 * Character set excludes: 0, O, 1, I (to avoid confusion in manual entry)
 * Length of 8 provides ~1.7 billion unique combinations.
 */
public final class ItemIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Excludes visually confusable characters: 0, O, 1, I
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_LENGTH = 8;

    private ItemIdGenerator() {
        // Prevent instantiation
    }

    /**
     * Generates a short, unique identifier.
     * Example output: "A7K3M9X2", "B5N8P4R6"
     *
     * @param length desired identifier length (min 6, max 12)
     * @return the generated identifier
     */
    public static String generate(int length) {
        int safeLength = Math.max(6, Math.min(12, length));
        StringBuilder sb = new StringBuilder(safeLength);
        byte[] bytes = new byte[safeLength];
        RANDOM.nextBytes(bytes);

        for (int i = 0; i < safeLength; i++) {
            sb.append(CHARS.charAt(Math.abs(bytes[i] % CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generates an 8-character identifier.
     */
    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * Generates a QR-code friendly identifier with a prefix for scanning context.
     * Format: QL-XXXX-XXXX (e.g., "QL-A7K3-M9X2")
     */
    public static String generateQrCode(String prefix) {
        String part1 = generate(4);
        String part2 = generate(4);
        return String.format("%s-%s-%s", prefix, part1, part2);
    }

    /**
     * Validates whether a string matches the expected short ID format.
     */
    public static boolean isValid(String id) {
        if (id == null || id.isEmpty()) return false;
        return id.matches("^[" + CHARS + "]+$");
    }
}