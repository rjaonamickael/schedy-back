package com.schedy.exception;

/**
 * Thrown when a clock-in attempt is rejected by the creneau guard.
 *
 * <p>The {@link #getMessage() message} is a single generic user-facing string
 * ("Pointage non autorisé actuellement") so that an attacker standing at the
 * kiosk cannot distinguish between "wrong PIN", "wrong site", "not scheduled
 * now" and other failure modes. The concrete reason is preserved in
 * {@link #getReason()} for server-side audit logs only.
 */
public class ClockInNotAuthorizedException extends RuntimeException {

    /** Machine-readable reason codes for audit logs. Never sent to the client. */
    public enum Reason {
        /** No creneau assigned to this employee at this site today. */
        NO_CRENEAU_TODAY,
        /** A creneau exists but the current time falls outside the tolerance window. */
        OUTSIDE_TOLERANCE_WINDOW,
        /** The resolved employee does not belong to the target site. */
        WRONG_SITE,
        /** The PIN did not match any employee for this site. */
        PIN_INVALID,
        /** Defensive fallback for unexpected rejections. */
        UNKNOWN
    }

    /** Single generic user-facing message — identical for all reasons. */
    public static final String GENERIC_MESSAGE = "Pointage non autorisé actuellement";

    private final Reason reason;

    public ClockInNotAuthorizedException(Reason reason) {
        super(GENERIC_MESSAGE);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
