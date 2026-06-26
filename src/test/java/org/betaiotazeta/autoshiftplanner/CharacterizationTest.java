package org.betaiotazeta.autoshiftplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

/**
 * Golden-master tests that pin the EXACT score the current {@link AspEasyScoreCalculator} produces.
 *
 * <p>These freeze today's behavior so that (a) refactors of the legacy calculator can't silently
 * drift, and (b) the fixtures are proven to load. The comprehensive equivalence guarantee for the
 * Constraint Streams migration lives in {@link DifferentialScoreTest} (old == new on identical
 * states); this class locks the absolute numbers. Expected values were captured from the legacy
 * calculator — if a number here changes, the legacy scoring behavior changed.
 *
 * <p>See {@code CONSTRAINTS.md} for the meaning of each constraint.
 */
class CharacterizationTest {

    private final AspEasyScoreCalculator calc = new AspEasyScoreCalculator();

    // --- Score of each fixture exactly as persisted (no mutation) ---

    static List<Arguments> asLoadedCases() {
        return List.of(
                Arguments.of(TestFixtures.SOLVED_7EMP, HardSoftScore.of(0, -1258)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP, HardSoftScore.of(-826, 0)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP_MANDATORY, HardSoftScore.of(-1106, 0)));
    }

    @ParameterizedTest(name = "as-loaded {0} == {1}")
    @MethodSource("asLoadedCases")
    void asLoadedScore(String fixture, HardSoftScore expected) {
        assertEquals(expected, calc.calculateScore(TestFixtures.load(fixture)));
    }

    // --- Score under a fixed randomization seed (locks behavior across varied planning states) ---

    static List<Arguments> seededCases() {
        return List.of(
                Arguments.of(TestFixtures.SOLVED_7EMP, 1L, HardSoftScore.of(-1397, -1378)),
                Arguments.of(TestFixtures.SOLVED_7EMP, 2L, HardSoftScore.of(-1422, -1244)),
                Arguments.of(TestFixtures.SOLVED_7EMP, 3L, HardSoftScore.of(-2287, -1459)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP, 1L, HardSoftScore.of(-1919, -1448)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP, 2L, HardSoftScore.of(-1335, -1031)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP, 3L, HardSoftScore.of(-1416, -1385)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP_MANDATORY, 1L, HardSoftScore.of(-2169, -1448)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP_MANDATORY, 2L, HardSoftScore.of(-1555, -1031)),
                Arguments.of(TestFixtures.UNSOLVED_7EMP_MANDATORY, 3L, HardSoftScore.of(-1626, -1385)));
    }

    @ParameterizedTest(name = "{0} seed={1} == {2}")
    @MethodSource("seededCases")
    void seededScore(String fixture, long seed, HardSoftScore expected) {
        Solution s = TestFixtures.load(fixture);
        TestFixtures.randomizeAssignments(s, seed, 0.3);
        assertEquals(expected, calc.calculateScore(s));
    }

    // --- Per-constraint isolation: solved fixture, seed 1, all flags off then exactly one on ---
    // baseline (-1051 hard) is the always-on overflow + forbidden-cell penalty (CONSTRAINTS.md §4).

    static List<Arguments> isolatedCases() {
        return List.of(
                Arguments.of((TestFixtures.Flag) null, HardSoftScore.of(-1051, 0)), // baseline
                Arguments.of(TestFixtures.Flag.HOURS_PER_WEEK, HardSoftScore.of(-1169, 0)),
                Arguments.of(TestFixtures.Flag.SHIFTS_PER_DAY, HardSoftScore.of(-1051, 0)),
                Arguments.of(TestFixtures.Flag.BREAK_LENGTH, HardSoftScore.of(-1119, 0)),
                Arguments.of(TestFixtures.Flag.SHIFT_LENGTH, HardSoftScore.of(-1100, 0)),
                Arguments.of(TestFixtures.Flag.EMPLOYEES_PER_PERIOD, HardSoftScore.of(-1126, 0)),
                Arguments.of(TestFixtures.Flag.HOURS_PER_DAY, HardSoftScore.of(-1081, 0)),
                Arguments.of(TestFixtures.Flag.OVERNIGHT_REST, HardSoftScore.of(-1057, 0)),
                Arguments.of(TestFixtures.Flag.MANDATORY_SHIFTS, HardSoftScore.of(-1051, 0)),
                Arguments.of(TestFixtures.Flag.UNIFORM_DISTRIBUTION, HardSoftScore.of(-1051, -1378)));
    }

    @ParameterizedTest(name = "isolate {0}")
    @MethodSource("isolatedCases")
    void isolatedConstraintScore(TestFixtures.Flag flag, HardSoftScore expected) {
        Solution s = TestFixtures.load(TestFixtures.SOLVED_7EMP);
        TestFixtures.randomizeAssignments(s, 1L, 0.3);
        TestFixtures.setAllChecks(s.getConfigurator(), false);
        if (flag != null) {
            flag.set(s.getConfigurator(), true);
        }
        assertEquals(expected, calc.calculateScore(s));
    }

    @Test
    void solvedFixtureIsFeasible() {
        // Sanity anchor: the shipped "solved" sample really is hard-feasible under its own config.
        assertEquals(0, calc.calculateScore(TestFixtures.load(TestFixtures.SOLVED_7EMP)).hardScore());
    }
}
