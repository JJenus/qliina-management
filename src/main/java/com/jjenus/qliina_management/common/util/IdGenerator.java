// ./src/main/java/com/jjenus/qliina_management/common/util/IdGenerator.java
package com.jjenus.qliina_management.common.util;

import java.security.SecureRandom;

/**
 * Utility for generating short, human-readable, URL-safe identifiers
 * for laundry orders and items.
 *
 * Character set excludes: 0, O, 1, I (to avoid confusion in manual entry)
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Excludes visually confusable characters: 0, O, 1, I
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int DEFAULT_ITEM_LENGTH = 8;

    private IdGenerator() {
        // Prevent instantiation
    }

    /**
     * Core generator (unbiased).
     */
    public static String generate(int length) {
        int safeLength = Math.max(6, Math.min(12, length));
        StringBuilder sb = new StringBuilder(safeLength);

        for (int i = 0; i < safeLength; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        return sb.toString();
    }

    /**
     * Default generator (8 chars).
     */
    public static String generate() {
        return generate(DEFAULT_ITEM_LENGTH);
    }

    // =========================================================
    // ITEM IDS
    // =========================================================

    /**
     * Generates a short item ID for tagging clothes.
     * Example: "A7K3M9X2"
     */
    public static String generateItemId() {
        return generate(DEFAULT_ITEM_LENGTH);
    }

    /**
     * Generates an item ID with checksum for manual entry safety.
     * Example: "A7K3M9X2-5"
     */
    public static String generateItemIdWithChecksum() {
        String base = generateItemId();
        int check = computeCheckDigit(base);
        return base + "-" + check;
    }

    // =========================================================
    // MULTI-UNIT (BATCH) IDS
    // =========================================================

    /**
     * Generates the 8-char base shared by all units of one item line.
     * All unit barcodes for the same line share this base, so staff can
     * visually group them: "QL-FX78GLJ6-01", "QL-FX78GLJ6-02", …
     */
    public static String generateBatchBase() {
        return generate(DEFAULT_ITEM_LENGTH);
    }

    /**
     * Full scannable barcode for one unit within a multi-quantity item line.
     * Format: "QL-{batchBase}-{nn}" where nn is zero-padded 2-digit unit number.
     *
     * The 2-digit suffix distinguishes unit barcodes from single-item checksum
     * barcodes which use a single-digit suffix ("-0"…"-9").
     *
     * Example: generateUnitBarcode("FX78GLJ6", 3) → "QL-FX78GLJ6-03"
     */
    public static String generateUnitBarcode(String batchBase, int unitNum) {
        return "QL-" + batchBase + "-" + String.format("%02d", unitNum);
    }

    // =========================================================
    // ORDER IDS
    // =========================================================

    /**
     * Generates a hybrid order ID (time + randomness).
     * Example: "ORD-KY7Z3F-7XK2Q"
     */
    public static String generateOrderId() {
        String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomPart = generate(5);
        return "ORD-" + timePart + "-" + randomPart;
    }
    
    public static String generateTrackingId() {
        String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomPart = generate(5);
        return "TRK-" + timePart + "-" + randomPart;
    }

    /**
     * Generates a QR-code friendly identifier.
     * Example: "QL-A7K3-M9X2"
     */
    public static String generateQrCode(String type) {
      if (type != null && type.equalsIgnoreCase("item")){
        return "QL-" + generateItemIdWithChecksum();
      } else if (type != null && type.equalsIgnoreCase("order")) {
        return "QL-" + generateItemIdWithChecksum();
      }
        String part1 = generate(4);
        String part2 = generate(4);
        return String.format("QL-%s-%s", part1, part2);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    /**
     * Validates base ID (no checksum).
     */
    public static boolean isValid(String id) {
        if (id == null || id.isEmpty()) return false;
        return id.matches("^[" + CHARS + "]+$");
    }

    /**
     * Validates ID with checksum.
     */
    public static boolean isValidWithChecksum(String id) {
        if (id == null || !id.contains("-")) return false;

        String[] parts = id.split("-");
        if (parts.length != 2) return false;

        String base = parts[0];
        int expected;

        try {
            expected = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        return isValid(base) && computeCheckDigit(base) == expected;
    }

    // =========================================================
    // INTERNALS
    // =========================================================

    /**
     * Simple check digit (mod 10).
     */
    private static int computeCheckDigit(String input) {
        int sum = 0;
        for (char c : input.toCharArray()) {
            sum += CHARS.indexOf(c);
        }
        return sum % 10;
    }
}