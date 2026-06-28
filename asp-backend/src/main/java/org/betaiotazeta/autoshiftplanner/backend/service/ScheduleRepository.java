package org.betaiotazeta.autoshiftplanner.backend.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.betaiotazeta.autoshiftplanner.Solution;
import org.springframework.stereotype.Repository;

/**
 * In-memory store of the latest (best-so-far) {@link Solution} per solve job. Consistent with the
 * app's single-process, no-database design; jobs do not survive a restart. The solver writes the
 * latest best solution here from its background thread, and {@code GET /schedules/{id}} reads it.
 */
@Repository
public class ScheduleRepository {

    private final ConcurrentMap<UUID, Solution> solutions = new ConcurrentHashMap<>();

    public void save(UUID jobId, Solution solution) {
        solutions.put(jobId, solution);
    }

    public Optional<Solution> find(UUID jobId) {
        return Optional.ofNullable(solutions.get(jobId));
    }

    public boolean contains(UUID jobId) {
        return solutions.containsKey(jobId);
    }
}
