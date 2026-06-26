# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AutoShiftPlanner is a Java Swing desktop app that auto-generates employee work schedules using [OptaPlanner](https://www.optaplanner.org/) as the constraint-solving engine. The user defines a business week (start/end time, employees, weekly hours, constraints), marks time units as forbidden/mandatory in a grid editor, and the solver fills in shifts. There is no server, database, or web component — it's a single-process GUI application.

## Build & run

- Build (produces a runnable fat jar): `mvn clean package`
  - Outputs `target/AutoShiftPlanner-<version>-jar-with-dependencies.jar` (main class `org.betaiotazeta.autoshiftplanner.AspApp`).
- Run the app: `mvn exec:java -Dexec.mainClass=org.betaiotazeta.autoshiftplanner.AspApp`, or `java -jar target/AutoShiftPlanner-*-jar-with-dependencies.jar`. **Requires a display** (Swing GUI) — it cannot run headless.
- Java 21 and Maven are required (`maven.compiler.source/target` = 21).
- There is **no test suite, linter, or CI** in this repo. Don't look for `src/test`. Verification is manual: build, launch, load a `data/unsolved/*.xml` sample, and Solve.

## Key facts that shape the whole codebase

- **Time is measured in half-hour "grains."** The GUI and saved files express times as decimal hours (8:30 AM = `8.5`), but internally the score calculator and domain work in half-hour units. `AspEasyScoreCalculator` multiplies config values by 2 (or `*60` for minutes) on entry. When touching time math, know which unit you're in.
- **The domain uses the OptaPlanner TimeGrain pattern** (the v0.2.0 rewrite — see `docs/about.md`). Earlier versions used a "binary variable anti-pattern" (one boolean cell per employee per half-hour); that is gone. Don't reintroduce per-cell planning variables.
- **Score is `HardSoftScore`.** All user constraints are *hard* except "Uniformly distributed employees," which is the only *soft* constraint. Soft score can never reach 0 by design (employees must be assigned somewhere); lower is better. A feasible solution has hardScore 0.

## Architecture

The OptaPlanner model (annotated classes in `src/main/java/org/betaiotazeta/autoshiftplanner/`):

- `Solution` — `@PlanningSolution`. Holds the planning entities, problem facts, the `Configurator` (constraint settings), `Table`/`Business`/staff, and the `@PlanningScore`. This is the object that gets cloned and solved, and that gets serialized to/from XML.
- `ShiftAssignment` — `@PlanningEntity`. Has two **nullable** `@PlanningVariable`s: `timeGrain` (when the shift starts) and `shiftDuration` (how long). Shifts are **over-provisioned** and nullable because the number of shifts an employee needs isn't known upfront. Each carries a `Shift` (which links to an `Employee`).
- `TimeGrain` / `ShiftDuration` — value-range objects (the candidate values for the two planning variables), supplied via `ValueRangeProvider`s on `Solution`.
- `AspEasyScoreCalculator` — implements `EasyScoreCalculator`. **This is where every constraint lives.** It rasterizes all shift assignments into a `Table` of worked cells, then walks it to accumulate hard penalties (hours/week, max hours/day, max shifts/day, shift length, break length, min employees/period, overnight rest, mandatory cells, grid overflow) and the single soft penalty (uniform distribution). Adding or changing a constraint = editing this class, not adding new score-rule files.
- `ForbiddenCellSelectionFilter` — a move `SelectionFilter` referenced from the solver config; prevents the solver from proposing moves into cells the user marked forbidden.
- `Configurator` — plain bean of which constraints are enabled and their values; populated from the GUI via `AspApp.updateConfiguratorFromGui()`.
- `Employee`, `Day`, `Shift`, `Cell`, `Table`, `Business`, `TimeGrain` — domain/problem-fact data.

GUI & orchestration:

- `AspApp` — the `JFrame` main window and entry point (`main`). The Solve button (`solve_jButtonActionPerformed`) runs solving on a `SwingWorker` background thread so the UI stays responsive: it builds a `Solver` from `aspSolverConfig.xml`, generates the working `Solution` via `SolutionGenerator`, registers a `SolverEventListener` to repaint on each new best solution, and calls `solver.solve(...)`. Solving terminates by the config's time limit or `solver.terminateEarly()`.
- `SolutionGenerator` — builds the `Solution` from the current GUI state, **deep-cloning** the table and staff (important: cloning supports parallel benchmarking and keeps the GUI's live objects separate from the solver's working copy).
- `TablePanel` / `TableGraphic` — the grid editor where cells are painted forbidden/mandatory/attributed (click, drag, or mouse-wheel). Each employee gets a unique color.
- `*.form` files are NetBeans GUI-designer descriptors paired with `AspApp.java` / `TablePanel.java`. The matching `// GEN-FIRST`/`// GEN-LAST` regions in the `.java` are **machine-generated** — edit the layout via the form, or be careful editing generated regions by hand.

Solver configuration lives in XML, not code:

- `src/main/resources/.../solver/aspSolverConfig.xml` — phases (construction heuristic + local search), move selectors, the `ForbiddenCellSelectionFilter`, and termination (180s / bestScoreLimit `0hard/0soft`). Tune solver behavior here.
- `src/main/resources/.../benchmark/aspBenchmarkConfig.xml` and the `.ftl` template — OptaPlanner benchmark setup, reachable from the app's **Benchmark** menu. Benchmarking is a developer-only feature.

## Persistence

Solutions are saved/loaded as XML via XStream (`XStreamSolutionFileIO`, classes annotated with `@XStreamAlias`). Sample inputs live in `data/unsolved/`, sample outputs in `data/solved/`. Use these to reproduce behavior. Because state is XStream-serialized, renaming fields or classes changes the on-disk format and can break existing saved `.xml` files.

## Notes

- `docs/` is the GitHub Pages site (Jekyll) for the project page, including end-user usage instructions in `docs/usage.md` — useful for understanding what each constraint means from the user's perspective.
- Spelling note: the codebase uses `Lenght` (not `Length`) in identifiers like `shiftLenghtMin`, `breakLenght`. Match the existing spelling when referencing these.
