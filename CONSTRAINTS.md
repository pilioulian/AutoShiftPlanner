# AutoShiftPlanner — Constraint Specification

This document is the precise, behavior-level specification of the scoring logic implemented in
`AspEasyScoreCalculator.calculateScore(Solution)`. It was the reference ("oracle spec") for the
migration to **Constraint Streams**, which is now complete — `AspConstraintProvider` is
the production scorer and `AspEasyScoreCalculator` is retained as the test oracle. The migration
outcome and the per-shift-vs-legacy reconciliation are recorded in §6.

Two sources were merged:
- **Intent** (what each rule means to a user): `docs/usage.md`.
- **Exact math** (penalty formulas, edge cases): reverse-engineered from
  `AspEasyScoreCalculator.java` (the only place the math exists).

Where intent and code disagree, **the code is authoritative** and the discrepancy is flagged.

---

## 0. Score type and units

- Score is `HardSoftScore` (int hard, int soft). Returned as `HardSoftScore.of(hardScore, softScore)`.
- A **feasible** solution has `hardScore == 0`. `softScore` is always `<= 0` and (by design) can never reach 0.
- **All times are internally in half-hour "grains."** The GUI/persistence use decimal hours
  (08:30 → `8.5`). The calculator converts on entry:

  | Configurator value        | Conversion              | Internal unit        |
  |---------------------------|-------------------------|----------------------|
  | `shiftsPerDay`            | `× 2`                   | (see §2 — boundary count) |
  | `breakLenght`             | `× 2`                   | grains (half-hours)  |
  | `shiftLenghtMin/Max`      | `× 2`                   | grains               |
  | `hoursPerDay`             | `× 2`                   | grains               |
  | `overnightRest`           | `× 60`                  | minutes              |
  | `employeesPerPeriod`      | (none)                  | count                |

  (Spelling note: identifiers use `Lenght`, not `Length`.)

---

## 1. The grid model and rasterization (ALWAYS runs — not flag-gated)

Every constraint is computed on a rasterized grid `tableScore`, **not** on the
`ShiftAssignment` entities directly. This is the single most important fact for the migration.

**Grid shape.** `nR` rows × `nC` columns.
- Columns `j` = half-hour grains of a day (`startingGrainOfDay`).
- Rows `i` = `employeeIndex + dayOfWeek × numberOfEmployees`. So the grid is 7 day-blocks
  stacked vertically, each block holding one row per employee. A `(row, column)` cell is one
  employee, on one day, in one half-hour slot.

**Rasterization.** All cells reset to `worked = false`, then for each `ShiftAssignment` whose
`timeGrain` **and** `shiftDuration` are both non-null:
1. `startingGrainOfDay`, `dayOfWeek`, `durationInGrains`, and the employee's index are read.
2. `row = employeeIndex + dayOfWeek × numberOfEmployees`.
3. `finalGrainOfDay = startingGrainOfDay + durationInGrains`.
4. **Overflow penalty (ALWAYS):** if `finalGrainOfDay >= nC`, then
   `overflow = finalGrainOfDay - nC`, `finalGrainOfDay` is clamped to `nC`, and
   `hardScore -= overflow`. A shift running past the end of the grid is truncated and penalized
   one point per grain of overrun.
5. Cells `[startingGrainOfDay, finalGrainOfDay)` are set `worked = true`.

**Merge semantics (critical).** Marking is idempotent: overlapping or adjacent shifts collapse
into a single contiguous block of worked cells. An explicit overlap penalty exists but is
**commented out** (intentionally, "allow shifts to overlap may help during solving"). Consequence:
**every downstream constraint counts contiguous grid runs, not shifts.** Two back-to-back shifts
are indistinguishable from one long shift. A Constraint Streams rewrite that reasons per-entity
will diverge here unless it reproduces the merge.

**Derived `hoursWorked`.** After rasterization, each employee's `hoursWorked` is recomputed as
`(number of worked cells attributed to that employee) × 0.5`, attribution via `cell.getIdEmployee()`
(1-based). Because it counts cells, **overlap is counted once** — `hoursWorked` is *not* the sum of
shift durations.

---

## 2. Hard constraints

Each `hardScore -=` below accumulates into the same hard total. Unless noted, a constraint only
runs when its `Configurator` flag is on, which means **toggling all flags off but one isolates a
single constraint** (the only always-on contributors are §1 overflow and §2.8 forbidden cells).

### 2.1 Hours per week — `hoursPerWeekCheck`
For each employee: `hardScore -= | hoursPerWeek×2 − round(hoursWorked×2) |`.
- **Two-sided**: both under- and over-working are penalized, at half-hour granularity.
- Intent (`usage.md`): employee must work *exactly* the allotted weekly hours.

### 2.2 Shifts per day — `shiftsPerDayCheck`
Per row (one employee-day), count run boundaries `k`:
```
k = 0
for j in 1 .. nC-1:
    if j == 1 and cell(i,0).worked:        k++          # leading edge
    if cell(i,j-1).worked != cell(i,j).worked: k++      # every rising OR falling transition
at end of row: if k > shiftsPerDay (= configured × 2): hardScore -= k   # penalty is the full k
```
- For an interior worked run, `k` increments by 2 (one rising + one falling), so `k ≈ 2 × number
  of runs`, compared against `configured × 2`. Net effect: penalize when **runs-per-day > configured
  shifts-per-day**.
- **Edge subtlety:** a run that ends exactly at the last column `nC-1` contributes only its rising
  edge (the loop has no `j` past `nC-1` to see the falling edge), so it counts 1 instead of 2.
- Penalty magnitude is the entire `k`, applied once per offending row (not `k − threshold`).

### 2.3 Break length — `breakLenghtCheck`
Per row: walk each worked run, then the gap that follows it. If work **resumes** after the gap
(i.e. the gap is *between* two worked runs on the same day), let `k` = gap length in grains; if
`k != breakLenght`, `hardScore -= |k − breakLenght|`.
- Only **internal** gaps (flanked by work on both sides, same row/day) are checked. A trailing gap
  with no resumption is ignored.
- Intent: the break between two shifts in a day must equal the configured value **exactly**.

### 2.4 Shift length — `shiftLenghtCheck`
Per row, for each worked run of length `k` grains:
- `k < shiftLenghtMin` → `hardScore -= (shiftLenghtMin − k)`
- `k > shiftLenghtMax` → `hardScore -= (k − shiftLenghtMax)`
- `k == 0` (no run) → ignored.
- **Two-sided band** `[min, max]`; penalty is the distance outside the band. Per grid run.

### 2.5 Minimum employees per period — `employeesPerPeriodCheck`
A "period" = a single `(day, grain)`, i.e. for a given column `j`, one day-block of
`numberOfEmployees` rows. For each such period, `k` = how many employees are worked there; if
`k < employeesPerPeriod`, `hardScore -= (employeesPerPeriod − k)`.
- **One-sided**: only under-staffing is penalized; over-staffing is free.
- After scanning all periods, `hardScore += solution.getBonus()`. `bonus` is precomputed in
  `SolutionGenerator` to offset the baseline punishment for periods that are entirely forbidden
  (so fully-denied periods aren't double-counted as understaffed). **The migration must reproduce
  or eliminate this bonus offset consistently with how understaffing is penalized.**

### 2.6 Maximum hours per day — `hoursPerDayCheck`
Per row, `k` = count of worked cells in the row (the day's worked grains). If `k > hoursPerDay`,
`hardScore -= (k − hoursPerDay) × 2`.
- **One-sided** (only exceeding is penalized) and **penalty is doubled** ("punishment augmented by
  two"). Every row is evaluated (the loop checks the prior row at the top of each iteration plus a
  final check after the loop).

### 2.7 Mandatory shifts — `mandatoryShiftsCheck`
For each cell with `isMandatory() && !isWorked()`: `hardScore -= 10`. (Flat 10 per unworked
mandatory cell.)

### 2.8 Forbidden cells — ALWAYS runs (no flag)
For each cell with `isForbidden() && isWorked()`: `hardScore -= 10`. Runs unconditionally; the GUI
guarantees a cell is never both forbidden and mandatory/worked, so this is a solver-safety penalty.

### 2.9 Minimum overnight rest — `overnightRestCheck`
Per employee, iterate that employee's day-rows in chronological order (`i = employeeIndex`, then
`+= numberOfEmployees`). Carry `lastWorkedCellMinute` across days:
- For a day that has any worked cell (`flag`): `firstWorkedCellMinute` = `startingMinuteOfDay` of
  its first worked cell. `calculatedRest = (1440 − lastWorkedCellMinute) + firstWorkedCellMinute`
  (1440 = minutes at midnight). If `calculatedRest < overnightRest`,
  `hardScore -= (overnightRest − calculatedRest) / 30` (integer division → penalty in missing
  half-hour cells). Then set `lastWorkedCellMinute = (last worked cell's startingMinuteOfDay) + 30`.
- For a day with no worked cells: `lastWorkedCellMinute` resets to `0`.
- On the employee's first worked day, `lastWorkedCellMinute` starts at 0, so
  `calculatedRest = 1440 + firstWorkedCellMinute` (large → effectively no penalty).
- **State carried across day-rows of the same employee** — a per-entity rewrite must model the
  consecutive-day relationship explicitly.

---

## 3. Soft constraint

### 3.1 Uniform employee distribution — `uniformEmployeesDistributionCheck`
The **only** soft constraint. `numberOfPeriods` = `idPeriod` of the last cell. For each worked cell,
increment a usage counter for its `idPeriod`. Then for every period:
`softScore -= usage²`.
- Quadratic penalty favors **spreading** employees evenly across periods rather than clustering.
- Because every worked cell contributes, `softScore` is always `< 0` when anyone works; it can never
  reach 0. Lower (closer to 0) is better. Enabling it slows solving noticeably (documented in
  `usage.md`).

---

## 4. Always-on baseline (no flag)

With every `Configurator` flag off, the score still includes:
- §1 overflow penalty (shifts past grid end),
- §2.8 forbidden-cell penalty, and
- §4.1 partial-assignment penalty.

This baseline is the reference point for the per-constraint isolation tests: enable one flag, and
the delta from baseline is attributable to that one constraint.

### 4.1 Partial shift assignment — ALWAYS runs (no flag, structural)
`ShiftAssignment` has two independently unassignable variables (`allowsUnassigned=true`). A shift
with **exactly one** of `timeGrain`/`shiftDuration` set is invalid: it models no real shift, yet is
invisible to every other constraint here (they all require both, via `forEach`, which excludes
partially-assigned entities). For each such shift: `hardScore -= 1`.
- Implemented with `forEachIncludingUnassigned` (the only constraint that sees partial entities).
- **Why it exists:** it is not in the legacy `AspEasyScoreCalculator` (which simply ignores partial
  shifts). It was added during the Timefold 1.x→2.x migration because 2.0's Local Search exploits
  the independently-nullable variables — it nulls *one* variable of a shift to cheaply shed a
  penalty, stranding shifts in partial states that form local-optima traps it cannot escape (the
  solver plateaued ~3 hard short of feasibility). Penalizing the partial state restores a gradient
  out of those traps, and the production solve reaches `0hard` again.
- **Oracle-safe:** it is 0 on any fully-assigned *or* fully-unassigned schedule, so every legacy
  fixture, characterization golden-master, and exact-match differential case is unchanged, and
  feasibility-equivalence holds (a CS-feasible solution has only fully-assigned shifts, which the
  legacy calculator scores identically).

---

## 5. Migration risk register (Constraint Streams)

Behaviors most likely to diverge in a naive per-entity rewrite — each must be pinned by a test:

1. **Grid-run vs. per-shift counting (§1 merge).** Breaks (§2.3), shift length (§2.4), and
   shifts-per-day (§2.2) all count *merged contiguous runs*. Adjacent/overlapping shifts merge.
   **Resolved (§6):** Constraint Streams uses per-shift semantics, with a `No overlapping shifts`
   hard constraint so overlaps can't make the two scorers diverge.
2. **`hoursWorked` counts overlap once (§1).** Not the sum of durations.
3. **Overflow truncation + penalty (§1).**
4. **Asymmetry of penalties:** §2.1/§2.4 two-sided; §2.5 (under only), §2.6 (over only, ×2).
5. **`solution.getBonus()` offset (§2.5)** tied to fully-forbidden periods.
6. **Overnight rest carries state across consecutive day-rows per employee (§2.9)**; midnight
   boundary math; integer `/30` rounding.
7. **Quadratic soft score (§3.1)** and the "never reaches 0" property.
8. **Edge counting in §2.2** for runs that touch column 0 or the final column.
9. **Flat −10** magnitudes for mandatory/forbidden vs. distance-based penalties elsewhere — the
   relative weighting between constraints must be preserved for feasibility to mean the same thing.

These nine items were the acceptance checklist for the migration. They are covered by
`DifferentialScoreTest` (old calculator == new ConstraintProvider: exact match on no-merge fixtures,
plus a solve-and-grade gate requiring feasibility agreement) and the per-constraint
`ConstraintVerifier` cases. The old calculator is **not** removed — it is kept as the test oracle.
See §6 for how the per-shift gaps surfaced by the gate were closed.

## 6. Migration outcome (resolved)

The migration chose **per-shift, feasibility-equivalent** semantics. `AspConstraintProvider` is the
production scorer (Constraint Streams / BAVET); `AspEasyScoreCalculator` is retained as the test
oracle. Acceptance = feasibility agreement, validated by `DifferentialScoreTest`: exact match on
no-merge states (all-null, the solved fixture incl. the quadratic soft term) plus a solve-and-grade
gate (solve with the provider, grade with the legacy calculator, require `0hard`).

Notes specific to per-shift semantics that the harness surfaced and the provider had to handle:
- **Idle employees (§2.1).** Shift-grouped streams omit employees with no shifts; a separate
  `Hours per week (no shifts)` constraint over the `Employee` fact restores legacy "every employee"
  coverage.
- **Overlap (risk #1/#2).** Per-shift hours *sum* durations, so overlapping shifts would over-count
  vs. the legacy cell count. A `No overlapping shifts` hard constraint forbids same-employee overlap,
  keeping the per-shift sum equal to the legacy cell count (and matching reality — no double-booking).
  Legacy-feasible schedules are already overlap-free (overlap causes a legacy hours deficit), so this
  does not change which schedules are feasible.
- **Null-tolerant lambdas.** BAVET evaluates constraints against in-flux states during incremental
  solving (a tuple member can momentarily be unassigned). Accessors (`startGrain`, `endGrain`, …)
  return sentinels for null rather than throwing; such transient tuples are retracted on the next
  propagation, so steady-state scores are unaffected.
- **BAVET, not Drools.** The Drools CS engine hits a `groupBy` reaccumulation bug on this model;
  `aspSolverConfig.xml` sets `constraintStreamImplType = BAVET`.
