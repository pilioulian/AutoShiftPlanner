package org.betaiotazeta.autoshiftplanner.backend.mapper;

import java.util.ArrayList;
import org.betaiotazeta.autoshiftplanner.Business;
import org.betaiotazeta.autoshiftplanner.Cell;
import org.betaiotazeta.autoshiftplanner.Configurator;
import org.betaiotazeta.autoshiftplanner.Employee;
import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.betaiotazeta.autoshiftplanner.SolutionGenerator;
import org.betaiotazeta.autoshiftplanner.Table;
import org.betaiotazeta.autoshiftplanner.TableToShiftConverter;
import org.betaiotazeta.autoshiftplanner.backend.dto.CellMarkingDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.ConfiguratorDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.EmployeeDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;
import org.springframework.stereotype.Component;

/**
 * Builds a solver-ready {@link Solution} from a {@link SolveRequest}, reproducing the headless solve
 * setup the Swing app performs (see {@code AspApp.solve_jButtonActionPerformed}): construct the
 * {@link Business}/{@link Configurator}/staff/{@link Table}, apply cell markings, then run
 * {@link SolutionGenerator} and {@link TableToShiftConverter}.
 */
@Component
public class ProblemMapper {

    private static final int DAYS_PER_WEEK = 7;

    /**
     * @throws IllegalArgumentException if a cell marking falls outside the derived grid (surfaced as 400)
     * @throws ShiftConversionException if a pre-worked region cannot be turned into a shift given the
     *         constraint settings (surfaced as 400)
     */
    public Solution toSolution(SolveRequest request) throws ShiftConversionException {
        Business business = new Business(
                request.business().startTime(),
                request.business().endTime(),
                request.business().numberOfEmployees());

        if (business.getEndTime() <= business.getStartTime()) {
            throw new IllegalArgumentException("endTime (" + business.getEndTime()
                    + ") must be greater than startTime (" + business.getStartTime() + ").");
        }
        if (request.employees().size() != business.getNumberOfEmployees()) {
            throw new IllegalArgumentException("business.numberOfEmployees (" + business.getNumberOfEmployees()
                    + ") must match the number of employees provided (" + request.employees().size() + ").");
        }

        Configurator configurator = toConfigurator(request.configurator());

        ArrayList<Employee> staff = new ArrayList<>(request.employees().size());
        for (EmployeeDto dto : request.employees()) {
            staff.add(new Employee(dto.name(), dto.hoursPerWeek()));
        }

        int numberOfEmployees = business.getNumberOfEmployees();
        int rows = numberOfEmployees * DAYS_PER_WEEK;
        int columns = (int) Math.round((business.getEndTime() - business.getStartTime()) * 2); // half-hour grains
        Table table = new Table(rows, columns, business);
        applyCellMarkings(table, request, numberOfEmployees, rows, columns);

        Solution solution = new SolutionGenerator(business, configurator, table, staff).createSolution();
        new TableToShiftConverter().convert(table, solution);
        return solution;
    }

    private void applyCellMarkings(Table table, SolveRequest request, int numberOfEmployees, int rows, int columns) {
        if (request.cells() == null) {
            return;
        }
        for (CellMarkingDto marking : request.cells()) {
            if (marking.employeeIndex() >= numberOfEmployees) {
                throw new IllegalArgumentException("employeeIndex " + marking.employeeIndex()
                        + " is out of range (0.." + (numberOfEmployees - 1) + ").");
            }
            if (marking.dayOfWeek() >= DAYS_PER_WEEK) {
                throw new IllegalArgumentException("dayOfWeek " + marking.dayOfWeek() + " is out of range (0..6).");
            }
            if (marking.grainOfDay() >= columns) {
                throw new IllegalArgumentException("grainOfDay " + marking.grainOfDay()
                        + " is out of range (0.." + (columns - 1) + ").");
            }
            int row = marking.employeeIndex() + marking.dayOfWeek() * numberOfEmployees;
            Cell cell = table.getCell(row, marking.grainOfDay());
            switch (marking.state()) {
                case FORBIDDEN -> cell.setForbidden(true);
                case MANDATORY -> cell.setMandatory(true);
                case WORKED -> cell.setWorked(true);
            }
        }
    }

    private Configurator toConfigurator(ConfiguratorDto dto) {
        Configurator c = new Configurator();
        c.setHoursPerWeekCheck(dto.hoursPerWeekCheck());
        c.setShiftsPerDayCheck(dto.shiftsPerDayCheck());
        c.setShiftsPerDay(dto.shiftsPerDay());
        c.setBreakLenghtCheck(dto.breakLenghtCheck());
        c.setBreakLenght(dto.breakLenght());
        c.setShiftLenghtCheck(dto.shiftLenghtCheck());
        c.setShiftLenghtMin(dto.shiftLenghtMin());
        c.setShiftLenghtMax(dto.shiftLenghtMax());
        c.setEmployeesPerPeriodCheck(dto.employeesPerPeriodCheck());
        c.setEmployeesPerPeriod(dto.employeesPerPeriod());
        c.setHoursPerDayCheck(dto.hoursPerDayCheck());
        c.setHoursPerDay(dto.hoursPerDay());
        c.setOvernightRestCheck(dto.overnightRestCheck());
        c.setOvernightRest(dto.overnightRest());
        c.setMandatoryShiftsCheck(dto.mandatoryShiftsCheck());
        c.setUniformEmployeesDistributionCheck(dto.uniformEmployeesDistributionCheck());
        return c;
    }
}
