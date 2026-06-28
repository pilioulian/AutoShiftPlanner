package org.betaiotazeta.autoshiftplanner.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.betaiotazeta.autoshiftplanner.ShiftAssignment;
import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.betaiotazeta.autoshiftplanner.backend.TestProblems;
import org.betaiotazeta.autoshiftplanner.backend.dto.BusinessDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.CellMarkingDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.CellState;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link ProblemMapper} (no Spring context). */
class ProblemMapperTest {

    private final ProblemMapper mapper = new ProblemMapper();

    @Test
    void buildsGridWithDerivedDimensions() throws ShiftConversionException {
        // 2 employees x 7 days = 14 rows; (12 - 8) * 2 = 8 grains/day = 8 columns.
        Solution solution = mapper.toSolution(
                new SolveRequest(TestProblems.business(), TestProblems.configurator(), TestProblems.employees(), List.of()));

        assertThat(solution.getTableScore().getNumberOfRows()).isEqualTo(14);
        assertThat(solution.getTableScore().getnumberOfColumns()).isEqualTo(8);
        assertThat(solution.getStaffScore()).extracting(e -> e.getName()).containsExactly("Alice", "Bob");
    }

    @Test
    void appliesCellMarkingsToTheRightCell() throws ShiftConversionException {
        // Bob = employeeIndex 1, day 2, grain 3 -> row = 1 + 2*2 = 5, column 3.
        SolveRequest request = new SolveRequest(TestProblems.business(), TestProblems.configurator(),
                TestProblems.employees(),
                List.of(new CellMarkingDto(1, 2, 3, CellState.FORBIDDEN),
                        new CellMarkingDto(0, 0, 0, CellState.MANDATORY)));

        Solution solution = mapper.toSolution(request);

        assertThat(solution.getTableScore().getCell(5, 3).isForbidden()).isTrue();
        assertThat(solution.getTableScore().getCell(0, 0).isMandatory()).isTrue();
    }

    @Test
    void convertsPreWorkedRunIntoAnAssignedShift() throws ShiftConversionException {
        Solution solution = mapper.toSolution(TestProblems.feasibleWithOneShift());

        List<ShiftAssignment> assigned = solution.getShiftAssignmentList().stream()
                .filter(sa -> sa.getTimeGrain() != null && sa.getShiftDuration() != null)
                .toList();

        assertThat(assigned).hasSize(1);
        ShiftAssignment shift = assigned.get(0);
        assertThat(shift.getShift().getEmployee().getName()).isEqualTo("Alice");
        assertThat(shift.getTimeGrain().getDay().getDayOfWeek()).isZero();
        assertThat(shift.getTimeGrain().getStartingMinuteOfDay()).isEqualTo(8 * 60);
        assertThat(shift.getShiftDuration().getDurationInGrains()).isEqualTo(4); // 4 grains = 2 hours
    }

    @Test
    void rejectsOutOfRangeCellMarking() {
        SolveRequest request = new SolveRequest(TestProblems.business(), TestProblems.configurator(),
                TestProblems.employees(),
                List.of(new CellMarkingDto(5, 0, 0, CellState.FORBIDDEN))); // only 2 employees

        assertThatThrownBy(() -> mapper.toSolution(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employeeIndex");
    }

    @Test
    void rejectsHeadcountMismatch() {
        SolveRequest request = new SolveRequest(new BusinessDto(8.0, 12.0, 3), // says 3
                TestProblems.configurator(), TestProblems.employees(), List.of()); // provides 2

        assertThatThrownBy(() -> mapper.toSolution(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numberOfEmployees");
    }
}
