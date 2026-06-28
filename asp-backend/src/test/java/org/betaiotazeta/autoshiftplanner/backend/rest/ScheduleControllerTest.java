package org.betaiotazeta.autoshiftplanner.backend.rest;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.betaiotazeta.autoshiftplanner.backend.TestProblems;
import org.betaiotazeta.autoshiftplanner.backend.dto.SolveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end test of the async solve flow over MockMvc. The solver's time limit is overridden to a few
 * seconds (the XML config's 180s would make the test hang) so a tiny problem solves to feasibility
 * quickly; the test then polls until solving stops.
 */
@SpringBootTest(properties = "timefold.solver.termination.spent-limit=5s")
class ScheduleControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void startsSolveAndPollsToFeasibleSolution() throws Exception {
        MockMvc mockMvc = mockMvc();
        String body = objectMapper.writeValueAsString(TestProblems.feasibleWithOneShift());

        String submitJson = mockMvc.perform(post("/schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.solverStatus").exists())
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(submitJson, "$.jobId");

        // Poll until the solver has finished (spent-limit reached).
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                mockMvc.perform(get("/schedules/{jobId}", jobId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.solverStatus").value("NOT_SOLVING")));

        mockMvc.perform(get("/schedules/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.score.hard").value(0))
                .andExpect(jsonPath("$.score.feasible").value(true))
                .andExpect(jsonPath("$.shifts", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.workloads", hasSize(2)));
    }

    @Test
    void unknownJobReturns404() throws Exception {
        mockMvc().perform(get("/schedules/{jobId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        // Empty employee list violates @NotEmpty on SolveRequest.
        SolveRequest invalid = new SolveRequest(
                TestProblems.business(), TestProblems.configurator(), List.of(), List.of());
        String body = objectMapper.writeValueAsString(invalid);

        mockMvc().perform(post("/schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
