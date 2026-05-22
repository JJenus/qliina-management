package com.jjenus.qliina_management.common;

/**
 * PII masking helpers for SUPPORT_AGENT and data-export scenarios.
 * All methods are null-safe and return "—" for null/blank input.
 */
public final class MaskingUtils {

    private MaskingUtils() {}

    /**
     * Mask email: john.doe@example.com → j***@e***.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "—";
        int atIdx = email.indexOf('@');
        if (atIdx < 0) return "***";
        String local  = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);
        int dotIdx = domain.lastIndexOf('.');
        String domainMain = dotIdx > 0 ? domain.substring(0, dotIdx) : domain;
        String tld        = dotIdx > 0 ? domain.substring(dotIdx)    : "";
        return first(local) + "***@" + first(domainMain) + "***" + tld;
    }

    /**
     * Mask phone: +2348012345678 → +234***5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "—";
        String digits = phone.replaceAll("[^+\\d]", "");
        if (digits.length() < 7) return "***";
        String prefix = digits.length() > 6 ? digits.substring(0, Math.min(4, digits.length() - 4)) : "";
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "***" + suffix;
    }

    /**
     * Mask name: John Doe → J*** D***
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "—";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(first(part)).append("***");
        }
        return sb.toString();
    }

    /**
     * Mask card PAN: 4111111111111234 → ****1234
     */
    public static String maskCard(String pan) {
        if (pan == null || pan.isBlank()) return "—";
        String digits = pan.replaceAll("[^\\d]", "");
        if (digits.length() < 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }

    private static String first(String s) {
        if (s == null || s.isEmpty()) return "";
        return String.valueOf(s.charAt(0));
    }
}
