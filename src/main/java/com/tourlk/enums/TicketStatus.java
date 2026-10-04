package com.tourlk.enums;

/**
 * Lifecycle status of a support ticket.
 * <p>
 * OPEN -&gt; IN_PROGRESS -&gt; RESOLVED -&gt; CLOSED, with WAITING_FOR_USER
 * (an admin has replied and the raiser's answer is awaited) and WITHDRAWN
 * (the raiser abandoned it before it was resolved).
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    CLOSED,
    WITHDRAWN
}
