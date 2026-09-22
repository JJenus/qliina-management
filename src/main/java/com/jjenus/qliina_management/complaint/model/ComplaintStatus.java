package com.jjenus.qliina_management.complaint.model;

/**
 * Lifecycle of a complaint.
 * <ul>
 *   <li>OPEN — newly logged, awaiting triage</li>
 *   <li>IN_REVIEW — being worked on</li>
 *   <li>RESOLVED — closed with a resolution note</li>
 *   <li>REJECTED — closed without action (note still required)</li>
 * </ul>
 * RESOLVED / REJECTED are terminal.
 */
public enum ComplaintStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED
}