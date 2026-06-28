package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A single non-default cell in the grid. Only cells that differ from the neutral default need to be
 * sent; everything else is treated as free. The grid coordinate is (employee, day, grain):
 * {@code row = employeeIndex + dayOfWeek * numberOfEmployees}, {@code column = grainOfDay}.
 */
@Schema(description = "A non-default marking for one grid cell.")
public record CellMarkingDto(
        @Schema(description = "0-based index of the employee (matches the order of the employees list).", example = "0")
        @PositiveOrZero int employeeIndex,

        @Schema(description = "Day of week, 0 = first day .. 6 = last day.", example = "0")
        @PositiveOrZero int dayOfWeek,

        @Schema(description = "0-based half-hour slot within the business day (0 = opening half hour).", example = "4")
        @PositiveOrZero int grainOfDay,

        @Schema(description = "How this cell is marked.")
        @NotNull CellState state) {
}
