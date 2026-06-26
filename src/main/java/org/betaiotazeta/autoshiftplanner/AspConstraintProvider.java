package org.betaiotazeta.autoshiftplanner;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.sum;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;

/**
 * Constraint Streams re-implementation of {@link AspEasyScoreCalculator}.
 *
 * <p><b>Semantics: per-shift, feasibility-equivalent</b> (see {@code CONSTRAINTS.md}). Unlike the
 * legacy calculator, which rasterizes shifts onto a grid and counts merged contiguous runs, this
 * provider reasons over {@link ShiftAssignment} entities directly so the solver can score
 * incrementally. Exact penalty magnitudes therefore diverge from the legacy calculator where shifts
 * overlap or abut; the migration's acceptance gate is feasibility agreement (a schedule reaches
 * {@code hardScore == 0} here iff it does under the legacy calculator), verified by solving with
 * this provider and grading the result with the legacy calculator.
 *
 * <p>{@code forEach(ShiftAssignment.class)} only yields assignments whose planning variables are
 * both non-null, matching the legacy {@code timeGrain != null && shiftDuration != null} guard.
 * Constraint thresholds and enable/disable flags are read from the single {@link Configurator}
 * problem fact, joined into each stream.
 */
public class AspConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
            shiftLength(factory),
            hoursPerWeek(factory),
            hoursPerDay(factory),
            shiftsPerDay(factory),
        };
    }

    // CONSTRAINTS.md §2.4 — each shift's duration must lie within [min, max] grains; penalty is the
    // distance outside the band (two-sided). Per shift (not per merged run).
    Constraint shiftLength(ConstraintFactory factory) {
        return factory.forEach(ShiftAssignment.class)
                .join(Configurator.class)
                .filter((sa, cfg) -> cfg.isShiftLenghtCheck())
                .filter((sa, cfg) -> {
                    int k = sa.getShiftDuration().getDurationInGrains();
                    return k < grains(cfg.getShiftLenghtMin()) || k > grains(cfg.getShiftLenghtMax());
                })
                .penalize(HardSoftScore.ONE_HARD, (sa, cfg) -> {
                    int k = sa.getShiftDuration().getDurationInGrains();
                    int min = grains(cfg.getShiftLenghtMin());
                    int max = grains(cfg.getShiftLenghtMax());
                    return k < min ? (min - k) : (k - max);
                })
                .asConstraint("Shift length");
    }

    // CONSTRAINTS.md §2.1 — total grains worked per employee must equal hoursPerWeek (two-sided).
    Constraint hoursPerWeek(ConstraintFactory factory) {
        return factory.forEach(ShiftAssignment.class)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sum(sa -> sa.getShiftDuration().getDurationInGrains()))
                .join(Configurator.class)
                .filter((employee, workedGrains, cfg) -> cfg.isHoursPerWeekCheck())
                .penalize(HardSoftScore.ONE_HARD,
                        (employee, workedGrains, cfg) ->
                                Math.abs(employee.getHoursPerWeek() * 2 - workedGrains))
                .asConstraint("Hours per week");
    }

    // CONSTRAINTS.md §2.6 — grains worked per (employee, day) must not exceed hoursPerDay; the
    // overage is penalized double (one-sided).
    Constraint hoursPerDay(ConstraintFactory factory) {
        return factory.forEach(ShiftAssignment.class)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        sum(sa -> sa.getShiftDuration().getDurationInGrains()))
                .join(Configurator.class)
                .filter((employee, day, workedGrains, cfg) ->
                        cfg.isHoursPerDayCheck() && workedGrains > grains(cfg.getHoursPerDay()))
                .penalize(HardSoftScore.ONE_HARD,
                        (employee, day, workedGrains, cfg) ->
                                (workedGrains - grains(cfg.getHoursPerDay())) * 2)
                .asConstraint("Hours per day");
    }

    // CONSTRAINTS.md §2.2 — number of shifts per (employee, day) must not exceed shiftsPerDay.
    Constraint shiftsPerDay(ConstraintFactory factory) {
        return factory.forEach(ShiftAssignment.class)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        count())
                .join(Configurator.class)
                .filter((employee, day, shiftCount, cfg) ->
                        cfg.isShiftsPerDayCheck() && shiftCount > cfg.getShiftsPerDay())
                .penalize(HardSoftScore.ONE_HARD,
                        (employee, day, shiftCount, cfg) -> shiftCount - cfg.getShiftsPerDay())
                .asConstraint("Shifts per day");
    }

    /** Convert a configurator value expressed in hours to half-hour grains. */
    private static int grains(double hours) {
        return (int) (hours * 2);
    }
}
