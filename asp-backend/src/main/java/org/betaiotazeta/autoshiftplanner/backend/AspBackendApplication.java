package org.betaiotazeta.autoshiftplanner.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * Entry point for the AutoShiftPlanner REST backend.
 *
 * <p>The Timefold Spring Boot starter auto-configures a {@code SolverManager<Solution>} from the
 * solver XML referenced in {@code application.yaml}. The {@code @PlanningSolution}/{@code @PlanningEntity}
 * classes live in {@code asp-core}'s {@code org.betaiotazeta.autoshiftplanner} package — outside this
 * application's base package — so {@link EntityScan} points the solver's domain scanner at them.
 */
@SpringBootApplication
@EntityScan("org.betaiotazeta.autoshiftplanner")
public class AspBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AspBackendApplication.class, args);
    }
}
