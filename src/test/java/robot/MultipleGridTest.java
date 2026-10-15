package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import robot.commands.CommandResult;

class MultipleGridTest {
    /** Invalid session-level commands neither initialize a grid nor select one. */
    @ParameterizedTest
    @ValueSource(strings = {"grid list extra", "grid checkout", "grid checkout G1 extra", "grid checkout G99",
            "grid init 0 2", "grid init abc 2"})
    void rejectsInvalidGridCommandsWithoutStateChanges(String command) {
        RobotCli cli = new RobotCli();
        assertEquals(1, cli.execute(command).statusCode());
        assertEquals("No grids initialized.", cli.execute("grid list").message());
        assertEquals("Grid G1 initialized: 1 rows, 1 columns.", cli.execute("grid init 1 1").message());
        assertEquals("Error: No grid checked out.", cli.execute("robot list").message());
    }

    /** Initialization creates unique grids and requires explicit checkout even for the first. */
    @Test
    void createsGridsWithoutSelectingThem() {
        GridRegistry grids = new GridRegistry();
        assertEquals(List.of(), grids.listGrids());
        assertEquals("G1", grids.initialize(3, 4));
        assertEquals("G2", grids.initialize(1, 2));
        assertEquals(List.of("G1", "G2"), grids.listGrids().stream().map(Grid::id).toList());
        assertEquals(3, grids.listGrids().get(0).rows());
        assertEquals(4, grids.listGrids().get(0).columns());
        assertEquals(1, grids.listGrids().get(1).rows());
        assertEquals(2, grids.listGrids().get(1).columns());
        assertEquals("", grids.currentGridId());
        assertThrows(IllegalArgumentException.class, grids::currentGrid);
        grids.checkout("G2");
        assertEquals("G2", grids.currentGridId());
        assertEquals("G2", grids.currentGrid().id());
        assertEquals(new Robot("R1", 2, 1), grids.currentGrid().addRobot(2, 1));
    }

    /** Failed creation preserves the selection, contents, and next grid ID. */
    @Test
    void failedCreationIsAtomic() {
        GridRegistry grids = new GridRegistry();
        assertThrows(IllegalArgumentException.class, () -> grids.initialize(0, 2));
        assertEquals("G1", grids.initialize(3, 4));
        grids.checkout("G1");
        Grid original = grids.currentGrid();
        Robot robot = original.addRobot(2, 2);
        assertThrows(IllegalArgumentException.class, () -> grids.initialize(1, -1));
        assertEquals(List.of("G1"), grids.listGrids().stream().map(Grid::id).toList());
        assertSame(original, grids.currentGrid());
        assertEquals(robot, original.status(robot.id()));
        assertEquals("G2", grids.initialize(1, 1));
        assertSame(original, grids.currentGrid());
    }

    /** Unknown checkout preserves a previous selection or leaves an empty session unselected. */
    @Test
    void failedCheckoutPreservesSelection() {
        GridRegistry grids = new GridRegistry();
        assertThrows(IllegalArgumentException.class, () -> grids.checkout("G99"));
        assertEquals("", grids.currentGridId());
        grids.initialize(2, 2);
        grids.checkout("G1");
        Grid original = grids.currentGrid();
        assertThrows(IllegalArgumentException.class, () -> grids.checkout("G99"));
        assertEquals("G1", grids.currentGridId());
        assertSame(original, grids.currentGrid());
        grids.checkout("G1");
        assertSame(original, grids.currentGrid());
    }

    /** Listing is an immutable snapshot and never changes selection. */
    @Test
    void listsGridsWithoutExposingMutableState() {
        GridRegistry grids = new GridRegistry();
        grids.initialize(2, 2);
        List<Grid> snapshot = grids.listGrids();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        grids.initialize(3, 4);
        grids.checkout("G1");
        assertEquals(List.of("G1"), snapshot.stream().map(Grid::id).toList());
        assertEquals(2, snapshot.get(0).rows());
        assertEquals(2, snapshot.get(0).columns());
        assertEquals(List.of("G1", "G2"), grids.listGrids().stream().map(Grid::id).toList());
        assertEquals("G1", grids.currentGridId());
    }

    /** All operations on grid contents require checkout, even when grids have been created. */
    @ParameterizedTest
    @ValueSource(strings = {"robot add 1 1", "robot move R1 right 1", "robot delete R1", "robot status R1",
            "robot list", "grid print", "obstacle add 1 1", "obstacle delete 1 1"})
    void requiresCheckout(String command) {
        RobotCli cli = new RobotCli();
        assertEquals(0, cli.execute("grid init 2 2").statusCode());
        assertEquals(new CommandResult(1, "Error: No grid checked out."), cli.execute(command));
        assertEquals(new CommandResult(0, "G1: 2 rows, 2 columns."), cli.execute("grid list"));
    }

    /** Lists every initialized grid from any context without changing the selected one. */
    @Test
    void listsFromAnyGridOrNoGrid() {
        RobotCli cli = new RobotCli();
        assertEquals(new CommandResult(0, "No grids initialized."), cli.execute("grid list"));
        cli.execute("grid init 3 4; grid init 1 2");
        assertEquals(new CommandResult(0, "G1: 3 rows, 4 columns.\nG2: 1 rows, 2 columns."), cli.execute("grid list"));
        cli.execute("grid checkout G1; robot add 3 2");
        assertEquals("G1: 3 rows, 4 columns.\nG2: 1 rows, 2 columns.", cli.execute("grid list").message());
        assertEquals("R1 at (3, 2)", cli.execute("robot status R1").message());
        cli.execute("grid checkout G2; robot add 1 1");
        assertEquals("G1: 3 rows, 4 columns.\nG2: 1 rows, 2 columns.", cli.execute("grid list").message());
        assertEquals("R1 at (1, 1)", cli.execute("robot status R1").message());
    }

    /** Robots, obstacles, dimensions, and local IDs remain independent across checkout. */
    @Test
    void isolatesEveryGridsContents() {
        RobotCli cli = new RobotCli();
        assertEquals(0, cli.execute("grid init 3 4; grid checkout G1; robot add 1 1; robot add 4 3;"
                + " obstacle add 3 1; robot move R1 right 9; grid init 1 2").statusCode());
        assertEquals("R1 at (2, 1)", cli.execute("robot status R1").message());
        assertEquals("R2 at (4, 3)", cli.execute("robot status R2").message());
        assertEquals("Checked out G2.\nNo robots in grid.", cli.execute("grid checkout G2; robot list").message());
        assertEquals("Error: Robot not found: R2", cli.execute("robot delete R2").message());
        assertEquals("R1 at (1, 1)", cli.execute("robot add 1 1").message());
        assertEquals("Obstacle added at (2, 1).\nR1 at (1, 1)",
                cli.execute("obstacle add 2 1; robot move R1 right 9").message());
        assertEquals("Obstacle removed at (2, 1).\nR1 at (2, 1)",
                cli.execute("obstacle delete 2 1; robot move R1 right 9").message());
        assertFalse(cli.execute("grid print").message().contains("y=3"));
        cli.execute("robot delete R1; grid checkout G1");
        assertEquals("R1 at (2, 1)\nR2 at (4, 3)", cli.execute("robot list").message());
        assertEquals("R1 at (2, 1)", cli.execute("robot move R1 right 9").message());
        assertTrue(cli.execute("grid print").message().contains("*"));
        assertTrue(cli.execute("grid print").message().contains("y=3"));
        assertEquals("R3 at (1, 1)", cli.execute("robot add 1 1").message());
        cli.execute("grid checkout G2");
        assertEquals("No robots in grid.", cli.execute("robot list").message());
        assertFalse(cli.execute("grid print").message().contains("*"));
        assertEquals("R2 at (1, 1)", cli.execute("robot add 1 1").message());
    }

    /** A failed checkout stops its batch and preserves earlier changes and the current grid. */
    @Test
    void stopsBatchAtUnknownCheckout() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 2 2; grid init 1 1; grid checkout G1");
        assertEquals(new CommandResult(1, "R1 at (1, 1)\nError: Grid not found: G99"),
                cli.execute("robot add 1 1; grid checkout G99; grid checkout G2; robot delete R1"));
        assertEquals("R1 at (1, 1)", cli.execute("robot status R1").message());
        cli.execute("grid checkout G2");
        assertEquals("No robots in grid.", cli.execute("robot list").message());
    }

    /** Help leaves the registry and current selection unchanged. */
    @Test
    void gridHelpHasNoSideEffects() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init -h; grid checkout G99 --help; grid list -h");
        assertEquals("No grids initialized.", cli.execute("grid list").message());
        cli.execute("grid init 2 2; grid checkout G1; robot add 1 1; grid init 1 1");
        cli.execute("grid init 4 4 --help; grid checkout G2 -h; grid list --help");
        assertEquals("G1: 2 rows, 2 columns.\nG2: 1 rows, 1 columns.", cli.execute("grid list").message());
        assertEquals("R1 at (1, 1)", cli.execute("robot status R1").message());
    }

    /** The prompt follows successful checkout; creation, listing, and failed checkout preserve it. */
    @Test
    void promptReflectsCurrentGrid() throws Exception {
        StringWriter output = new StringWriter();
        String input = String.join("\n", "grid list", "grid init 2 2", "grid checkout G1",
                "grid init 1 1", "grid checkout G99", "grid checkout G2", "grid list", "exit") + "\n";
        new RobotCli().run(new StringReader(input), new PrintWriter(output));
        assertEquals(String.join(System.lineSeparator(), "> No grids initialized.",
                "> Grid G1 initialized: 2 rows, 2 columns.", "> Checked out G1.",
                "[G1] > Grid G2 initialized: 1 rows, 1 columns.", "[G1] > Error: Grid not found: G99",
                "[G1] > Checked out G2.", "[G2] > G1: 2 rows, 2 columns.\nG2: 1 rows, 1 columns.",
                "[G2] > Goodbye.", ""), output.toString());
    }
}
