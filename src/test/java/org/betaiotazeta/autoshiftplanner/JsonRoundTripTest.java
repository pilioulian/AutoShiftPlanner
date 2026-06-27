package org.betaiotazeta.autoshiftplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.betaiotazeta.autoshiftplanner.persistence.JsonSolutionFileIO;
import org.junit.jupiter.api.Test;

/**
 * Fidelity guard for the JSON ({@link JsonSolutionFileIO}) persistence.
 *
 * <p>Loads a {@code data/*.json} fixture, writes it back out to JSON, reads it again, and asserts the
 * restored graph is equivalent on the two things that matter:
 * <ol>
 *   <li><b>Score</b> is identical — i.e. every field and the entity/value-range wiring survived.</li>
 *   <li><b>Object identity</b> of shared references is preserved: an assigned {@code TimeGrain} /
 *       {@code ShiftDuration} is the <em>same instance</em> as its value-range element, and a
 *       {@code Shift}/{@code Employee} is the same instance as in its fact list — exactly what
 *       XStream's id-references guaranteed and Jackson would silently break without
 *       {@code @JsonIdentityInfo}.</li>
 * </ol>
 * Both must hold before persistence is cut over from XStream to Jackson.
 */
class JsonRoundTripTest {

    private static HardSoftScore score(Solution solution) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(Solution.class)
                .withEntityClasses(ShiftAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(AspConstraintProvider.class)
                        .withConstraintStreamImplType(ConstraintStreamImplType.BAVET));
        return SolutionManager.<Solution, HardSoftScore>create(SolverFactory.create(config)).update(solution);
    }

    private static Solution jsonRoundTrip(Solution original) throws Exception {
        JsonSolutionFileIO io = new JsonSolutionFileIO();
        File tmp = Files.createTempFile("asp-roundtrip", ".json").toFile();
        tmp.deleteOnExit();
        io.write(original, tmp);
        return io.read(tmp);
    }

    @Test
    void solvedFixtureRoundTripsWithSameScoreAndIdentity() throws Exception {
        // A solved schedule exercises non-null timeGrain/shiftDuration references (the identity path).
        assertRoundTripFidelity(TestFixtures.load(TestFixtures.SOLVED_7EMP));
    }

    @Test
    void randomizedAssignmentsRoundTripWithSameScoreAndIdentity() throws Exception {
        // A mix of assigned and null shifts, drawn from the value ranges, over the mandatory fixture.
        Solution solution = TestFixtures.load(TestFixtures.UNSOLVED_7EMP_MANDATORY);
        TestFixtures.randomizeAssignments(solution, 42L, 0.3);
        assertRoundTripFidelity(solution);
    }

    private static void assertRoundTripFidelity(Solution original) throws Exception {
        HardSoftScore originalScore = score(original);
        Solution restored = jsonRoundTrip(original);

        assertEquals(originalScore, score(restored), "score must be identical after a JSON round-trip");

        assertEquals(original.getShiftAssignmentList().size(), restored.getShiftAssignmentList().size(),
                "shift assignment count must survive the round-trip");
        for (ShiftAssignment sa : restored.getShiftAssignmentList()) {
            assertTrue(containsSameInstance(restored.getShiftList(), sa.getShift()),
                    "shift must be the same instance as in shiftList");
            assertTrue(containsSameInstance(restored.getStaffScore(), sa.getShift().getEmployee()),
                    "employee must be the same instance as in staffScore");
            if (sa.getTimeGrain() != null) {
                assertTrue(containsSameInstance(restored.getTimeGrainList(), sa.getTimeGrain()),
                        "assigned timeGrain must be the same instance as its value-range element");
            }
            if (sa.getShiftDuration() != null) {
                assertTrue(containsSameInstance(restored.getShiftDurationList(), sa.getShiftDuration()),
                        "assigned shiftDuration must be the same instance as its value-range element");
            }
        }
    }

    private static boolean containsSameInstance(List<?> list, Object target) {
        for (Object element : list) {
            if (element == target) {
                return true;
            }
        }
        return false;
    }
}
