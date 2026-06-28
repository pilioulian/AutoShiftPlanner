package org.betaiotazeta.autoshiftplanner.backend.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Verifies the OpenAPI spec is generated and documents the schedule endpoints. */
@SpringBootTest
class OpenApiDocsTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void apiDocsDescribeScheduleEndpoints() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AutoShiftPlanner API"))
                .andExpect(jsonPath("$.paths['/schedules'].post").exists())
                .andExpect(jsonPath("$.paths['/schedules/{jobId}'].get").exists())
                .andExpect(jsonPath("$.paths['/schedules/{jobId}'].delete").exists());
    }
}
