package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class BatchCommandTest {
    /** Any resource can participate, and later commands observe earlier changes. */
    @Test
    void executesMixedCommandsInOrder() {
        RobotCli cli = new RobotCli();
        assertEquals(String.join("\n", "Grid G1 initialized: 3 rows, 4 columns.", "Checked out G1.",
                "R1 at (1, 1)", "Obstacle added at (3, 1).", "R1 at (2, 1)",
                "R1 at (2, 1)", "Obstacle removed at (3, 1).", "R1 at (4, 1)", "R1 at (4, 1)"),
                cli.execute("grid init 3 4; grid checkout G1; robot add 1 1; obstacle add 3 1; robot move R1 right 9;"
                        + " robot status R1; obstacle delete 3 1; robot move R1 right 9; robot list").message());
    }

    /** A first-command error prevents initialization later on the same line. */
    @Test
    void stopsAtFirstError() {
        RobotCli cli = new RobotCli();
        assertEquals("Error: No grid checked out.", cli.execute("robot add 1 1; grid init 3 4").message());
        assertEquals("Error: No grid checked out.", cli.execute("robot list").message());
    }

    /** Completed moves remain applied, but commands after an unknown command do not run. */
    @Test
    void preservesEarlierChangesAndAllowsRecovery() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1; robot add 1 1");
        assertEquals("R1 at (3, 1)\nError: Unknown command. Type help for commands.",
                cli.execute("robot move R1 right 2; dance; robot delete R1").message());
        assertEquals("R1 at (3, 1)", cli.execute("robot status R1").message());
        assertEquals("R1 at (3, 2)", cli.execute("robot move R1 up 1").message());
    }

    /** Robot lookup errors stop the batch after successful deletion. */
    @Test
    void deletionErrorsStopRemainingCommands() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1; robot add 1 1");
        assertEquals("Deleted R1.\nError: Robot not found: R1",
                cli.execute("robot delete R1; robot delete R1; robot add 2 2").message());
        assertEquals("No robots in grid.", cli.execute("robot list").message());
        assertEquals("R2 at (2, 2)", cli.execute("robot add 2 2").message());
    }

    /** Obstacle conflicts stop the batch without rolling back a successful placement. */
    @Test
    void obstacleErrorsStopRemainingCommands() {
        RobotCli cli = new RobotCli();
        assertEquals("Grid G1 initialized: 2 rows, 2 columns.\nChecked out G1.\nObstacle added at (1, 1)."
                        + "\nError: Cannot place a robot on an obstacle.",
                cli.execute("grid init 2 2; grid checkout G1; obstacle add 1 1; robot add 1 1; obstacle delete 1 1").message());
        assertTrue(cli.execute("grid print").message().contains("*"));
        assertEquals("R1 at (2, 2)", cli.execute("robot add 2 2").message());
    }

    /** Empty segments and trailing semicolons follow the existing blank-input behavior. */
    @Test
    void ignoresEmptySegments() {
        RobotCli cli = new RobotCli();
        assertEquals("", cli.execute(" ; ; ").message());
        assertEquals("Grid G1 initialized: 1 rows, 1 columns.\nChecked out G1.\nR1 at (1, 1)",
                cli.execute(" ; grid init 1 1 ;; grid checkout G1; robot add 1 1; ").message());
    }

    /** Resource and command help do not interrupt later commands in a batch. */
    @Test
    void supportsHelpWithinBatch() {
        RobotCli cli = new RobotCli();
        assertEquals("grid init <rows> <columns>\ngrid list\ngrid checkout <grid-id>\ngrid print\nUsage: robot add <x> <y>"
                        + "\nGrid G1 initialized: 1 rows, 1 columns.\nChecked out G1.\nR1 at (1, 1)",
                cli.execute("grid -h; robot add --help; grid init 1 1; grid checkout G1; robot add 1 1").message());
    }

    /** Each input line has one prompt, and exit skips the rest of its batch and later lines. */
    @Test
    void promptsOncePerLineAndStopsOnExit() throws Exception {
        StringWriter output = new StringWriter();
        new RobotCli().run(new StringReader("grid init 1 1; grid checkout G1; robot add 1 1\n"
                + "robot status R1; exit; robot delete R1\nrobot status R1\n"), new PrintWriter(output));
        String newline = System.lineSeparator();
        assertEquals("> Grid G1 initialized: 1 rows, 1 columns.\nChecked out G1.\nR1 at (1, 1)" + newline
                + "[G1] > R1 at (1, 1)\nGoodbye." + newline, output.toString());
    }
}
