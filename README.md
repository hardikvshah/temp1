# Robot CLI

An interactive Java CLI for moving multiple robots across independent bounded grids. Requires JDK 17 or newer. State lasts for the current session.

See [DESIGN.md](DESIGN.md) for the design and invariants, and [REVIEW_CONTEXT.md](REVIEW_CONTEXT.md) for portable context and instructions for an independent code/test review.

## Prerequisites

- Install a **JDK 17 or newer**, including both `java` and `javac`. Put its `bin` directory on `PATH`. If `JAVA_HOME` is set, it must point to that JDK's installation directory.
- On **macOS/Linux**, use a POSIX shell with standard system utilities and either `unzip` or `tar`. The wrapper uses `curl` or `wget` when available and otherwise uses its Java downloader.
- On **Windows**, use PowerShell and run `mvnw.cmd` from the project directory.
- Allow internet access to Maven Central for the first build, so the wrapper can download Maven, build plugins, and JUnit. Later builds can reuse the cached downloads.

Verify Java before building:

```sh
java -version
javac -version
```

Both should report version 17 or newer. Maven does not need to be installed separately: the included wrapper downloads Maven 3.9.11. JUnit is downloaded by Maven; no manual installation is needed. If you already have the built JAR, running it only requires a Java runtime 17 or newer.

## Build, test, and run

The Maven wrapper downloads Maven and dependencies on its first run; an internet connection is required.

```sh
./mvnw test
./mvnw package
java -jar target/robot-cli-1.0-SNAPSHOT.jar
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. If Maven is already installed, `mvn test` and `mvn package` also work.

The CLI displays `> ` before checkout, then `[G1] > ` when grid `G1` is selected. Separate multiple commands on one line with semicolons (`;`). Type `exit` to end the session, or send end-of-input (Ctrl-D on macOS/Linux).

## Commands

Grid, robot, and obstacle commands use `<resource> <verb>`. The session commands `help` and `exit` stand alone. Previous verb-first command spellings are no longer accepted.

| Command | Behavior |
| --- | --- |
| `grid init <rows> <columns>` | Create a grid with positive integer dimensions and return its ID. Does not select it. |
| `grid list` | List every initialized grid's ID, rows, and columns, including before checkout. |
| `grid checkout <grid-id>` | Select an existing grid as the context for robot, obstacle, and print commands. |
| `robot add <x> <y>` | Add a robot and return its ID and position. |
| `robot move <robot-id> <up\|down\|left\|right> <units>` | Move by a nonnegative integer number of cells; stop at the boundary or before an obstacle. |
| `robot status <robot-id>` | Show the robot's current coordinates. |
| `robot list` | List every robot's ID and current position, in creation order. |
| `robot delete <robot-id>` | Remove the robot; an unknown ID returns an error. |
| `grid print` | Draw the grid with borders, coordinates, and robot IDs. |
| `obstacle add <x> <y>` | Place an obstacle at a valid, unoccupied cell. |
| `obstacle delete <x> <y>` | Remove an obstacle; an empty valid cell also succeeds. |
| `help` | List all registered commands and their syntax. |
| `exit` | End the session. |

Help supports three levels:

- `help` lists all commands.
- `grid -h`, `robot --help`, or `obstacle -h` lists all supported verbs for that resource, with their argument syntax.
- `grid init -h` or `robot move --help` shows usage for that specific command.

Both `--help` and `-h` work at the resource and command levels. Help works before initialization and never executes a command; `exit -h` keeps the session running. Command help flags may also appear alongside arguments.

`robot list` prints one line per robot using the same format as `robot status`, such as `R1 at (2, 1)`. An initialized grid without robots returns `No robots in grid.`.

Coordinates are one-based, with `(1, 1)` at the bottom-left. `x` is the column and `y` is the row. Right increases `x`; up increases `y`. For `grid init 3 4`, valid coordinates are `x=1..4`, `y=1..3`.

Commands and directions use the lowercase spelling shown above; IDs use their returned spelling, such as `R1`. Leading, trailing, and repeated whitespace are accepted. Blank input does nothing.

Unknown commands return status `1` and suggest the closest registered command names within two Levenshtein edits (insertions, deletions, or substitutions). For example, `robot ad 1 1` returns `Error: Unknown command. Type help for commands. Did you mean robot add?`. Matching ignores case for suggestions and excludes coordinate arguments. If multiple names tie for the closest match, all are shown; unrelated input receives no suggestion. Suggestions never execute a command, and a typo stops the rest of a semicolon-separated line just like any other error.

Robots may share a cell. Printing lists their IDs separated by commas and expands cell widths as needed. Robot IDs are sequential within each grid and never reused in that grid. Each grid starts with `R1`, so a robot is identified by its grid and robot ID.

Obstacles appear as `*` in `grid print`. They cannot share cells with robots: adding an obstacle on a robot or a robot on an obstacle returns an error. Adding an existing obstacle also returns an error. Deleting a missing obstacle succeeds, but coordinates must still be within the initialized grid. Moving robots keep any progress made before reaching the first obstacle and stop in the cell immediately before it.

Robot, obstacle, and print commands require an explicit checkout. Before checkout they return `Error: No grid checked out.`, even if grids exist. `grid init`, `grid list`, `grid checkout`, `help`, and `exit` do not require a current grid. Unknown IDs, invalid numbers, missing or extra arguments, and out-of-bounds placements produce errors without changing state. Coordinate zero is invalid. A zero-unit move is valid. The session continues after an invalid command.

Grid IDs are `G1`, `G2`, and so on, in creation order. Each grid stores its own immutable ID and exposes its dimensions. `grid list` returns one grid per line, such as `G1: 3 rows, 4 columns.`, or `No grids initialized.` for an empty session. Creating another grid preserves the current selection. `grid checkout G2` changes context without discarding any grid's robots, obstacles, or dimensions; an unknown ID preserves the previous context. Each grid's dimensions remain fixed. There is no grid deletion command.

## Multiple commands

```text
grid init 3 4; grid checkout G1; robot add 1 1; robot move R1 right 2; robot status R1
```

Commands execute from left to right. The first error stops the rest of that line; earlier successful commands keep their changes. The next input line can run normally. `exit` stops the session immediately, including the rest of its line. Blank segments are ignored. The CLI prints one prompt per input line.

## Example

```text
grid init 2 2
grid checkout G1
robot add 1 1
robot add 1 1
robot move R1 right 5
robot status R1
robot list
grid print
robot delete R1
exit
```

`robot status R1` returns `R1 at (2, 1)`. Before deletion, `grid print` shows:

```text
    +----+----+
y=2 |    |    |
    +----+----+
y=1 | R2 | R1 |
    +----+----+
      1    2   x
```

## Design and extension

- `RobotCli` owns the input loop, error reporting, and command registry.
- `GridRegistry` stores grids, generates grid IDs, and resolves the selected grid for each operation.
- `commands/` contains one file for each command and a small shared argument validator.
- `Grid` owns its immutable ID, dimensions, initialization, robot positions, movement rules, and rendering.
- `Robot` is an immutable snapshot containing its own ID and position.

To add a command, implement `Command` in a new file and register it in the `RobotCli` constructor. The `usage()` method supplies its help entry automatically. Implement `executeValidated()` for the operation; the shared `execute()` method converts validation exceptions into error results.

Commands and `RobotCli.execute()` return `CommandResult(int statusCode, String message)`: `0` means success and `1` means error. A batch returns status `1` at the first error and combines messages from the commands executed so far. Checkout changes apply immediately to later commands in the same batch. Blank input succeeds with an empty message. The CLI displays the message, and batch execution checks the status code. These are command result codes; the interactive Java process continues after a command error.

`Grid` stores obstacle coordinates and checks each traversed cell through `canEnter`, so robots cannot jump over obstacles. Placement validation prevents robot/obstacle overlap, and rendering displays obstacles as stars.

## Test coverage

JUnit tests cover positive and negative command cases, result status codes and messages, initialization and checkout guards, independent grid state and local robot IDs, context changes and prompts, input validation, all movement directions and boundaries, rectangular and single-cell grids, robot independence, shared cells, deletion and ID allocation, obstacle placement and removal, blocked movement, exact ASCII output, error recovery, per-command help flags, robot listings, semicolon batches and error handling, exit, and end-of-input.

The end-to-end test starts the actual CLI process, initializes and checks out a grid, adds two robots, verifies movement, status, deletion, obstacles, rendering, and batches, then creates and checks out a second grid. It verifies local robot IDs and returns to the first grid with its state preserved. It compares the complete output, including prompts.
