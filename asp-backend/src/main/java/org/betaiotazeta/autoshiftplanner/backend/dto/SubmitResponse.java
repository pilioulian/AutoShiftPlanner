package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned from {@code POST /schedules}: the id of the newly started solve job, to be polled via
 * {@code GET /schedules/{jobId}}.
 */
@Schema(description = "Acknowledgement of a started solve job.")
public record SubmitResponse(
        @Schema(description = "Identifier of the started solve job.", example = "0f8b1a2c-1234-4abc-9def-0123456789ab")
        String jobId,

        @Schema(description = "Solver status right after submission.", example = "SOLVING_SCHEDULED")
        String solverStatus) {
}
