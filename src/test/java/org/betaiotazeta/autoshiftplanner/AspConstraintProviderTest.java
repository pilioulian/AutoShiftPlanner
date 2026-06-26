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

    private static ShiftAssignment assignment(Employee employee, int dayOfWeek, int startGrain, int durationGrains) {
        Day day = new Day();
        day.setDayOfWeek(dayOfWeek);
        TimeGrain timeGrain = new TimeGrain();
        timeGrain.setDay(day);
        timeGrain.setStartingGrainOfDay(startGrain);
        ShiftDuration duration = new ShiftDuration();
        duration.setDurationInGrains(durationGrains);
        Shift shift = new Shift();
        shift.setEmployee(employee);
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
