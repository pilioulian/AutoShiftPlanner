package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One employee in the request. Their order defines the {@code employeeIndex} used by
 * {@link CellMarkingDto} (first employee = index 0).
 */
@Schema(description = "An employee to be scheduled.")
public record EmployeeDto(
        @Schema(description = "Display name.", example = "Alice")
        @NotBlank String name,

        @Schema(description = "Target weekly hours.", example = "40")
        @PositiveOrZero int hoursPerWeek) {
}
