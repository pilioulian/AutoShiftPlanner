package org.betaiotazeta.autoshiftplanner;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.max;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.min;
import static org.optaplanner.core.api.score.stream.ConstraintCollectors.sum;

import java.util.Comparator;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

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
            breakLength(factory),
            overnightRest(factory),
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

    // CONSTRAINTS.md §2.3 — the gap between two *consecutive* shifts of the same employee on the
    // same day must equal the configured break length; penalty is the distance from it. A pair (a, b)
    // is consecutive when a starts before b and no third shift of that employee/day starts between
    // them. Only internal gaps (work resumes) are scored, exactly like the legacy run-walk.
    Constraint breakLength(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftAssignment.class,
                        Joiners.equal(sa -> sa.getShift().getEmployee()),
                        Joiners.equal(sa -> sa.getTimeGrain().getDay().getDayOfWeek()))
                .filter((a, b) -> startGrain(a) < startGrain(b))
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((a, b) -> a.getShift().getEmployee(),
                                sa -> sa.getShift().getEmployee()),
                        Joiners.equal((a, b) -> a.getTimeGrain().getDay().getDayOfWeek(),
                                sa -> sa.getTimeGrain().getDay().getDayOfWeek()),
                        Joiners.filtering((a, b, c) ->
                                startGrain(c) > startGrain(a) && startGrain(c) < startGrain(b)))
                .join(Configurator.class)
                .filter((a, b, cfg) -> cfg.isBreakLenghtCheck())
                .filter((a, b, cfg) -> (startGrain(b) - endGrain(a)) != grains(cfg.getBreakLenght()))
                .penalize(HardSoftScore.ONE_HARD,
                        (a, b, cfg) -> Math.abs((startGrain(b) - endGrain(a)) - grains(cfg.getBreakLenght())))
                .asConstraint("Break length");
    }

    // CONSTRAINTS.md §2.9 — between an employee's last shift on a day and their first shift on the
    // next calendar day, the rest (minutes to midnight + minutes after midnight) must be at least the
    // configured overnight rest; the shortfall is penalized in missing half-hour cells. Mirrors the
    // legacy carry: only two consecutive days that are both worked are compared.
    Constraint overnightRest(ConstraintFactory factory) {
        var dayEnd = factory.forEach(ShiftAssignment.class)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        max(AspConstraintProvider::endMinute, Comparator.naturalOrder()))
                .map((employee, day, maxEnd) -> new DayBound(employee, day, maxEnd));

        var dayStart = factory.forEach(ShiftAssignment.class)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        min(AspConstraintProvider::startMinute, Comparator.naturalOrder()))
                .map((employee, day, minStart) -> new DayBound(employee, day, minStart));

        return dayEnd
                .join(dayStart,
                        Joiners.equal(DayBound::employee, DayBound::employee),
                        Joiners.equal(end -> end.dayOfWeek() + 1, DayBound::dayOfWeek))
                .join(Configurator.class)
                .filter((end, start, cfg) -> cfg.isOvernightRestCheck())
                .filter((end, start, cfg) ->
                        rest(end, start) < (int) (cfg.getOvernightRest() * 60))
                .penalize(HardSoftScore.ONE_HARD,
                        (end, start, cfg) ->
                                ((int) (cfg.getOvernightRest() * 60) - rest(end, start)) / 30)
                .asConstraint("Overnight rest");
    }

    /** Minutes of rest from the end of {@code end}'s day to the start of {@code start}'s next day. */
    private static int rest(DayBound end, DayBound start) {
        return (1440 - end.minute()) + start.minute();
    }

    private static int startMinute(ShiftAssignment sa) {
        return sa.getTimeGrain().getStartingMinuteOfDay();
    }

    private static int endMinute(ShiftAssignment sa) {
        return startMinute(sa) + sa.getShiftDuration().getDurationInMinutes();
    }

    /** Per-(employee, day) boundary minute — a day's latest end or earliest start, depending on use. */
    record DayBound(Employee employee, int dayOfWeek, int minute) {
    }

    private static int startGrain(ShiftAssignment sa) {
        return sa.getTimeGrain().getStartingGrainOfDay();
    }

    private static int endGrain(ShiftAssignment sa) {
        return startGrain(sa) + sa.getShiftDuration().getDurationInGrains();
    }

    /** Convert a configurator value expressed in hours to half-hour grains. */
    private static int grains(double hours) {
        return (int) (hours * 2);
    }
}
