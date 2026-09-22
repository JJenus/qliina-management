package com.jjenus.qliina_management.complaint.model;

/**
 * Severity of a complaint. Each severity carries a target resolution SLA in
 * hours (LOW 72h, MEDIUM 48h, HIGH 24h, URGENT 8h).
 */
public enum ComplaintSeverity {

    LOW(72),
    MEDIUM(48),
    HIGH(24),
    URGENT(8);

    private final int slaHours;

    ComplaintSeverity(int slaHours) {
        this.slaHours = slaHours;
    }

    public int getSlaHours() {
        return slaHours;
    }
}