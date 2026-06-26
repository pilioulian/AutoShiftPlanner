package org.betaiotazeta.autoshiftplanner;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.ArrayTypePermission;
import com.thoughtworks.xstream.security.NoTypePermission;
import java.io.File;
import java.util.List;
import java.util.Random;
import org.optaplanner.persistence.xstream.impl.domain.solution.XStreamSolutionFileIO;

/**
 * Test-only helpers for the score-migration harness.
 *
 * <p>Loads real {@link Solution} graphs from the {@code data/} sample files using the same XStream
 * configuration as {@code AspApp}, and perturbs their planning variables so the old
 * {@link AspEasyScoreCalculator} and the future ConstraintProvider can be compared on identical
 * states (differential testing). See {@code CONSTRAINTS.md}.
 */
final class TestFixtures {

    static final String SOLVED_7EMP = "data/solved/asp_8-5_23_7employees_forbidden.xml";
    static final String UNSOLVED_7EMP = "data/unsolved/asp_7employees_forbidden.xml";
    static final String UNSOLVED_7EMP_MANDATORY = "data/unsolved/asp_7employees_forbidden_mandatory.xml";

    static final List<String> ALL_FIXTURES = List.of(SOLVED_7EMP, UNSOLVED_7EMP, UNSOLVED_7EMP_MANDATORY);

    private TestFixtures() {
    }

    /** The nine flag-gated constraints, each able to toggle itself on a {@link Configurator}. */
    enum Flag {
        HOURS_PER_WEEK(Configurator::setHoursPerWeekCheck),
        SHIFTS_PER_DAY(Configurator::setShiftsPerDayCheck),
        BREAK_LENGTH(Configurator::setBreakLenghtCheck),
        SHIFT_LENGTH(Configurator::setShiftLenghtCheck),
        EMPLOYEES_PER_PERIOD(Configurator::setEmployeesPerPeriodCheck),
        HOURS_PER_DAY(Configurator::setHoursPerDayCheck),
        OVERNIGHT_REST(Configurator::setOvernightRestCheck),
        MANDATORY_SHIFTS(Configurator::setMandatoryShiftsCheck),
        UNIFORM_DISTRIBUTION(Configurator::setUniformEmployeesDistributionCheck);

        private final java.util.function.BiConsumer<Configurator, Boolean> setter;

        Flag(java.util.function.BiConsumer<Configurator, Boolean> setter) {
            this.setter = setter;
        }

        void set(Configurator configurator, boolean value) {
            setter.accept(configurator, value);
        }
    }

    /** Turn every flag-gated constraint on or off. The always-on penalties (§1, §2.8) are unaffected. */
    static void setAllChecks(Configurator configurator, boolean value) {
        for (Flag flag : Flag.values()) {
            flag.set(configurator, value);
        }
    }

    /** Load a Solution from a {@code data/} XML file, mirroring AspApp's hardened XStream setup. */
    static Solution load(String relativePath) {
        XStreamSolutionFileIO<Solution> io = new XStreamSolutionFileIO<>(Solution.class);
        XStream xstream = io.getXStream();
        xstream.addPermission(NoTypePermission.NONE);
        xstream.addPermission(ArrayTypePermission.ARRAYS);
        xstream.allowTypesByWildcard(new String[]{
            Solution.class.getPackage().getName() + ".*"
        });
        return io.read(new File(relativePath));
    }

    /**
     * Assign a fresh, reproducible random planning state in place: every {@link ShiftAssignment}
     * gets either a random (timeGrain, shiftDuration) drawn from the solution's value ranges, or —
     * with probability {@code nullFraction} — left unassigned (both null), exactly as the solver may
     * leave over-provisioned nullable shifts. Problem facts (table, staff, value ranges) are
     * untouched.
     */
    static void randomizeAssignments(Solution solution, long seed, double nullFraction) {
        Random random = new Random(seed);
        List<TimeGrain> timeGrains = solution.getTimeGrainList();
        List<ShiftDuration> durations = solution.getShiftDurationList();
        for (ShiftAssignment assignment : solution.getShiftAssignmentList()) {
            if (random.nextDouble() < nullFraction) {
                assignment.setTimeGrain(null);
                assignment.setShiftDuration(null);
            } else {
                assignment.setTimeGrain(timeGrains.get(random.nextInt(timeGrains.size())));
                assignment.setShiftDuration(durations.get(random.nextInt(durations.size())));
            }
        }
    }
}
