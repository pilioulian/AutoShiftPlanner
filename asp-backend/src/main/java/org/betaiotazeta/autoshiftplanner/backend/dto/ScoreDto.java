package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The solution's {@code HardSoftScore}, flattened for the frontend. A schedule is feasible when
 * {@code hard == 0}; {@code soft} (always negative by design) is "lower is better".
 */
@Schema(description = "The hard/soft score of a solution.")
public record ScoreDto(
        @Schema(description = "Hard score; 0 means all hard constraints satisfied (feasible).", example = "0")
        long hard,

        @Schema(description = "Soft score; higher (closer to 0) is better.", example = "-12")
        long soft,

        @Schema(description = "True when the schedule is feasible (hard score is 0).", example = "true")
        boolean feasible) {
}
