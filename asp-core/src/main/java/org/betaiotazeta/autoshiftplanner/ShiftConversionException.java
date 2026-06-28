package org.betaiotazeta.autoshiftplanner;

/**
 * Thrown by {@link TableToShiftConverter} when a worked region of the table cannot be turned into a
 * shift assignment given the current constraint settings (e.g. a run whose length matches no
 * {@link ShiftDuration}, or no free {@link ShiftAssignment} for the employee). The message is
 * user-facing; the Swing layer surfaces it as a warning dialog.
 */
public class ShiftConversionException extends Exception {

    public ShiftConversionException(String message) {
        super(message);
    }
}
