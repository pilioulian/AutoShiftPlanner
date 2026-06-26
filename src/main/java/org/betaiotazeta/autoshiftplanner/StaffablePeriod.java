package org.betaiotazeta.autoshiftplanner;

/**
 * A problem fact: one (day, grain-of-day) period that can be staffed, i.e. not <em>every</em>
 * employee is forbidden there. Periods where all employees are forbidden are excluded, which is how
 * this implementation reproduces the legacy {@code bonus} (the legacy calculator penalizes
 * understaffing on every period and then adds back a bonus that exactly cancels the penalty for
 * fully-forbidden periods; excluding them up front is equivalent). See {@code CONSTRAINTS.md} §2.5.
 *
 * <p>{@code grainIndex} is the global period index ({@code day * columns + grainOfDay}), matching
 * {@link TimeGrain#getGrainIndex()} so coverage can be counted by joining shifts to time grains.
 */
public record StaffablePeriod(int dayOfWeek, int grainOfDay, int grainIndex) {

    /** True when the given shift (same day, grain span including this period) covers this period. */
    boolean coveredBy(ShiftAssignment shift) {
        if (shift.getTimeGrain() == null || shift.getShiftDuration() == null) {
            return false; // partially-assigned shift during solving: covers nothing
        }
        if (shift.getTimeGrain().getDay().getDayOfWeek() != dayOfWeek) {
            return false;
        }
        int start = shift.getTimeGrain().getStartingGrainOfDay();
        int end = start + shift.getShiftDuration().getDurationInGrains();
        return grainOfDay >= start && grainOfDay < end;
    }
}
