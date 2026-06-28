package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One assigned shift in the solution, in frontend-friendly units: day-of-week plus decimal-hour
 * start/end. Only shifts with both a start time and a duration assigned are emitted.
 */
@Schema(description = "An assigned shift for one employee.")
public record ShiftDto(
        @Schema(description = "Employee who works this shift.", example = "Alice")
        String employeeName,

        @Schema(description = "Day of week, 0 = first day .. 6 = last day.", example = "0")
        int dayOfWeek,

        @Schema(description = "Shift start in decimal hours (e.g. 8.5 = 08:30).", example = "8.0")
        double startTime,

        @Schema(description = "Shift end in decimal hours.", example = "16.0")
        double endTime,

        @Schema(description = "Shift length in decimal hours.", example = "8.0")
        double durationHours) {
}
