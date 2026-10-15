# Portable context for an independent Robot CLI review

## Review request

Review the attached Java code and tests against the contracts below and `DESIGN.md`. This file is self-contained context from the requirements discussion; no prior chat is required. Report findings rather than modifying code unless explicitly asked.

For each actionable finding, give severity, source file and line, a reproducible command sequence or test case, expected versus actual behavior, and the violated invariant. Separately list meaningful test gaps and unresolved product questions. Passing tests are evidence, not proof. Avoid proposing frameworks or large abstractions without a concrete need.

## What to share

Send this file and `DESIGN.md` together with `README.md`, `pom.xml`, `src/main/java/`, `src/test/java/`, `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`. Do not rely on a local absolute path, this conversation, or the build output being available. The files do not require credentials or external services beyond normal Maven downloads.

## Agreed requirements

- Java implementation, handwritten registry, Maven and JUnit. Keep the code simple, place each command in its own file, and add a brief comment to every declared method.
- Multiple independent in-memory grids per session. `grid init` creates a grid and returns its ID without selecting it, even for the first grid. Positive integer rows/columns remain fixed for each grid. Creating a new grid preserves existing grids and selection.
- Each grid stores its own immutable ID (`G1`, `G2`, etc.) and exposes its dimensions. `grid list` lists all initialized grids with IDs, rows, and columns in creation order, from any context, such as `G1: 3 rows, 4 columns.`; an empty session succeeds with `No grids initialized.`. `grid checkout <grid-id>` selects an existing grid. Unknown checkout fails without changing selection. Robot/obstacle/print commands require explicit checkout and resolve the current grid on every execution.
- One-based coordinates: `x` is the column, `y` is the row, bottom-left is `(1, 1)`. Bounds are inclusive: `x=1..columns`, `y=1..rows`. Right/up increase their respective axes. Zero is invalid.
- Generated robot IDs are unique within each grid and not reused after deletion. Each grid starts at `R1`; identical IDs in different grids identify independent robots. Multiple robots may share a cell and do not block one another. Failed additions must not consume an ID; failed grid creation must not consume a grid ID.
- Movement accepts up/down/left/right and nonnegative integer units. Zero is a valid no-op. Stop at an edge without wrapping. Stop before the first obstacle while preserving progress. Clipped or blocked moves succeed.
- Robot deletion requires an existing ID. Status/move/delete for an unknown or deleted robot return an error. A robot operation must not affect another robot.
- Obstacles are addressed by coordinates. Duplicate addition is an error. Robots and obstacles cannot overlap, regardless of which is added first. Deleting a missing obstacle succeeds only if its coordinates are valid; it never deletes a robot.
- ASCII printing includes borders, one-based coordinate labels, all robot IDs, and obstacles as `*`. Shared cells show all IDs and can widen cells.
- Every command returns a status code and message: `0` success, `1` error. Individual invalid commands must not change state. Keep the session usable after errors.
- Semicolons separate any CLI commands. Run in order and stop at the first error or `exit`. Prior successful changes remain applied. A batch combines executed messages and reports the error status if one occurred. Ignore blank input/segments.
- Prompt is `> ` before checkout, then `[G1] > ` for current grid `G1`, once per input line, flushed before reading. Creation and failed checkout preserve it; successful checkout changes the next prompt. `exit` skips all remaining input; EOF also ends the session.
- Global `help` lists commands. `<resource> -h` lists its verbs. `<resource> <verb> -h` shows that command's usage. Both `-h` and `--help` work. Help is available before initialization and has no side effects, including `exit -h`.
- Unknown commands should recommend a close registered command when available, e.g. `robot ad` suggests `robot add`. A suggestion remains status `1` and must not execute anything.

## Command syntax

```text
grid init <rows> <columns>
grid list
grid checkout <grid-id>
grid print
robot add <x> <y>
robot move <robot-id> <up|down|left|right> <units>
robot status <robot-id>
robot list
robot delete <robot-id>
obstacle add <x> <y>
obstacle delete <x> <y>
help
exit
```

Only the resource-first names are supported. Old examples using `init grid`, `add robot`, bare `status`, or bare `print` are obsolete. Zero-based examples and the old single-grid CLI restriction are obsolete. Obstacles, multiple robots, and multiple grids are implemented now. Each `Grid` object still initializes once; `grid init` creates another object rather than resetting one.

## Implementation policies to preserve or assess

These details were selected during implementation; distinguish them from the explicit requirements above when reporting findings.

- Java 17 compilation target; inputs use Java `int` parsing. Commands/directions are lowercase and case-sensitive; suggestion matching ignores case. IDs such as `R1` retain their spelling.
- Listings follow creation order and use `R1 at (x, y)`. An empty initialized grid says `No robots in grid.`. Shared-cell labels use comma-separated IDs.
- Suggestions use Levenshtein distance, maximum two edits, ignoring arguments. Show all closest ties in registry order. Restrict to a recognized resource when applicable. Suggestions do not repair flags or missing verbs.
- Command help takes precedence when a supported help flag is present among its arguments. Content operations require checkout; `grid init`, `grid list`, `grid checkout`, help, and exit are session-level operations.
- `CommandResult` is a record with `statusCode()` and `message()`. The shared `Command.execute()` catches expected validation exceptions from `executeValidated()`. `Grid` itself uses exceptions for domain validation.
- The CLI prints messages only. Result status codes are not automatically OS process exit codes. Batch stopping must use the numeric result, not matching `Error:` text.
- No persistence, grid deletion, concurrent mutation, shell quoting/escaping, or explicit upper resource limits have been agreed. Identify problems that matter to current behavior; label future feature suggestions separately.

## Code map

- `RobotCli.java`: command registration, dispatch, help, suggestions, batches, input/output.
- `GridRegistry.java`: grid IDs, creation, listing, selection, and current grid lookup.
- `Grid.java`: immutable grid ID, dimension access, robot/obstacle state, placement, movement and rendering.
- `Robot.java`: immutable record containing its own robot ID and position.
- `commands/Command.java`, `CommandResult.java`, `Arguments.java`: shared command contract, result and validation.
- Remaining files in `commands/`: individual commands.
- `src/test/java/robot/`: eight JUnit test classes covering domain behavior, grid isolation/checkout, CLI, batches, results, suggestions, obstacles, and a real-process end-to-end transcript.

## Run and validate

Install JDK 17+ with both `java` and `javac` on PATH; a set `JAVA_HOME` must point to that JDK. macOS/Linux use a POSIX shell and `unzip` or `tar`; Windows uses PowerShell with `mvnw.cmd`. First builds need Maven Central access. The wrapper supplies Maven 3.9.11; JUnit 5.14.2 is a test dependency.

```sh
java -version
javac -version
./mvnw verify
java -jar target/robot-cli-1.0-SNAPSHOT.jar
```

Latest verified baseline after the grid identity and dimensions update: **300 tests passed, zero failures/errors/skips**, on JDK 26 with release target 17. Re-run in the reviewing environment; do not claim fresh execution from this recorded result.

Useful review scenarios:

```text
grid init 3 4; grid checkout G1; robot add 1 1; obstacle add 3 1; robot move R1 right 9; robot status R1
```

Expected final position: `(2, 1)`, status `0`. Deleting the obstacle then moving right reaches `(4, 1)`.

```text
robot delete R99; robot delete R1
```

With `R1` still present, the first command returns status `1`; the second command must not execute. `R1` remains present.

```text
robot ad 1 1; robot add 2 2
```

Suggest `robot add` and return status `1`. Neither addition runs. Resource/command help must also leave all state untouched.

Review numeric bounds, single-cell and rectangular grids, shared robots, obstacle adjacency and path traversal, repeated deletion, partial batches, exact rendering orientation, suggestion ties/thresholds, and whether test cases independently verify behavior rather than reproduce the implementation.

For multiple-grid review, create two grids before any checkout and verify content commands still fail. Then alternate checkout with robot/obstacle mutations and printing, including in a single batch. Check that IDs start locally at `R1`, dimensions and obstacles stay isolated, listing/help are read-only, and failed checkout neither changes the prompt nor executes the rest of its batch.
