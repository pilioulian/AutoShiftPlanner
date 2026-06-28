package org.betaiotazeta.autoshiftplanner.backend.service;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import java.util.UUID;
import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveResponse;
import org.betaiotazeta.autoshiftplanner.backend.mapper.ProblemMapper;
import org.betaiotazeta.autoshiftplanner.backend.mapper.SolutionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates asynchronous solving. Maps a {@link SolveRequest} to a {@link Solution}, hands it to
 * the auto-configured {@link SolverManager} under a generated job id, and streams each new best
 * solution into the {@link ScheduleRepository} for polling. The {@code SolverManager} runs solving on
 * its own thread pool, so submission returns immediately.
 */
@Service
public class SolveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SolveService.class);

    private final SolverManager<Solution> solverManager;
    private final ScheduleRepository repository;
    private final ProblemMapper problemMapper;
    private final SolutionMapper solutionMapper;

    public SolveService(SolverManager<Solution> solverManager, ScheduleRepository repository,
            ProblemMapper problemMapper, SolutionMapper solutionMapper) {
        this.solverManager = solverManager;
        this.repository = repository;
        this.problemMapper = problemMapper;
        this.solutionMapper = solutionMapper;
    }

    /**
     * Builds the problem and starts solving it asynchronously.
     *
     * @return the id of the new solve job
     * @throws ShiftConversionException if a pre-worked region is incompatible with the settings (-> 400)
     */
    public UUID solve(SolveRequest request) throws ShiftConversionException {
        Solution problem = problemMapper.toSolution(request);
        UUID jobId = UUID.randomUUID();
        // Seed the repository so a GET right after submission returns the (unsolved) problem rather than 404.
        repository.save(jobId, problem);
        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withBestSolutionEventConsumer(event -> repository.save(jobId, event.solution()))
                .withExceptionHandler((id, throwable) ->
                        LOGGER.error("Solving failed for job {}.", id, throwable))
                .run();
        return jobId;
    }

    public SolverStatus getStatus(UUID jobId) {
        return solverManager.getSolverStatus(jobId);
    }

    /** @throws ScheduleNotFoundException if the job id is unknown (-> 404) */
    public SolveResponse getSchedule(UUID jobId) {
        Solution solution = repository.find(jobId).orElseThrow(() -> new ScheduleNotFoundException(jobId));
        return solutionMapper.toResponse(solution, jobId.toString(), solverManager.getSolverStatus(jobId));
    }

    /** @throws ScheduleNotFoundException if the job id is unknown (-> 404) */
    public void terminate(UUID jobId) {
        if (!repository.contains(jobId)) {
            throw new ScheduleNotFoundException(jobId);
        }
        solverManager.terminateEarly(jobId);
    }
}
