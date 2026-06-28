package org.betaiotazeta.autoshiftplanner.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.betaiotazeta.autoshiftplanner.backend.TestProblems;
import org.betaiotazeta.autoshiftplanner.backend.dto.ShiftDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveResponse;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link SolutionMapper} (no Spring context). */
class SolutionMapperTest {

    private final ProblemMapper problemMapper = new ProblemMapper();
    private final SolutionMapper solutionMapper = new SolutionMapper();

    @Test
    void projectsAssignedShiftAndWorkloads() throws ShiftConversionException {
        // A solution with one pre-assigned Alice shift (day 0, 08:00, 2h); give it a feasible score.
        Solution solution = problemMapper.toSolution(TestProblems.feasibleWithOneShift());
        solution.setScore(HardSoftScore.of(0, -5));

        SolveResponse response = solutionMapper.toResponse(solution, "job-1", SolverStatus.NOT_SOLVING);

        assertThat(response.jobId()).isEqualTo("job-1");
        assertThat(response.solverStatus()).isEqualTo("NOT_SOLVING");
        assertThat(response.score().hard()).isZero();
        assertThat(response.score().soft()).isEqualTo(-5);
        assertThat(response.score().feasible()).isTrue();

        assertThat(response.shifts()).hasSize(1);
        ShiftDto shift = response.shifts().get(0);
        assertThat(shift.employeeName()).isEqualTo("Alice");
        assertThat(shift.dayOfWeek()).isZero();
        assertThat(shift.startTime()).isEqualTo(8.0);
        assertThat(shift.durationHours()).isEqualTo(2.0);
        assertThat(shift.endTime()).isEqualTo(10.0);

        assertThat(response.workloads())
                .extracting(w -> w.name(), w -> w.hoursWorked())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Alice", 2.0),
                        org.assertj.core.groups.Tuple.tuple("Bob", 0.0));
    }

    @Test
    void nullScoreMapsToNullScoreDto() throws ShiftConversionException {
        Solution solution = problemMapper.toSolution(TestProblems.feasibleWithOneShift());
        // score left null (e.g. polled before the first best solution is produced)

        SolveResponse response = solutionMapper.toResponse(solution, "job-2", SolverStatus.SOLVING_ACTIVE);

        assertThat(response.score()).isNull();
        assertThat(response.solverStatus()).isEqualTo("SOLVING_ACTIVE");
    }
}
