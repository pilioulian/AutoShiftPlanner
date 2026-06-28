package org.betaiotazeta.autoshiftplanner.persistence;

import ai.timefold.solver.jackson.api.TimefoldJacksonModule;
import ai.timefold.solver.jackson.impl.domain.solution.JacksonSolutionFileIO;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.betaiotazeta.autoshiftplanner.Solution;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON persistence for {@link Solution}. This is the solution file format, replacing the legacy
 * XStream/XML format used before the Timefold migration.
 *
 * <p>The {@link ObjectMapper} is deliberately configured to mirror the old XStream model so the
 * domain round-trips faithfully:
 * <ul>
 *   <li><b>Field-based access</b> (getters/setters/creators ignored): the domain is (de)serialized by
 *       its private fields, exactly like XStream. This automatically skips the computed,
 *       setter-less getters ({@code getGridCells()}, {@code getStaffablePeriods()},
 *       {@code ShiftAssignment.getId()}) — they are recomputed on demand — and avoids mistaking the
 *       multi-arg domain constructors for Jackson creators (a private no-arg constructor is used
 *       instead).</li>
 *   <li><b>Object identity</b> is preserved by {@code @JsonIdentityInfo} on the shared
 *       fact/value-range classes ({@code Employee}, {@code Day}, {@code TimeGrain},
 *       {@code ShiftDuration}, {@code Shift}); this is the JSON equivalent of XStream's
 *       id-references and is what keeps an assigned {@code TimeGrain}/{@code ShiftDuration} the same
 *       instance as its value-range element (required by the solver).</li>
 *   <li>{@link TimefoldJacksonModule} provides the {@code HardSoftScore} (de)serializer.</li>
 * </ul>
 */
public class JsonSolutionFileIO extends JacksonSolutionFileIO<Solution> {

    public JsonSolutionFileIO() {
        super(Solution.class, buildObjectMapper());
    }

    private static ObjectMapper buildObjectMapper() {
        // Jackson 3's ObjectMapper is immutable and configured through the builder (the 1.x-era
        // mutators registerModule/setVisibility/enable were removed in the move to Jackson 3).
        return JsonMapper.builder()
                .addModule(TimefoldJacksonModule.createModule())
                .changeDefaultVisibility(vc -> vc
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }
}
