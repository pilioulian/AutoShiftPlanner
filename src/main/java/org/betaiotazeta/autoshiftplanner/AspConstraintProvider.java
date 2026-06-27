package org.betaiotazeta.autoshiftplanner;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countDistinct;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.max;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.min;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sum;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

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
            hoursPerWeekWorked(factory),
            hoursPerWeekIdle(factory),
            hoursPerDay(factory),
            shiftsPerDay(factory),
            noOverlap(factory),
            partialAssignment(factory),
            breakLength(factory),
            overnightRest(factory),
            forbiddenCells(factory),
            mandatoryCells(factory),
            overflow(factory),
            employeesPerPeriodUnderstaffed(factory),
            employeesPerPeriodEmpty(factory),
            uniformDistribution(factory),
        };
    }

    // CONSTRAINTS.md §2.4 — each shift's duration must lie within [min, max] grains; penalty is the
    // distance outside the band (two-sided). Per shift (not per merged run).
    Constraint shiftLength(ConstraintFactory factory) {
        return assignedShifts(factory)
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
    // Split so that employees with NO assigned shifts are still penalized their full deficit: they
    // never appear in a shift-grouped stream, so the legacy "every employee" coverage would be lost.
    Constraint hoursPerWeekWorked(ConstraintFactory factory) {
        return assignedShifts(factory)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sum(sa -> sa.getShiftDuration().getDurationInGrains()))
                .join(Configurator.class)
                .filter((employee, workedGrains, cfg) -> cfg.isHoursPerWeekCheck())
                .penalize(HardSoftScore.ONE_HARD,
                        (employee, workedGrains, cfg) ->
                                Math.abs(employee.getHoursPerWeek() * 2 - workedGrains))
                .asConstraint("Hours per week (worked)");
    }

    // An employee with no assigned shift owes the full weekly amount (|target - 0|).
    Constraint hoursPerWeekIdle(ConstraintFactory factory) {
        return factory.forEach(Employee.class)
                .join(Configurator.class)
                .filter((employee, cfg) -> cfg.isHoursPerWeekCheck())
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((employee, cfg) -> employee, sa -> sa.getShift().getEmployee()))
                .penalize(HardSoftScore.ONE_HARD,
                        (employee, cfg) -> employee.getHoursPerWeek() * 2)
                .asConstraint("Hours per week (no shifts)");
    }

    // CONSTRAINTS.md §2.6 — grains worked per (employee, day) must not exceed hoursPerDay; the
    // overage is penalized double (one-sided).
    Constraint hoursPerDay(ConstraintFactory factory) {
        return assignedShifts(factory)
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
        return assignedShifts(factory)
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

    // Two shifts of the same employee on the same day must not overlap. The legacy calculator counts
    // each worked cell once (overlaps merge), so an overlapping schedule would over-count hours
    // per-shift while the legacy calculator sees a deficit; forbidding overlap keeps the per-shift
    // sum equal to the legacy cell count (and is real-world correct — no double-booking). The penalty
    // is the number of overlapping grains. See CONSTRAINTS.md §5 (overlap divergence).
    Constraint noOverlap(ConstraintFactory factory) {
        return assignedShifts(factory)
                .join(ShiftAssignment.class,
                        Joiners.equal(sa -> sa.getShift().getEmployee()),
                        Joiners.equal(AspConstraintProvider::dayOf),
                        Joiners.lessThan(ShiftAssignment::getId))
                .filter((a, b) -> assigned(a) && assigned(b))
                .filter((a, b) -> overlapGrains(a, b) > 0)
                .penalize(HardSoftScore.ONE_HARD, (a, b) -> overlapGrains(a, b))
                .asConstraint("No overlapping shifts");
    }

    // Structural (always on) — a shift with exactly one of its two planning variables assigned is
    // invalid: it represents no real shift, yet is invisible to every per-shift constraint (they all
    // require both timeGrain and shiftDuration). Because the variables are independently unassignable
    // (allowsUnassigned), Local Search can null one of them to cheaply shed a penalty, stranding the
    // shift in a partial state that is a local-optimum trap it cannot escape. Penalizing the partial
    // state forces the solver to either complete the shift or fully clear it. No legacy fixture or
    // feasible schedule contains a partial shift, so this is 0 on every oracle/exact-match case and
    // preserves feasibility-equivalence (a CS-feasible solution still has only fully-assigned shifts,
    // which the legacy calculator scores identically). See CONSTRAINTS.md §1 (structural).
    Constraint partialAssignment(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftAssignment.class)
                .filter(sa -> (sa.getTimeGrain() == null) != (sa.getShiftDuration() == null))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Partial shift assignment");
    }

    /** Number of grains two shifts share (positive only when they overlap); null-tolerant. */
    private static int overlapGrains(ShiftAssignment a, ShiftAssignment b) {
        return Math.min(endGrain(a), endGrain(b)) - Math.max(startGrain(a), startGrain(b));
    }

    // CONSTRAINTS.md §2.3 — the gap between two *consecutive* shifts of the same employee on the
    // same day must equal the configured break length; penalty is the distance from it. A pair (a, b)
    // is consecutive when a starts before b and no third shift of that employee/day starts between
    // them. Only internal gaps (work resumes) are scored, exactly like the legacy run-walk.
    Constraint breakLength(ConstraintFactory factory) {
        // Self-join ordered by START grain (not by @PlanningId): the solver assigns time grains in
        // arbitrary order, so pairing must be by start time. The right side comes from the raw entity
        // set, so the join keys and the result are guarded to fully-assigned shifts.
        return assignedShifts(factory)
                .join(ShiftAssignment.class,
                        Joiners.equal(sa -> sa.getShift().getEmployee()),
                        Joiners.equal(AspConstraintProvider::dayOf),
                        Joiners.lessThan(AspConstraintProvider::startGrain))
                .filter((a, b) -> assigned(a) && assigned(b))
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((a, b) -> a.getShift().getEmployee(),
                                sa -> sa.getShift().getEmployee()),
                        Joiners.equal((a, b) -> dayOf(a), AspConstraintProvider::dayOf),
                        // The intermediate shift c comes from the raw entity set (may be partially
                        // assigned during solving); only a fully-assigned shift counts as "between".
                        Joiners.filtering((a, b, c) -> assigned(c)
                                && startGrain(c) > startGrain(a) && startGrain(c) < startGrain(b)))
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
        var dayEnd = assignedShifts(factory)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        max(AspConstraintProvider::endMinute))
                .map((employee, day, maxEnd) -> new DayBound(employee, day, maxEnd));

        var dayStart = assignedShifts(factory)
                .groupBy(sa -> sa.getShift().getEmployee(),
                        sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                        min(AspConstraintProvider::startMinute))
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

    // CONSTRAINTS.md §2.8 (always on) — a shift covering a forbidden cell is penalized 10.
    Constraint forbiddenCells(ConstraintFactory factory) {
        return factory.forEach(GridCell.class)
                .filter(GridCell::forbidden)
                .join(ShiftAssignment.class,
                        Joiners.equal(GridCell::employee, sa -> sa.getShift().getEmployee()))
                .filter((cell, sa) -> cell.coveredBy(sa))
                .penalize(HardSoftScore.ONE_HARD, (cell, sa) -> 10)
                .asConstraint("Forbidden cell worked");
    }

    // CONSTRAINTS.md §2.7 — a mandatory cell not covered by any shift is penalized 10.
    Constraint mandatoryCells(ConstraintFactory factory) {
        return factory.forEach(GridCell.class)
                .filter(cell -> !cell.forbidden())
                .join(Configurator.class)
                .filter((cell, cfg) -> cfg.isMandatoryShiftsCheck())
                .ifNotExists(ShiftAssignment.class,
                        Joiners.equal((cell, cfg) -> cell.employee(), sa -> sa.getShift().getEmployee()),
                        Joiners.filtering((cell, cfg, sa) -> cell.coveredBy(sa)))
                .penalize(HardSoftScore.ONE_HARD, (cell, cfg) -> 10)
                .asConstraint("Mandatory cell not worked");
    }

    // CONSTRAINTS.md §1 (always on) — a shift running past the last grain of the day is penalized one
    // per overrun grain. Column count is derived from the business hours: (endTime - startTime) * 2.
    Constraint overflow(ConstraintFactory factory) {
        return assignedShifts(factory)
                .join(Business.class)
                .filter((sa, business) -> endGrain(sa) > columns(business))
                .penalize(HardSoftScore.ONE_HARD, (sa, business) -> endGrain(sa) - columns(business))
                .asConstraint("Shift overflow past day end");
    }

    private static int columns(Business business) {
        return (int) ((business.getEndTime() - business.getStartTime()) * 2);
    }

    // CONSTRAINTS.md §2.5 — each staffable period needs at least employeesPerPeriod employees
    // (one-sided). This branch covers periods worked by 1..N-1 distinct employees; the empty (zero)
    // case is handled by employeesPerPeriodEmpty so under-coverage of unworked periods is not missed.
    Constraint employeesPerPeriodUnderstaffed(ConstraintFactory factory) {
        return assignedShifts(factory)
                .join(TimeGrain.class,
                        Joiners.equal(sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                                tg -> tg.getDay().getDayOfWeek()),
                        Joiners.filtering((sa, tg) -> covers(sa, tg.getStartingGrainOfDay())))
                .groupBy((sa, tg) -> tg.getGrainIndex(),
                        countDistinct((sa, tg) -> sa.getShift().getEmployee()))
                .join(StaffablePeriod.class,
                        Joiners.equal((grainIndex, count) -> grainIndex, StaffablePeriod::grainIndex))
                .join(Configurator.class)
                .filter((grainIndex, count, period, cfg) ->
                        cfg.isEmployeesPerPeriodCheck() && count < cfg.getEmployeesPerPeriod())
                .penalize(HardSoftScore.ONE_HARD,
                        (grainIndex, count, period, cfg) -> cfg.getEmployeesPerPeriod() - count)
                .asConstraint("Employees per period understaffed");
    }

    // CONSTRAINTS.md §2.5 — a staffable period with no covering shift counts as 0 employees and is
    // penalized by the full requirement.
    Constraint employeesPerPeriodEmpty(ConstraintFactory factory) {
        return factory.forEach(StaffablePeriod.class)
                .join(Configurator.class)
                .filter((period, cfg) -> cfg.isEmployeesPerPeriodCheck())
                .ifNotExists(ShiftAssignment.class,
                        Joiners.filtering((period, cfg, sa) -> period.coveredBy(sa)))
                .penalize(HardSoftScore.ONE_HARD, (period, cfg) -> cfg.getEmployeesPerPeriod())
                .asConstraint("Employees per period empty");
    }

    // CONSTRAINTS.md §3.1 — the only soft constraint. Penalize the square of the number of distinct
    // employees in each worked period, which rewards spreading employees evenly across periods.
    Constraint uniformDistribution(ConstraintFactory factory) {
        return assignedShifts(factory)
                .join(TimeGrain.class,
                        Joiners.equal(sa -> sa.getTimeGrain().getDay().getDayOfWeek(),
                                tg -> tg.getDay().getDayOfWeek()),
                        Joiners.filtering((sa, tg) -> covers(sa, tg.getStartingGrainOfDay())))
                .groupBy((sa, tg) -> tg.getGrainIndex(),
                        countDistinct((sa, tg) -> sa.getShift().getEmployee()))
                .join(Configurator.class)
                .filter((grainIndex, count, cfg) -> cfg.isUniformEmployeesDistributionCheck())
                .penalize(HardSoftScore.ONE_SOFT, (grainIndex, count, cfg) -> count * count)
                .asConstraint("Uniform employee distribution");
    }

    /** True when the shift's grain span includes the given grain-of-day. */
    private static boolean covers(ShiftAssignment sa, int grainOfDay) {
        return grainOfDay >= startGrain(sa) && grainOfDay < endGrain(sa);
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

    // startGrain/endGrain are null-tolerant: BAVET evaluates constraint lambdas against in-flux
    // states during incremental solving (a tuple member can momentarily have a null variable before
    // the tuple is retracted). Returning a sentinel avoids a crash; such transient tuples are
    // discarded on the next propagation, so steady-state scores are unaffected.
    private static int startGrain(ShiftAssignment sa) {
        return sa.getTimeGrain() == null ? Integer.MIN_VALUE : sa.getTimeGrain().getStartingGrainOfDay();
    }

    private static int endGrain(ShiftAssignment sa) {
        return (sa.getTimeGrain() == null || sa.getShiftDuration() == null)
                ? Integer.MIN_VALUE
                : sa.getTimeGrain().getStartingGrainOfDay() + sa.getShiftDuration().getDurationInGrains();
    }

    /** Convert a configurator value expressed in hours to half-hour grains. */
    private static int grains(double hours) {
        return (int) (hours * 2);
    }

    /** A shift is "assigned" only when both nullable planning variables are set (legacy guard). */
    private static boolean assigned(ShiftAssignment sa) {
        return sa.getTimeGrain() != null && sa.getShiftDuration() != null;
    }

    /**
     * The base stream of fully-assigned shifts. {@code forEach} already excludes unassigned entities,
     * but the solver passes through partially-assigned states (one of the two variables still null),
     * so this explicit guard keeps the shift-consuming constraints null-safe.
     */
    private static UniConstraintStream<ShiftAssignment> assignedShifts(ConstraintFactory factory) {
        return factory.forEach(ShiftAssignment.class).filter(AspConstraintProvider::assigned);
    }

    /** Day of week of a shift's start, or -1 if its time grain is unset (so it matches no real day). */
    private static int dayOf(ShiftAssignment sa) {
        return sa.getTimeGrain() == null ? -1 : sa.getTimeGrain().getDay().getDayOfWeek();
    }

}
