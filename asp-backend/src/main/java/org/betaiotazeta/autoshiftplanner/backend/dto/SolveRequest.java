package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The full problem to solve: the business week, the constraint configuration, the staff, and any
 * non-default cell markings. Grid dimensions are derived, not sent
 * ({@code rows = numberOfEmployees * 7}, {@code columns = (endTime - startTime) * 2}).
 */
@Schema(description = "A complete shift-scheduling problem to solve.")
public record SolveRequest(
        @Schema(description = "Business week dimensions.")
        @NotNull @Valid BusinessDto business,

        @Schema(description = "Enabled constraints and thresholds.")
        @NotNull @Valid ConfiguratorDto configurator,

        @Schema(description = "Staff to schedule; order defines employeeIndex.")
        @NotEmpty @Valid List<EmployeeDto> employees,

        @Schema(description = "Non-default cell markings (forbidden / mandatory / pre-worked). May be empty.")
        @Valid List<CellMarkingDto> cells) {
}
