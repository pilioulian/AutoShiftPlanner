# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AutoShiftPlanner is a Java Swing desktop app that auto-generates employee work schedules using [Timefold Solver](https://timefold.ai/) (the maintained fork of OptaPlanner) as the constraint-solving engine. The user defines a business week (start/end time, employees, weekly hours, constraints), marks time units as forbidden/mandatory in a grid editor, and the solver fills in shifts. There is no server, database, or web component — it's a single-process GUI application.

## Build & run

- Build (produces a runnable fat jar): `mvn clean package`
  - Outputs `target/AutoShiftPlanner-<version>-jar-with-dependencies.jar` (main class `org.betaiotazeta.autoshiftplanner.AspApp`).
- Run the app: `mvn exec:java -Dexec.mainClass=org.betaiotazeta.autoshiftplanner.AspApp`, or `java -jar target/AutoShiftPlanner-*-jar-with-dependencies.jar`. **Requires a display** (Swing GUI) — it cannot run headless.
- Java 21 and Maven are required (`maven.compiler.source/target` = 21). If `mvn` defaults to an older JDK, set `JAVA_HOME` to a JDK 21 (e.g. `/usr/lib/jvm/java-21-openjdk-amd64`) — a wrong target release fails the build.
- Solver engine: **Timefold Solver 2.2.0** (`timefold-solver-bom`, groupId `ai.timefold.solver`). This is the maintained successor to OptaPlanner 9; the codebase was migrated from OptaPlanner 8.44 (package `org.optaplanner.*` → `ai.timefold.solver.*`), then from Timefold 1.33.0 to 2.x. The 2.0 jump flattened the score package (`...api.score.buildin.hardsoft.HardSoftScore` → `...api.score.HardSoftScore`), made scores **long-based** (`hardScore()`/`softScore()` return `long`), renamed `@PlanningVariable(nullable=true)` → `allowsUnassigned=true`, moved `@PlanningId` to `...api.domain.common`, removed `ConstraintStreamImplType` (BAVET is the only engine), folded the test API into core (`...test.api.score.stream.ConstraintVerifier` → `...core.api.score.stream.test.ConstraintVerifier`; the `timefold-solver-test` artifact is gone), and switched `timefold-solver-jackson` to **Jackson 3** (`tools.jackson.*`).
- Tests: `mvn test` (JUnit 5 + `ConstraintVerifier` from `timefold-solver-core`). Run one class with `-Dtest=AspConstraintProviderTest`, one method with `-Dtest=Class#method`. No linter or CI. The GUI app itself still requires a display and cannot run headless; non-GUI verification is the test suite below.
- The test suite is the safety net for the score migration (see `CONSTRAINTS.md`): `AspConstraintProviderTest` (exact per-constraint `ConstraintVerifier` cases), `CharacterizationTest` (golden-master legacy scores), `DifferentialScoreTest` (legacy == Constraint Streams on the `data/*.xml` fixtures; one solve-and-grade test runs the solver for minutes).

## Key facts that shape the whole codebase

- **Time is measured in half-hour "grains."** The GUI and saved files express times as decimal hours (8:30 AM = `8.5`), but internally the scorers and domain work in half-hour units. Both the production `AspConstraintProvider` (its `grains()` helper) and the legacy `AspEasyScoreCalculator` multiply config values by 2 (or `*60` for minutes) on entry. When touching time math, know which unit you're in.
- **The domain uses the TimeGrain pattern** (the v0.2.0 rewrite — see `docs/about.md`). Earlier versions used a "binary variable anti-pattern" (one boolean cell per employee per half-hour); that is gone. Don't reintroduce per-cell planning variables.
- **Score is `HardSoftScore`.** All user (flag-toggled) constraints are *hard* except "Uniformly distributed employees," the only *soft* constraint; there are also always-on structural hard constraints with no flag (forbidden-cell, grid overflow, no-overlapping-shifts). Soft score can never reach 0 by design (employees must be assigned somewhere); lower is better. A feasible solution has hardScore 0.

## Architecture

The Timefold model (annotated classes in `src/main/java/org/betaiotazeta/autoshiftplanner/`):

- `Solution` — `@PlanningSolution`. Holds the planning entities, problem facts, the `Configurator` (constraint settings), `Table`/`Business`/staff, and the `@PlanningScore`. This is the object that gets cloned and solved, and that gets serialized to/from XML.
- `ShiftAssignment` — `@PlanningEntity`. Has two **unassignable** (`allowsUnassigned=true`) `@PlanningVariable`s: `timeGrain` (when the shift starts) and `shiftDuration` (how long). Shifts are **over-provisioned** and unassignable because the number of shifts an employee needs isn't known upfront. Each carries a `Shift` (which links to an `Employee`). A shift counts only when **both** variables are set; a shift with exactly one set is invalid (see the `partialAssignment` constraint below).
- `TimeGrain` / `ShiftDuration` — value-range objects (the candidate values for the two planning variables), supplied via `ValueRangeProvider`s on `Solution`.
- `AspConstraintProvider` — **the production scorer** (Constraint Streams). One method per constraint, reasoning **per shift** over `ShiftAssignment` entities (forbidden/mandatory/period rules join against derived problem facts — `GridCell`, `StaffablePeriod` — and `timeGrainList`). Thresholds/flags come from the `Configurator` problem fact. Add or change a constraint here. Semantics and the exact penalty formulas are specified in `CONSTRAINTS.md`. One structural constraint, `partialAssignment` (always on), penalizes a shift with exactly one of its two variables set: such partial states are invisible to every per-shift constraint, so without this penalty 2.0's Local Search strands shifts there as local-optima traps and cannot reach feasibility. It uses `forEachIncludingUnassigned` and is 0 on any fully-assigned or fully-unassigned schedule.
- `AspEasyScoreCalculator` — the **legacy** `EasyScoreCalculator`, no longer used in production. It rasterizes shift assignments onto a `Table` of worked cells and walks it (counting *merged runs*, not shifts). Kept as the **test oracle**: `CharacterizationTest` and `DifferentialScoreTest` compare the new provider against it. Don't delete it — that would remove the migration's safety net. Note the per-run vs. per-shift difference (`CONSTRAINTS.md` §5 risk register, §6 resolution).
- `ForbiddenCellSelectionFilter` — a move `SelectionFilter` referenced from the solver config; prevents the solver from proposing moves into cells the user marked forbidden.
- `Configurator` — plain bean of which constraints are enabled and their values; populated from the GUI via `AspApp.updateConfiguratorFromGui()`.
- `Employee`, `Day`, `Shift`, `Cell`, `Table`, `Business`, `TimeGrain` — domain/problem-fact data.

GUI & orchestration:

- `AspApp` — the `JFrame` main window and entry point (`main`). The Solve button (`solve_jButtonActionPerformed`) runs solving on a `SwingWorker` background thread so the UI stays responsive: it builds a `Solver` from `aspSolverConfig.xml`, generates the working `Solution` via `SolutionGenerator`, registers a `SolverEventListener` to repaint on each new best solution, and calls `solver.solve(...)`. Solving terminates by the config's time limit or `solver.terminateEarly()`.
- `SolutionGenerator` — builds the `Solution` from the current GUI state, **deep-cloning** the table and staff (important: cloning supports parallel benchmarking and keeps the GUI's live objects separate from the solver's working copy).
- `TablePanel` / `TableGraphic` — the grid editor where cells are painted forbidden/mandatory/attributed (click, drag, or mouse-wheel). Each employee gets a unique color.
- `*.form` files are NetBeans GUI-designer descriptors paired with `AspApp.java` / `TablePanel.java`. The matching `// GEN-FIRST`/`// GEN-LAST` regions in the `.java` are **machine-generated** — edit the layout via the form, or be careful editing generated regions by hand.

Solver configuration lives in XML, not code:

- `src/main/resources/.../solver/aspSolverConfig.xml` — the production config: `constraintProviderClass` = `AspConstraintProvider` (no `constraintStreamImplType` — it was removed in 2.0; BAVET is the only engine), `<environmentMode>NO_ASSERT</environmentMode>` (the 2.0 replacement for the removed `REPRODUCIBLE`), plus the construction-heuristic + local-search phases, move selectors, the `ForbiddenCellSelectionFilter`, and termination (180s / bestScoreLimit `0hard/0soft`). The move selectors are score-director-agnostic and were kept verbatim from the legacy config. `aspEasyScoreSolverConfig.xml` is the preserved legacy easy-score config (rollback / reference).
- `src/main/resources/.../benchmark/aspBenchmarkConfig.xml` and the `.ftl` template — Timefold benchmark setup, reachable from the app's **Benchmark** menu. Benchmarking is a developer-only feature. It loads problems via `<solutionFileIOClass>` (the in-project `JsonSolutionFileIO`).

## Persistence

Solutions are saved/loaded as **JSON** via `org.betaiotazeta.autoshiftplanner.persistence.JsonSolutionFileIO` (extends Timefold's `JacksonSolutionFileIO`). Sample inputs live in `data/unsolved/`, sample outputs in `data/solved/`, all `.json`. Use these to reproduce behavior. Renaming fields or classes changes the on-disk format and can break existing saved `.json` files.

The earlier XStream/XML format was retired in the move to Timefold (XStream's persistence module was dropped; pre-existing `data/*.xml` were converted to JSON). `JsonSolutionFileIO` configures the Jackson `ObjectMapper` to faithfully mirror the old model. **Note:** Timefold 2.0's `timefold-solver-jackson` uses **Jackson 3** (`tools.jackson.databind.*`; annotations stay at `com.fasterxml.jackson.annotation`), whose `ObjectMapper` is immutable — it is built via `JsonMapper.builder()...build()` (the 1.x mutators `registerModule`/`setVisibility`/`enable` are gone; visibility is set through `changeDefaultVisibility(...)`). The configuration is: **field-based** access (so computed, setter-less getters like `getGridCells()`/`getStaffablePeriods()`/`ShiftAssignment.getId()` are skipped and recomputed, and the multi-arg domain constructors aren't mistaken for creators — private no-arg constructors on `Table`/`Cell`/`Employee`/`Business` are used instead), plus `@JsonIdentityInfo` on the shared classes (`Employee`, `Day`, `TimeGrain`, `ShiftDuration`, `Shift`) to preserve object identity — the JSON equivalent of XStream's id-references, which keeps an assigned `TimeGrain`/`ShiftDuration` the same instance as its value-range element. `JsonRoundTripTest` guards this (identical score + preserved identity after a write/read round-trip).

## Notes

- `docs/` is the GitHub Pages site (Jekyll) for the project page, including end-user usage instructions in `docs/usage.md` — useful for understanding what each constraint means from the user's perspective.
- Spelling note: the codebase uses `Lenght` (not `Length`) in identifiers like `shiftLenghtMin`, `breakLenght`. Match the existing spelling when referencing these.
