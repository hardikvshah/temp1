# Robot CLI design

This document records the agreed behavior and the current Java implementation. Share it with `REVIEW_CONTEXT.md`, `src/`, `pom.xml`, and the Maven wrapper for an independent review.

## Scope and design constraints

An interactive, in-memory CLI manages multiple independent bounded grids, each with robots and obstacles. Java is the implementation language, with a handwritten command registry and Maven/JUnit for builds and tests. Keep abstractions small, put each command in a separate file, and comment every declared method. State lasts for the session; persistence, concurrent access, and grid deletion are outside the current scope.

## Command contract

| Command | Behavior |
| --- | --- |
| `grid init <rows> <columns>` | Create a grid with positive integer dimensions and return its ID, without selecting it. |
| `grid list` | List each initialized grid's own ID and dimensions from any context, including before checkout. |
| `grid checkout <grid-id>` | Select an existing grid as the context for subsequent operations. |
| `grid print` | Print borders, one-based coordinates, robot IDs, and obstacles as `*`. |
| `robot add <x> <y>` | Add a robot at a valid cell and return its generated ID and position. |
| `robot move <robot-id> <up\|down\|left\|right> <units>` | Move from its current position through available cells. |
| `robot status <robot-id>` | Return the ID and current coordinates. |
| `robot list` | Return all robot IDs and current coordinates. |
| `robot delete <robot-id>` | Remove the robot; an unknown ID is an error. |
| `obstacle add <x> <y>` | Add an obstacle; duplicate obstacles and cells containing robots are errors. |
| `obstacle delete <x> <y>` | Remove an obstacle; an empty valid cell succeeds. |
| `help` | List registered commands and argument syntax. |
| `exit` | Stop the session immediately. |

Resource commands use `<resource> <verb>`. The previous verb-first names are no longer accepted. Commands validate missing/extra arguments and numeric values. Current parsing uses signed Java `int` values, with range checks for dimensions, coordinates, and units.

## Behavioral invariants

These are the contracts against which code and tests should be reviewed.

| ID | Invariant |
| --- | --- |
| G1 | A grid must be explicitly checked out before operations on robots, obstacles, or grid contents. Grid creation/listing/checkout, help, and exit require no current grid. Creating even the first grid does not select it. |
| G2 | Dimensions are positive integers and fixed for each grid. Each `grid init` creates a new grid and preserves all existing grids and the current selection. Failed creation consumes no grid ID and preserves state. |
| G3 | Coordinates are one-based: `1 <= x <= columns` and `1 <= y <= rows`. `(1, 1)` is bottom-left; right increases `x`, up increases `y`. Zero and negative coordinates are invalid. |
| G4 | Grid IDs are unique within the session (`G1`, `G2`, etc.) and stored immutably by each grid. Listings include all initialized grids with their own IDs, row counts, and column counts in creation order, and never change selection. Unknown checkout fails and preserves the previous selection. |
| G5 | Each grid owns its dimensions, robots, obstacles, and robot ID counter. Checkout preserves those contents. Every content command resolves the current grid when it executes, including within batches. |
| R1 | Robot IDs are unique within their grid. Each grid starts with `R1`, then `R2`, etc.; deleted IDs are not reused within that grid. Failed additions do not consume IDs. |
| R2 | Multiple robots may share a cell and may move through one another. Robot operations affect only their target ID. |
| R3 | Robot status, movement, and deletion fail for an unknown or deleted ID. Listing an initialized empty grid succeeds. |
| O1 | A cell cannot contain both a robot and an obstacle. Reject placement in either direction without changing state. |
| O2 | A cell contains at most one obstacle. Adding an existing obstacle fails. |
| O3 | Obstacle deletion is idempotent for valid coordinates. It still requires initialization and in-bounds coordinates, and it never removes a robot. |
| M1 | Units are nonnegative integers. Zero units is a successful no-op for a valid robot and direction. |
| M2 | Movement never wraps or leaves the grid. An oversized move stops at the boundary; an outward move from an edge succeeds without displacement. |
| M3 | Movement examines traversed cells and stops immediately before the first obstacle. Progress before the obstacle is retained; the move succeeds. Obstacles cannot be jumped over. |
| E1 | Commands return `CommandResult(statusCode, message)`, using `0` for success and `1` for errors. Expected validation/domain failures are converted into error results. |
| E2 | An invalid individual command leaves domain state unchanged. The interactive session remains usable after an error. |
| B1 | Semicolons separate any CLI commands. Execute from left to right, stopping at the first nonzero result. Earlier successful mutations remain applied; there is no batch rollback. |
| B2 | `exit` stops the rest of its line and subsequent input. Empty input and empty semicolon segments are successful no-ops. |
| B3 | A batch result contains messages for commands actually executed. Its status is `1` if execution stopped on an error, otherwise `0`. Decisions use status codes, not message prefixes. |
| H1 | Both `-h` and `--help` work for resource and command help, including before initialization. Help never executes the requested operation or changes domain state. |
| H2 | Resource help, such as `grid -h`, lists that resource's verbs and argument syntax. Command help, such as `grid init -h`, shows that command's usage. Global `help` lists all commands. |
| P1 | Display one prompt per input line and flush it before waiting for input: `> ` before checkout, `[G1] > ` when `G1` is current. Creation and failed checkout preserve the context; successful checkout changes the next prompt. Batch commands do not introduce extra prompts. End-of-input ends the session. |
| P2 | Print rows from highest `y` to lowest, with column labels from left to right. Show all robot IDs in shared cells, widen cells as needed, and show obstacles as `*`. |
| S1 | Unknown commands remain errors even when a suggestion exists. A suggestion never executes or corrects the input automatically. |

## Current implementation choices

These describe the implementation; they are not additional product requirements silently inferred from the original request.

- Command names/directions are lowercase and case-sensitive; IDs use their returned spelling. Whitespace is flexible. There is no shell quoting or escaping syntax.
- `robot list` uses creation order and the same `R1 at (x, y)` format as status. An empty list returns `No robots in grid.`. Shared-cell IDs are comma-separated.
- `grid list` prints one initialized grid per line, such as `G1: 3 rows, 4 columns.`, or `No grids initialized.`. It includes all grids, not just the checked-out one. Content commands without checkout return `Error: No grid checked out.`.
- Command help flags can appear alongside arguments; help takes precedence over argument validation. Unsupported flag spellings are not aliases.
- Suggestions use Levenshtein distance with unit insertion/deletion/substitution costs. Compare command names, excluding arguments, case-insensitively. Recommend the closest names within two edits, including all ties in registry order. Prefer the recognized resource's commands and avoid treating missing verbs or resource flags as verb typos. A transposition may cost two edits.
- The REPL prints result messages, not numeric codes. Command result codes are separate from the Java process exit code; a command error does not terminate the process.
- No explicit resource limits for dimensions, robot counts, obstacle counts, or line lengths have been agreed. Review boundary behavior without inventing new limits.

## Components and data flow

```text
stdin -> RobotCli.run -> RobotCli.execute (semicolon batch)
     -> command lookup / help / suggestions
     -> Command.execute -> command.executeValidated -> GridRegistry -> current Grid
     <- CommandResult(statusCode, message)
stdout <- result.message()
```

| Component | Responsibility |
| --- | --- |
| `src/main/java/robot/RobotCli.java` | Registry, REPL, batch execution, help dispatch, typo suggestions. |
| `src/main/java/robot/GridRegistry.java` | Grid creation and ID generation, immutable grid inventory lists, explicit checkout, current grid resolution. |
| `src/main/java/robot/Grid.java` | Immutable grid identity, dimension access, initialization, bounds, robot/obstacle state, movement, ASCII rendering. |
| `src/main/java/robot/Robot.java` | Immutable ID/position snapshot. |
| `src/main/java/robot/commands/Command.java` | Command contract and shared conversion of `IllegalArgumentException` into an error result. |
| `src/main/java/robot/commands/CommandResult.java` | Result record and success/error factories. |
| `src/main/java/robot/commands/Arguments.java` | Argument counts and integer parsing. |
| Other files in `commands/` | One file per command; validate arguments, invoke domain operations, construct results. |

`GridRegistry` uses a `LinkedHashMap<String, Grid>` and stores the current ID, initially empty. It constructs a grid with a candidate ID and initializes it before advancing the ID counter or registering it. The map key comes from `grid.id()`. Commands receive the shared registry and resolve its current grid at execution time. Each individual `Grid` remains initialized once, exposes `id()`, `rows()`, and `columns()`, and uses a `LinkedHashMap<String, Robot>` plus a set of obstacle coordinate keys. Moves replace immutable robot records, preserving their IDs. Robot listings return immutable snapshots. Grid inventory lists are immutable copies containing the actual grid objects; their IDs and initialized dimensions remain fixed. Domain methods raise `IllegalArgumentException` for expected invalid operations; `Command.execute()` translates these to status `1`.

Movement caps its step count by distance to the boundary, then checks each cell through `canEnter`. Its time is proportional to the traversed distance. Adding an obstacle scans robot positions. Printing traverses every grid cell and allocates the complete output string. These costs should remain visible in a review.

To add a command, create its file implementing `usage()` and `executeValidated()`, then register it in `RobotCli`. Global/resource help and typo candidates derive from that registry. Current resource command names have exactly two tokens.

## Validation and review baseline

Build and run with JDK 17+:

```sh
./mvnw verify
java -jar target/robot-cli-1.0-SNAPSHOT.jar
```

Use `mvnw.cmd` on Windows. The wrapper downloads Maven 3.9.11; Maven downloads JUnit 5.14.2. See README prerequisites for shell, Java, and network setup.

The latest verified baseline after the grid identity and dimensions update is **300 tests, zero failures/errors/skips**, run on JDK 26 with Java 17 compilation targeting. This does not establish execution compatibility on every JDK or OS.

| Test class | Principal coverage |
| --- | --- |
| `GridTest` | Dimensions, one-based bounds, directions, edges, shared cells, IDs, snapshots, rendering. |
| `MultipleGridTest` | Explicit checkout, listing, independent state and IDs, failed creation/checkout, selection changes, help, contextual prompts. |
| `ObstacleTest` | Placement conflicts, duplicates, idempotent deletion, path blocking, stars, obstacle command validation/help. |
| `RobotCliTest` | Commands, argument errors, initialization guards, help levels, input recovery, prompt, exit/EOF. |
| `BatchCommandTest` | Mixed resources, ordering, stop-on-error, retained changes, empty segments, exit and prompts. |
| `CommandResultTest` | Success/error codes and messages, batches, recovery, direct command invocation. |
| `CommandSuggestionTest` | Close matches, ties, irrelevant input, case, arguments, no side effects, batch interruption. |
| `RobotCliEndToEndTest` | A real child JVM; complete transcript checks for help, batches, movement, status, deletion, obstacles, rendering, prompts, and exit. |

Reviewers should compare tests to the invariants, identify missing cases rather than trusting the test count, and distinguish implementation bugs from desired future features.
