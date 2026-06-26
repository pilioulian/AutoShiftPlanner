package org.betaiotazeta.autoshiftplanner;

import org.junit.jupiter.api.Test;
import org.optaplanner.test.api.score.stream.ConstraintVerifier;

/**
 * Exact per-constraint unit tests for {@link AspConstraintProvider} using OptaPlanner's
 * {@link ConstraintVerifier}. Each test pins one constraint's penalty on a hand-built minimal case,
 * independent of the legacy calculator. Semantics are per-shift (see {@code CONSTRAINTS.md}).
 */
class AspConstraintProviderTest {

    private final ConstraintVerifier<AspConstraintProvider, Solution> verifier =
            ConstraintVerifier.build(new AspConstraintProvider(), Solution.class, ShiftAssignment.class);

    // --- helpers ----------------------------------------------------------------

    private static Configurator config() {
        Configurator cfg = new Configurator();
        TestFixtures.setAllChecks(cfg, false);
        return cfg;
    }

    private int nextShiftIndex = 0;

    private ShiftAssignment assignment(Employee employee, int dayOfWeek, int startGrain, int durationGrains) {
        Day day = new Day();
        day.setDayOfWeek(dayOfWeek);
        TimeGrain timeGrain = new TimeGrain();
        timeGrain.setDay(day);
        timeGrain.setStartingGrainOfDay(startGrain);
        timeGrain.setStartingMinuteOfDay(startGrain * 30); // self-consistent: day starts at minute 0
        ShiftDuration duration = new ShiftDuration();
        duration.setDurationInGrains(durationGrains);
        duration.setDurationInMinutes(durationGrains * 30);
        Shift shift = new Shift();
        shift.setEmployee(employee);
        shift.setShiftIndex(nextShiftIndex++); // distinct @PlanningId per assignment
        ShiftAssignment sa = new ShiftAssignment();
        sa.setShift(shift);
        sa.setTimeGrain(timeGrain);
        sa.setShiftDuration(duration);
        return sa;
    }

    // --- §2.4 shift length ------------------------------------------------------

    @Test
    void shiftLengthBelowMinPenalizesByDistance() {
        Configurator cfg = config();
        cfg.setShiftLenghtCheck(true);
        cfg.setShiftLenghtMin(4.0); // 8 grains
        cfg.setShiftLenghtMax(8.0); // 16 grains
        Employee e = new Employee("A", 40);
        // 6 grains is 2 below the min of 8.
        verifier.verifyThat(AspConstraintProvider::shiftLength)
                .given(cfg, assignment(e, 0, 0, 6))
                .penalizesBy(2);
    }

    @Test
    void shiftLengthWithinBandIsFine() {
        Configurator cfg = config();
        cfg.setShiftLenghtCheck(true);
        cfg.setShiftLenghtMin(4.0);
        cfg.setShiftLenghtMax(8.0);
        Employee e = new Employee("A", 40);
        verifier.verifyThat(AspConstraintProvider::shiftLength)
                .given(cfg, assignment(e, 0, 0, 12)) // 12 grains, inside [8,16]
                .penalizesBy(0);
    }

    @Test
    void shiftLengthDisabledWhenFlagOff() {
        Configurator cfg = config(); // shiftLenghtCheck stays false
        cfg.setShiftLenghtMin(4.0);
        cfg.setShiftLenghtMax(8.0);
        Employee e = new Employee("A", 40);
        verifier.verifyThat(AspConstraintProvider::shiftLength)
                .given(cfg, assignment(e, 0, 0, 6))
                .penalizesBy(0);
    }

    // --- §2.1 hours per week ----------------------------------------------------

    @Test
    void hoursPerWeekPenalizesAbsoluteDeficit() {
        Configurator cfg = config();
        cfg.setHoursPerWeekCheck(true);
        Employee e = new Employee("A", 40); // target 80 grains
        // Two shifts totalling 20 grains -> |80 - 20| = 60.
        verifier.verifyThat(AspConstraintProvider::hoursPerWeek)
                .given(cfg, assignment(e, 0, 0, 12), assignment(e, 1, 0, 8))
                .penalizesBy(60);
    }

    // --- §2.6 hours per day -----------------------------------------------------

    @Test
    void hoursPerDayPenalizesDoubledOverage() {
        Configurator cfg = config();
        cfg.setHoursPerDayCheck(true);
        cfg.setHoursPerDay(8.0); // 16 grains
        Employee e = new Employee("A", 40);
        // Same employee, same day: 12 + 8 = 20 grains, 4 over -> (20-16)*2 = 8.
        verifier.verifyThat(AspConstraintProvider::hoursPerDay)
                .given(cfg, assignment(e, 0, 0, 12), assignment(e, 0, 12, 8))
                .penalizesBy(8);
    }

    // --- §2.2 shifts per day ----------------------------------------------------

    // --- §2.3 break length ------------------------------------------------------

    @Test
    void breakLengthPenalizesGapDifferentFromConfigured() {
        Configurator cfg = config();
        cfg.setBreakLenghtCheck(true);
        cfg.setBreakLenght(1.0); // 2 grains
        Employee e = new Employee("A", 40);
        // a: grains [0,4), b starts at 8 -> gap 4 grains, configured break 2 -> |4-2| = 2.
        verifier.verifyThat(AspConstraintProvider::breakLength)
                .given(cfg, assignment(e, 0, 0, 4), assignment(e, 0, 8, 4))
                .penalizesBy(2);
    }

    @Test
    void breakLengthExactGapIsFine() {
        Configurator cfg = config();
        cfg.setBreakLenghtCheck(true);
        cfg.setBreakLenght(1.0); // 2 grains
        Employee e = new Employee("A", 40);
        // a: [0,4), b starts at 6 -> gap exactly 2 grains.
        verifier.verifyThat(AspConstraintProvider::breakLength)
                .given(cfg, assignment(e, 0, 0, 4), assignment(e, 0, 6, 4))
                .penalizesBy(0);
    }

    @Test
    void breakLengthOnlyScoresConsecutivePairs() {
        Configurator cfg = config();
        cfg.setBreakLenghtCheck(true);
        cfg.setBreakLenght(1.0); // 2 grains
        Employee e = new Employee("A", 40);
        // Three shifts: a[0,4) m[6,10) b[12,16). Gaps a->m and m->b are each exactly 2 (fine);
        // the non-consecutive pair a->b must NOT be scored (a third shift starts between them).
        verifier.verifyThat(AspConstraintProvider::breakLength)
                .given(cfg, assignment(e, 0, 0, 4), assignment(e, 0, 6, 4), assignment(e, 0, 12, 4))
                .penalizesBy(0);
    }

    // --- §2.9 overnight rest ----------------------------------------------------

    @Test
    void overnightRestPenalizesShortfallInHalfHours() {
        Configurator cfg = config();
        cfg.setOvernightRestCheck(true);
        cfg.setOvernightRest(12.0); // 720 minutes required
        Employee e = new Employee("A", 40);
        // Day 0 ends at minute 1320 (grain 40 start, 4 grains -> 1200..1320).
        // Day 1 starts at minute 480 (grain 16). Rest = (1440-1320)+480 = 600 < 720.
        // Shortfall 120 minutes -> 120/30 = 4 half-hour cells.
        verifier.verifyThat(AspConstraintProvider::overnightRest)
                .given(cfg, assignment(e, 0, 40, 4), assignment(e, 1, 16, 8))
                .penalizesBy(4);
    }

    @Test
    void overnightRestSatisfiedWhenGapLargeEnough() {
        Configurator cfg = config();
        cfg.setOvernightRestCheck(true);
        cfg.setOvernightRest(8.0); // 480 minutes required
        Employee e = new Employee("A", 40);
        // Same boundaries -> rest 600 >= 480, no penalty.
        verifier.verifyThat(AspConstraintProvider::overnightRest)
                .given(cfg, assignment(e, 0, 40, 4), assignment(e, 1, 16, 8))
                .penalizesBy(0);
    }

    @Test
    void overnightRestIgnoresNonConsecutiveDays() {
        Configurator cfg = config();
        cfg.setOvernightRestCheck(true);
        cfg.setOvernightRest(12.0);
        Employee e = new Employee("A", 40);
        // Days 0 and 2 (a gap day with no work) are not adjacent -> not compared, like the legacy reset.
        verifier.verifyThat(AspConstraintProvider::overnightRest)
                .given(cfg, assignment(e, 0, 40, 4), assignment(e, 2, 16, 8))
                .penalizesBy(0);
    }

    @Test
    void shiftsPerDayPenalizesExcessCount() {
        Configurator cfg = config();
        cfg.setShiftsPerDayCheck(true);
        cfg.setShiftsPerDay(2);
        Employee e = new Employee("A", 40);
        // Three shifts on the same day -> 1 over the limit of 2.
        verifier.verifyThat(AspConstraintProvider::shiftsPerDay)
                .given(cfg, assignment(e, 0, 0, 2), assignment(e, 0, 4, 2), assignment(e, 0, 8, 2))
                .penalizesBy(1);
    }
}
