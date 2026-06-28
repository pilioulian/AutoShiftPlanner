package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The current state of a solve job: its status and the best solution found so far (score, assigned
 * shifts, and per-employee workloads). While the solver is still running this reflects the latest
 * best solution; the frontend polls until {@code solverStatus} is {@code NOT_SOLVING}.
 */
@Schema(description = "A solve job's status plus its best solution so far.")
public record SolveResponse(
        @Schema(description = "Identifier of the solve job.", example = "0f8b1a2c-1234-4abc-9def-0123456789ab")
        String jobId,

        @Schema(description = "Solver status: SOLVING_SCHEDULED, SOLVING_ACTIVE, or NOT_SOLVING.", example = "SOLVING_ACTIVE")
        String solverStatus,

        @Schema(description = "Score of the best solution so far; null before the first score is calculated.")
        ScoreDto score,

        @Schema(description = "Assigned shifts in the best solution so far.")
        List<ShiftDto> shifts,

        @Schema(description = "Per-employee target vs. scheduled hours.")
        List<EmployeeWorkloadDto> workloads) {
}
