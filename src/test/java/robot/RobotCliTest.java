package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

class RobotCliTest {
    /** Grid help lists only its registered verbs without requiring initialization. */
    @ParameterizedTest
    @ValueSource(strings = {"-h", "--help"})
    void showsGridResourceHelp(String flag) {
        RobotCli cli = new RobotCli();
        String expected = "grid init <rows> <columns>\ngrid list\ngrid checkout <grid-id>\ngrid print";
        assertEquals(expected, cli.execute("grid " + flag).message());
        assertEquals("Grid G1 initialized: 3 rows, 4 columns.", cli.execute("grid init 3 4").message());
        cli.execute("grid checkout G1");
        assertEquals(expected, cli.execute("grid " + flag).message());
        assertEquals("No robots in grid.", cli.execute("robot list").message());
    }

    /** Robot help lists only robot verbs and preserves any existing robots. */
    @ParameterizedTest
    @ValueSource(strings = {"-h", "--help"})
    void showsRobotResourceHelp(String flag) {
        RobotCli cli = new RobotCli();
        String expected = String.join("\n", "robot add <x> <y>",
                "robot move <robot-id> <up|down|left|right> <units>",
                "robot delete <robot-id>", "robot status <robot-id>", "robot list");
        assertEquals(expected, cli.execute("robot " + flag).message());
        cli.execute("grid init 3 4; grid checkout G1");
        cli.execute("robot add 2 2");
        assertEquals(expected, cli.execute("robot " + flag).message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Unknown resources, verbs, and unsupported resource flags remain errors. */
    @ParameterizedTest
    @ValueSource(strings = {"unknown -h", "unknown --help", "grid unknown -h", "robot unknown --help",
            "grid -H", "robot -help", "grid -h extra", "robot --help extra"})
    void rejectsInvalidResourceHelp(String command) {
        RobotCli cli = populatedCli();
        assertEquals("Error: Unknown command. Type help for commands.", cli.execute(command).message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** The resource-first interface rejects the previous verb-first command spellings. */
    @ParameterizedTest
    @ValueSource(strings = {"init grid 3 4", "add robot 0 0", "move robot R1 up 1",
            "delete robot R1", "list robots", "status R1", "print"})
    void rejectsPreviousCommandNames(String command) {
        RobotCli cli = populatedCli();
        assertEquals("Error: Unknown command. Type help for commands.", cli.execute(command).message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Every grid-dependent command rejects execution before initialization. */
    @ParameterizedTest
    @ValueSource(strings = {"robot add 1 1", "robot move R1 up 1", "robot delete R1", "robot status R1", "robot list", "grid print",
            "obstacle add 1 1", "obstacle delete 1 1"})
    void requiresInitializedGrid(String command) {
        assertEquals("Error: No grid checked out.", new RobotCli().execute(command).message());
    }

    /** Help and exit are available before any grid exists. */
    @Test
    void helpListsEveryRegisteredCommand() {
        RobotCli cli = new RobotCli();
        assertEquals(String.join("\n",
                "grid init <rows> <columns>",
                "grid list", "grid checkout <grid-id>",
                "robot add <x> <y>",
                "robot move <robot-id> <up|down|left|right> <units>",
                "robot delete <robot-id>",
                "robot status <robot-id>", "robot list", "grid print",
                "obstacle add <x> <y>", "obstacle delete <x> <y>", "help", "exit"), cli.execute("help").message());
        assertEquals("Goodbye.", cli.execute("exit").message());
    }

    /** Invalid dimensions and argument counts do not prevent later initialization. */
    @ParameterizedTest
    @ValueSource(strings = {"grid init", "grid init 3", "grid init 3 4 extra", "grid init 0 4",
            "grid init -1 4", "grid init 3 0", "grid init 3 -1", "grid init 1.5 4",
            "grid init 3 abc", "grid init 2147483648 4"})
    void rejectsInvalidInitialization(String command) {
        RobotCli cli = new RobotCli();
        assertTrue(cli.execute(command).message().startsWith("Error:"));
        assertEquals("Grid G1 initialized: 3 rows, 4 columns.", cli.execute("grid init 3 4").message());
    }

    /** Every command validates missing or extra arguments before changing state. */
    @ParameterizedTest
    @ValueSource(strings = {"robot add", "robot add 1", "robot add 2 2 extra",
            "robot move", "robot move R1 up", "robot move R1 up 1 extra",
            "robot delete", "robot delete R1 extra", "robot status", "robot status R1 extra",
            "robot list extra", "grid print extra", "grid list extra", "grid checkout", "grid checkout G1 extra",
            "help extra", "exit extra"})
    void rejectsIncorrectArgumentCounts(String command) {
        RobotCli cli = populatedCli();
        assertTrue(cli.execute(command).message().startsWith("Error: Usage:"));
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Invalid placement values leave the robot registry and ID counter unchanged. */
    @ParameterizedTest
    @ValueSource(strings = {"robot add 0 1", "robot add 1 0", "robot add -1 1", "robot add 1 -1",
            "robot add 5 1", "robot add 1 4", "robot add abc 1", "robot add 1 1.5", "robot add 2147483648 1"})
    void rejectsInvalidPlacement(String command) {
        RobotCli cli = populatedCli();
        assertTrue(cli.execute(command).message().startsWith("Error:"));
        assertEquals("R2 at (1, 1)", cli.execute("robot add 1 1").message());
    }

    /** Invalid moves report an error and preserve the robot position. */
    @ParameterizedTest
    @ValueSource(strings = {"robot move R1 diagonal 1", "robot move R1 up -1",
            "robot move R1 up 1.5", "robot move R1 up abc", "robot move R1 up 2147483648"})
    void rejectsInvalidMoves(String command) {
        RobotCli cli = populatedCli();
        assertTrue(cli.execute(command).message().startsWith("Error:"));
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Unknown robot IDs fail consistently across all robot lookup commands. */
    @ParameterizedTest
    @ValueSource(strings = {"robot status R99", "robot move R99 right 1", "robot delete R99"})
    void rejectsUnknownRobotIds(String command) {
        RobotCli cli = populatedCli();
        assertEquals("Error: Robot not found: R99", cli.execute(command).message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Creating a second grid preserves the selected grid and its robots. */
    @Test
    void createsAnotherGridWithoutChangingSelection() {
        RobotCli cli = populatedCli();
        assertEquals("Grid G2 initialized: 1 rows, 1 columns.", cli.execute("grid init 1 1").message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
        assertEquals("G1: 3 rows, 4 columns.\nG2: 1 rows, 1 columns.", cli.execute("grid list").message());
    }

    /** Successful move, status, and delete commands return useful output. */
    @Test
    void executesRobotCommands() {
        RobotCli cli = populatedCli();
        assertEquals("R1 at (4, 2)", cli.execute("robot move R1 right 5").message());
        assertEquals("R1 at (4, 2)", cli.execute("robot status R1").message());
        assertEquals("R1 at (4, 2)", cli.execute("robot move R1 up 0").message());
        assertEquals("Deleted R1.", cli.execute("robot delete R1").message());
        assertEquals("Error: Robot not found: R1", cli.execute("robot delete R1").message());
        assertEquals("Error: Robot not found: R1", cli.execute("robot status R1").message());
        assertEquals("Error: Robot not found: R1", cli.execute("robot move R1 up 1").message());
    }

    /** Blank lines are harmless, whitespace is flexible, and unknown commands allow recovery. */
    @Test
    void handlesInputAndRecoversFromErrors() {
        RobotCli cli = new RobotCli();
        assertEquals("", cli.execute("  \t ").message());
        assertEquals("Error: Unknown command. Type help for commands.", cli.execute("dance").message());
        assertEquals("Grid G1 initialized: 3 rows, 4 columns.", cli.execute("  grid  init\t3 4  ").message());
        cli.execute("grid checkout G1");
        assertEquals("R1 at (2, 2)", cli.execute("robot add 2 2").message());
    }

    /** Exit stops processing immediately, even when more input is available. */
    @Test
    void stopsOnExit() throws Exception {
        StringWriter output = new StringWriter();
        new RobotCli().run(new StringReader("exit\ngrid init 3 4\n"), new PrintWriter(output));
        assertEquals("> Goodbye." + System.lineSeparator(), output.toString());
    }

    /** End-of-input ends a session cleanly without requiring exit. */
    @Test
    void stopsAtEndOfInput() throws Exception {
        StringWriter output = new StringWriter();
        new RobotCli().run(new StringReader("help\n"), new PrintWriter(output));
        assertTrue(output.toString().contains("grid init <rows> <columns>"));
        assertFalse(output.toString().contains("Goodbye."));
        assertTrue(output.toString().startsWith("> "));
        assertTrue(output.toString().endsWith("> "));
    }

    /** Blank input prompts again, and exit does not leave an extra prompt behind. */
    @Test
    void promptsForEachInputLine() throws Exception {
        StringWriter output = new StringWriter();
        new RobotCli().run(new StringReader("\nexit\n"), new PrintWriter(output));
        assertEquals("> > Goodbye." + System.lineSeparator(), output.toString());
    }

    /** Both flags show each command's usage before initialization without executing it. */
    @ParameterizedTest
    @CsvSource({"grid init,grid init <rows> <columns>", "grid list,grid list",
            "grid checkout,grid checkout <grid-id>", "robot add,robot add <x> <y>",
            "robot move,robot move <robot-id> <up|down|left|right> <units>",
            "robot delete,robot delete <robot-id>", "robot status,robot status <robot-id>",
            "robot list,robot list", "grid print,grid print", "obstacle add,obstacle add <x> <y>",
            "obstacle delete,obstacle delete <x> <y>", "help,help", "exit,exit"})
    void supportsHelpForEveryCommand(String command, String usage) {
        for (String flag : new String[] {"--help", "-h"}) {
            RobotCli cli = new RobotCli();
            assertEquals("Usage: " + usage, cli.execute(command + " " + flag).message());
            assertEquals("Grid G1 initialized: 3 rows, 4 columns.", cli.execute("grid init 3 4").message());
            cli.execute("grid checkout G1");
            assertEquals("No robots in grid.", cli.execute("robot list").message());
        }
    }

    /** Help flags also work alongside arguments without moving, deleting, or resetting state. */
    @ParameterizedTest
    @ValueSource(strings = {"grid init 1 1 --help", "robot add 1 1 -h",
            "robot move R1 right 2 --help", "robot move -h R1 right 2", "robot delete R1 -h",
            "robot status R1 --help", "robot list -h", "grid print --help", "help -h", "exit --help",
            "obstacle add 1 1 -h", "obstacle delete 1 1 --help", "grid checkout G99 -h", "grid list --help"})
    void helpPreservesExistingState(String command) {
        RobotCli cli = populatedCli();
        assertTrue(cli.execute(command).message().startsWith("Usage:"));
        assertEquals("R1 at (2, 2)", cli.execute("robot list").message());
        assertEquals("R2 at (1, 1)", cli.execute("robot add 1 1").message());
    }

    /** Unsupported flags remain validation errors rather than executing commands. */
    @ParameterizedTest
    @ValueSource(strings = {"robot status -H", "robot list -help", "grid print --unknown"})
    void rejectsUnsupportedFlags(String command) {
        RobotCli cli = populatedCli();
        assertTrue(cli.execute(command).message().startsWith("Error:"));
        assertEquals("R1 at (2, 2)", cli.execute("robot list").message());
    }

    /** Listings show shared positions, reflect movement and deletion, and handle an empty grid. */
    @Test
    void listsAllRobotsWithCurrentStatus() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        assertEquals("No robots in grid.", cli.execute("robot list").message());
        cli.execute("robot add 2 2");
        cli.execute("robot add 2 2");
        assertEquals("R1 at (2, 2)\nR2 at (2, 2)", cli.execute("robot list").message());
        cli.execute("robot move R1 right 5");
        assertEquals("R1 at (4, 2)\nR2 at (2, 2)", cli.execute("robot list").message());
        cli.execute("robot delete R1");
        assertEquals("R2 at (2, 2)", cli.execute("robot list").message());
        cli.execute("robot delete R2");
        assertEquals("No robots in grid.", cli.execute("robot list").message());
    }

    /** Creates an independent CLI containing one robot at a known position. */
    private static RobotCli populatedCli() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        cli.execute("robot add 2 2");
        return cli;
    }
}
