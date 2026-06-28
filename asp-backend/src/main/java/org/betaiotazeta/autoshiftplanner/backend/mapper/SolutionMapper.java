package org.betaiotazeta.autoshiftplanner.backend.mapper;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.betaiotazeta.autoshiftplanner.Employee;
import org.betaiotazeta.autoshiftplanner.ShiftAssignment;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.betaiotazeta.autoshiftplanner.backend.dto.EmployeeWorkloadDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.ScoreDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.ShiftDto;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveResponse;
import org.springframework.stereotype.Component;

/**
 * Projects a (best-so-far) {@link Solution} into the frontend-facing {@link SolveResponse}. Shift
 * geometry is derived exactly as the Swing app does when it paints the solved table
 * (see {@code AspApp.solve_jButtonActionPerformed} process()): day from the time grain's day, start
 * from {@code startingMinuteOfDay}, length from the shift duration.
 */
@Component
public class SolutionMapper {

    public SolveResponse toResponse(Solution solution, String jobId, SolverStatus status) {
        List<ShiftDto> shifts = new ArrayList<>();
        // Hours scheduled per employee, accumulated from the same shifts we emit.
        Map<Employee, Double> hoursWorked = new IdentityHashMap<>();

        for (ShiftAssignment assignment : solution.getShiftAssignmentList()) {
            if (assignment.getTimeGrain() == null || assignment.getShiftDuration() == null) {
                continue; // a shift counts only when both planning variables are set
            }
            Employee employee = assignment.getShift().getEmployee();
            int dayOfWeek = assignment.getTimeGrain().getDay().getDayOfWeek();
            double startTime = assignment.getTimeGrain().getStartingMinuteOfDay() / 60.0;
            double durationHours = assignment.getShiftDuration().getDurationInMinutes() / 60.0;
            double endTime = startTime + durationHours;

            shifts.add(new ShiftDto(employee.getName(), dayOfWeek, startTime, endTime, durationHours));
            hoursWorked.merge(employee, durationHours, Double::sum);
        }

        List<EmployeeWorkloadDto> workloads = new ArrayList<>(solution.getStaffScore().size());
        for (Employee employee : solution.getStaffScore()) {
            workloads.add(new EmployeeWorkloadDto(
                    employee.getName(),
                    employee.getHoursPerWeek(),
                    hoursWorked.getOrDefault(employee, 0.0)));
        }

        return new SolveResponse(jobId, status.name(), toScoreDto(solution.getScore()), shifts, workloads);
    }

    private ScoreDto toScoreDto(HardSoftScore score) {
        if (score == null) {
            return null; // not yet calculated (e.g. polled before the first best solution)
        }
        return new ScoreDto(score.hardScore(), score.softScore(), score.isFeasible());
    }
}
