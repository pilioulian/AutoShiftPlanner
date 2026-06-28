package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-employee workload summary: the target weekly hours alongside the hours actually scheduled in
 * the solution (summed from the employee's assigned shifts).
 */
@Schema(description = "Target vs. scheduled hours for one employee.")
public record EmployeeWorkloadDto(
        @Schema(description = "Employee name.", example = "Alice")
        String name,

        @Schema(description = "Target weekly hours.", example = "40")
        int hoursPerWeek,

        @Schema(description = "Hours actually scheduled across the week.", example = "38.5")
        double hoursWorked) {
}
