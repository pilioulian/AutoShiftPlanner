package org.betaiotazeta.autoshiftplanner.backend.service;

import java.util.UUID;

/** Thrown when a solve job id is not known to the backend; surfaced as HTTP 404. */
public class ScheduleNotFoundException extends RuntimeException {

    public ScheduleNotFoundException(UUID jobId) {
        super("No solve job found for id " + jobId + ".");
    }
}
