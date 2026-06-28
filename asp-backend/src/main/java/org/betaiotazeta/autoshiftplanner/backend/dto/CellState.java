package org.betaiotazeta.autoshiftplanner.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The marking applied to a grid cell in the request. Maps directly onto the boolean flags of
 * asp-core's {@code Cell}.
 */
@Schema(description = "How a single grid cell is marked.")
public enum CellState {

    /** The employee cannot work this period (forbidden cell). */
    FORBIDDEN,

    /** The employee must work this period (mandatory cell). */
    MANDATORY,

    /** Pre-assigned worked period; converted into a fixed shift before solving. */
    WORKED
}
