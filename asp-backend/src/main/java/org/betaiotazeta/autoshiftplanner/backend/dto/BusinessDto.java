package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The business week dimensions. Times are decimal hours (8:30 AM = {@code 8.5}); the grid is half-hour
 * "grains", so {@code (endTime - startTime)} should land on a half-hour boundary.
 */
@Schema(description = "Opening hours and headcount that define the schedule grid.")
public record BusinessDto(
        @Schema(description = "Opening time in decimal hours (e.g. 8.5 = 08:30).", example = "8.0")
        @PositiveOrZero double startTime,

        @Schema(description = "Closing time in decimal hours (e.g. 21.0 = 21:00). Must be > startTime.", example = "20.0")
        @Positive double endTime,

        @Schema(description = "Total number of employees to schedule.", example = "3")
        @Positive int numberOfEmployees) {
}
