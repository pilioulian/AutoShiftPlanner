package org.betaiotazeta.autoshiftplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.score.ScoreExplanation;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

/**
 * Feasibility-equivalence gate for the EasyScoreCalculator -> Constraint Streams migration.
 *
 * <p>The acceptance criterion (see {@code CONSTRAINTS.md}) is feasibility agreement, not identical
 * magnitudes: a schedule that is hard-feasible under the legacy calculator must be hard-feasible
 * under the new {@link AspConstraintProvider}. This scores the shipped legacy-solved fixture (which
 * is {@code 0hard} under the legacy calculator) with the new provider and asserts it is also
 * {@code 0hard}.
 */
class DifferentialScoreTest {

    private static SolutionManager<Solution, HardSoftScore> constraintStreamScorer() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(Solution.class)
                .withEntityClasses(ShiftAssignment.class)
                .withConstraintProviderClass(AspConstraintProvider.class);
        return SolutionManager.create(SolverFactory.create(config));
    }

    @Test
    void legacyFeasibleSolutionIsAlsoFeasibleUnderConstraintStreams() {
        Solution solution = TestFixtures.load(TestFixtures.SOLVED_7EMP);

        HardSoftScore legacy = new AspEasyScoreCalculator().calculateScore(solution);
        assertEquals(0, legacy.hardScore(), "precondition: shipped fixture is legacy-feasible");

        SolutionManager<Solution, HardSoftScore> scorer = constraintStreamScorer();
        ScoreExplanation<Solution, HardSoftScore> explanation = scorer.explain(solution);
        HardSoftScore cs = explanation.getScore();

        assertEquals(0, cs.hardScore(),
                () -> "Constraint Streams must agree the legacy-feasible solution is feasible.\n"
                        + explanation.getSummary());
    }

    @Test
    void diagnoseUnsolvedFixture() {
        Solution s = TestFixtures.load(TestFixtures.UNSOLVED_7EMP);
        long assigned = s.getShiftAssignmentList().stream()
                .filter(a -> a.getTimeGrain() != null && a.getShiftDuration() != null).count();
        System.out.println("DIAG assignments total=" + s.getShiftAssignmentList().size()
                + " assigned(non-null)=" + assigned);
        System.out.println("DIAG legacy score = " + new AspEasyScoreCalculator().calculateScore(s));
        System.out.println("DIAG config: hpw=" + s.getConfigurator().isHoursPerWeekCheck()
                + " epp=" + s.getConfigurator().isEmployeesPerPeriodCheck()
                + " mand=" + s.getConfigurator().isMandatoryShiftsCheck()
                + " uniform=" + s.getConfigurator().isUniformEmployeesDistributionCheck());
        System.out.println("DIAG CS explanation:\n" + constraintStreamScorer().explain(s).getSummary());
    }

    @Test
    void constraintStreamsSolutionIsFeasibleUnderLegacyCalculator() {
        // The strongest gate: solve from scratch with the new provider, then grade the result with
        // the legacy calculator. If the provider were missing or under-weighting a constraint, the
        // solver would exploit the gap and the legacy calculator would report hardScore < 0.
        SolverConfig config = new SolverConfig()
                .withSolutionClass(Solution.class)
                .withEntityClasses(ShiftAssignment.class)
                .withConstraintProviderClass(AspConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withBestScoreFeasible(true)               // stop at first hard-feasible solution
                        .withSpentLimit(Duration.ofMinutes(3)));   // safety cap

        Solution unsolved = TestFixtures.load(TestFixtures.UNSOLVED_7EMP);
        Solution solved = SolverFactory.<Solution>create(config).buildSolver().solve(unsolved);

        HardSoftScore legacy = new AspEasyScoreCalculator().calculateScore(solved);
        assertEquals(0, legacy.hardScore(),
                () -> "A Constraint-Streams-feasible schedule must also be feasible under the legacy "
                        + "calculator. Legacy score: " + legacy);
    }
}
