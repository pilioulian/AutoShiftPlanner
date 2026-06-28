package org.betaiotazeta.autoshiftplanner;

/**
 * A problem fact derived from the {@code tableScore} grid for use by {@link AspConstraintProvider}.
 *
 * <p>Identifies one half-hour cell belonging to a specific employee on a specific day. Carries the
 * actual {@link Employee} reference (not its index) so Constraint Streams can match it to a
 * {@link ShiftAssignment} by object identity. A cell is "covered" by a shift when the shift is for
 * the same employee and day and its grain span includes {@link #grainOfDay()}.
 *
 * <p>Used for the forbidden (§2.8) and mandatory (§2.7) constraints; see {@code CONSTRAINTS.md}.
 * {@code forbidden} distinguishes the two kinds within the single fact collection.
 */
public record GridCell(Employee employee, int dayOfWeek, int grainOfDay, boolean forbidden) {

    /** True when a shift for this cell's employee/day with the given grain span covers this cell. */
    boolean coveredBy(ShiftAssignment shift) {
        if (shift.getTimeGrain() == null || shift.getShiftDuration() == null) {
            return false; // partially-assigned shift during solving: covers nothing
        }
        if (shift.getShift().getEmployee() != employee) {
            return false;
        }
        if (shift.getTimeGrain().getDay().getDayOfWeek() != dayOfWeek) {
            return false;
        }
        int start = shift.getTimeGrain().getStartingGrainOfDay();
        int end = start + shift.getShiftDuration().getDurationInGrains();
        return grainOfDay >= start && grainOfDay < end;
    }
}
