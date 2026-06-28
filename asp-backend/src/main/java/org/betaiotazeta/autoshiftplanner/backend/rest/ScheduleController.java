package org.betaiotazeta.autoshiftplanner.backend.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveResponse;
import org.betaiotazeta.autoshiftplanner.backend.dto.SubmitResponse;
import org.betaiotazeta.autoshiftplanner.backend.service.SolveService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for shift-scheduling solve jobs. Solving is asynchronous: {@code POST} starts a job and
 * returns its id; {@code GET} polls its status and best solution so far; {@code DELETE} stops it.
 */
@RestController
@RequestMapping("/schedules")
@Tag(name = "Schedules", description = "Start and poll shift-scheduling solve jobs.")
public class ScheduleController {

    private final SolveService solveService;

    public ScheduleController(SolveService solveService) {
        this.solveService = solveService;
    }

    @PostMapping
    @Operation(summary = "Start a solve job",
            description = "Builds the problem from the request and starts solving it asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solving started"),
            @ApiResponse(responseCode = "400", description = "Invalid request or incompatible pre-worked cells",
                    content = @Content)
    })
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody SolveRequest request)
            throws ShiftConversionException {
        UUID jobId = solveService.solve(request);
        SubmitResponse body = new SubmitResponse(jobId.toString(), solveService.getStatus(jobId).name());
        return ResponseEntity.accepted().body(body);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get a solve job's status and best solution so far")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found",
                    content = @Content(schema = @Schema(implementation = SolveResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown job id", content = @Content)
    })
    public SolveResponse get(@PathVariable("jobId") String jobId) {
        return solveService.getSchedule(toUuid(jobId));
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "Stop a solve job",
            description = "Terminates the solver early; the best solution found so far remains retrievable.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job terminated"),
            @ApiResponse(responseCode = "404", description = "Unknown job id", content = @Content)
    })
    public ResponseEntity<Void> terminate(@PathVariable("jobId") String jobId) {
        solveService.terminate(toUuid(jobId));
        return ResponseEntity.noContent().build();
    }

    /** Parse a job id; a malformed UUID becomes an {@link IllegalArgumentException} (-> 400). */
    private static UUID toUuid(String jobId) {
        try {
            return UUID.fromString(jobId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid job id: '" + jobId + "'.");
        }
    }
}
