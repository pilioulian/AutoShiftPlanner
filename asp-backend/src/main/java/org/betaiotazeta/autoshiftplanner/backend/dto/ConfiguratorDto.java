package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The constraint configuration, mirroring asp-core's {@code Configurator} one-for-one (including the
 * codebase's {@code Lenght} spelling). Each {@code *Check} flag toggles a constraint; the accompanying
 * value is its threshold. Hours/durations are decimal hours.
 */
@Schema(description = "Which scheduling constraints are enabled and their thresholds.")
public record ConfiguratorDto(
        @Schema(description = "Enforce each employee's weekly target hours.") boolean hoursPerWeekCheck,

        @Schema(description = "Enforce a maximum number of shifts per day.") boolean shiftsPerDayCheck,
        @Schema(description = "Maximum shifts an employee may work in one day.", example = "1") int shiftsPerDay,

        @Schema(description = "Enforce a minimum break between two shifts on the same day.") boolean breakLenghtCheck,
        @Schema(description = "Minimum break length in decimal hours.", example = "0.5") double breakLenght,

        @Schema(description = "Enforce minimum/maximum shift length bounds.") boolean shiftLenghtCheck,
        @Schema(description = "Minimum shift length in decimal hours.", example = "4.0") double shiftLenghtMin,
        @Schema(description = "Maximum shift length in decimal hours.", example = "8.0") double shiftLenghtMax,

        @Schema(description = "Enforce a minimum number of employees per half-hour period.") boolean employeesPerPeriodCheck,
        @Schema(description = "Minimum employees required to staff each (non-forbidden) period.", example = "1") int employeesPerPeriod,

        @Schema(description = "Enforce a maximum number of worked hours per calendar day.") boolean hoursPerDayCheck,
        @Schema(description = "Maximum worked hours per day in decimal hours.", example = "8.0") double hoursPerDay,

        @Schema(description = "Enforce a minimum overnight rest between days.") boolean overnightRestCheck,
        @Schema(description = "Minimum overnight rest in decimal hours.", example = "11.0") double overnightRest,

        @Schema(description = "Enforce cells the user marked mandatory.") boolean mandatoryShiftsCheck,

        @Schema(description = "Soft constraint: distribute employees uniformly across periods.") boolean uniformEmployeesDistributionCheck) {
}
