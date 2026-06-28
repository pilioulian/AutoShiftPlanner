package org.betaiotazeta.autoshiftplanner.backend;

import java.util.List;
import org.betaiotazeta.autoshiftplanner.backend.dto.BusinessDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.CellMarkingDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.CellState;
import org.betaiotazeta.autoshiftplanner.backend.dto.ConfiguratorDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.EmployeeDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;

/**
 * Shared, deliberately small problem fixtures for the backend tests: a 2-employee business open
 * 08:00–12:00 (8 half-hour grains/day, 14 rows). Constraints are off by default so feasibility is
 * trivial and solves finish fast; helpers add the pieces a given test needs.
 */
public final class TestProblems {

    private TestProblems() {
    }

    public static BusinessDto business() {
        return new BusinessDto(8.0, 12.0, 2);
    }

    public static List<EmployeeDto> employees() {
        return List.of(new EmployeeDto("Alice", 20), new EmployeeDto("Bob", 20));
    }

    /** All checks off, but shift-length bounds set so 2.0–4.0h shift durations are generated. */
    public static ConfiguratorDto configurator() {
        return new ConfiguratorDto(
                false,        // hoursPerWeekCheck
                false, 2,     // shiftsPerDayCheck, shiftsPerDay
                false, 0.5,   // breakLenghtCheck, breakLenght
                false, 2.0, 4.0, // shiftLenghtCheck, shiftLenghtMin, shiftLenghtMax
                false, 1,     // employeesPerPeriodCheck, employeesPerPeriod
                false, 8.0,   // hoursPerDayCheck, hoursPerDay
                false, 11.0,  // overnightRestCheck, overnightRest
                false,        // mandatoryShiftsCheck
                false);       // uniformEmployeesDistributionCheck
    }

    /** Pre-assign Alice (employeeIndex 0) a 2-hour worked run on day 0 (grains 0..3). */
    public static List<CellMarkingDto> alicePreWorked() {
        return List.of(
                new CellMarkingDto(0, 0, 0, CellState.WORKED),
                new CellMarkingDto(0, 0, 1, CellState.WORKED),
                new CellMarkingDto(0, 0, 2, CellState.WORKED),
                new CellMarkingDto(0, 0, 3, CellState.WORKED));
    }

    /** A feasible problem that pre-assigns one shift, so the solution always has at least one shift. */
    public static SolveRequest feasibleWithOneShift() {
        return new SolveRequest(business(), configurator(), employees(), alicePreWorked());
    }
}
