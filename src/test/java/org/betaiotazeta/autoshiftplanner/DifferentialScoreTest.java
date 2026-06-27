package org.betaiotazeta.autoshiftplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType;

/**
 * Feasibility-equivalence harness for the EasyScoreCalculator -> Constraint Streams migration.
 *
 * <p>The acceptance criterion (see {@code CONSTRAINTS.md}) is feasibility agreement, not identical
 * magnitudes — the new provider reasons per shift, the legacy one over merged grid runs, so penalty
 * totals may differ where shifts overlap or abut. Two cheap, deterministic checks anchor it:
 * <ol>
 *   <li>On states with no merges the two scorers must agree <em>exactly</em> (all-null fixture).</li>
 *   <li>A legacy-feasible schedule must be feasible under Constraint Streams (solved fixture).</li>
 * </ol>
 * The full solve-and-grade gate is {@link #constraintStreamsSolutionIsFeasibleUnderLegacyCalculator()}.
 */
class DifferentialScoreTest {

    private static SolutionManager<Solution, HardSoftScore> constraintStreamScorer() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(Solution.class)
                .withEntityClasses(ShiftAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(AspConstraintProvider.class)
                        .withConstraintStreamImplType(ConstraintStreamImplType.BAVET));
        return SolutionManager.create(SolverFactory.create(config));
    }

    private static HardSoftScore constraintStreamScore(Solution solution) {
        return constraintStreamScorer().update(solution);
    }

    private static HardSoftScore legacyScore(Solution solution) {
        return new AspEasyScoreCalculator().calculateScore(solution);
    }

    @Test
    void allNullStateMatchesLegacyExactly() {
        // The unsolved fixture has every shift unassigned: no overlaps, no merges, so per-shift and
        // per-run semantics coincide and the two scorers must agree on the exact HardSoftScore.
        Solution solution = TestFixtures.load(TestFixtures.UNSOLVED_7EMP);
        assertEquals(legacyScore(solution), constraintStreamScore(solution));
    }

    @Test
    void solvedFixtureMatchesLegacyExactly() {
        // The shipped solved schedule assigns one shift per contiguous run (no merges), so per-shift
        // and per-run semantics coincide: both scorers must agree on the exact HardSoftScore,
        // including the quadratic soft (uniform distribution) term. This is the feasibility anchor —
        // a legacy-feasible (0 hard) schedule stays feasible under Constraint Streams — strengthened
        // to a full exact-match.
        Solution solution = TestFixtures.load(TestFixtures.SOLVED_7EMP);
        assertEquals(0, legacyScore(solution).hardScore(), "precondition: fixture is legacy-feasible");
        assertEquals(legacyScore(solution), constraintStreamScore(solution));
    }

    @Test
    void constraintStreamsSolutionIsFeasibleUnderLegacyCalculator() {
        // Strongest gate: solve from scratch with the PRODUCTION CS config (constraintProviderClass +
        // the tuned move selectors), then grade the result with the legacy calculator. A missing or
        // under-weighted constraint would let the solver produce a schedule the legacy calculator
        // rejects. Termination is overridden to stop at first hard-feasible solution, with a cap.
        SolverConfig config = SolverConfig.createFromXmlResource(
                        "org/betaiotazeta/autoshiftplanner/solver/aspSolverConfig.xml")
                .withTerminationConfig(new TerminationConfig()
                        .withBestScoreFeasible(true)
                        .withSpentLimit(Duration.ofMinutes(4)));

        Solution unsolved = TestFixtures.load(TestFixtures.UNSOLVED_7EMP);
        Solution solved = SolverFactory.<Solution>create(config).buildSolver().solve(unsolved);

        assertEquals(0, legacyScore(solved).hardScore(),
                () -> "A CS-feasible schedule must be feasible under the legacy calculator too. "
                        + "Legacy score: " + legacyScore(solved));
    }
}
