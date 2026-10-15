package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import robot.commands.*;

class CommandResultTest {
    /** Initialization returns its status code and original success message. */
    @Test
    void returnsInitializationResult() {
        assertEquals(new CommandResult(0, "Grid G1 initialized: 3 rows, 4 columns."),
                new RobotCli().execute("grid init 3 4"));
    }

    /** Successful commands, help, empty listings, and exit all report status zero. */
    @ParameterizedTest
    @ValueSource(strings = {"robot add 1 1", "robot move R1 right 1", "robot status R1",
            "robot delete R1", "robot list", "grid print", "obstacle add 1 1", "obstacle delete 1 1",
            "help", "exit", "grid list", "grid checkout G1", "grid -h", "robot --help", "obstacle -h", "robot add --help", "exit -h"})
    void reportsSuccess(String command) {
        RobotCli cli = populatedCli();
        CommandResult result = cli.execute(command);
        assertEquals(0, result.statusCode());
        assertFalse(result.message().isEmpty());
    }

    /** Validation failures, unknown commands, and domain errors report status one. */
    @ParameterizedTest
    @ValueSource(strings = {"grid init 0 1", "grid checkout G99", "robot add 0 1", "robot add 1 4", "robot add 1.5 1",
            "robot move R1 up -1", "robot move R1 diagonal 1", "robot status R99", "robot delete R99",
            "robot list extra", "grid print extra", "obstacle add 2 2", "obstacle delete 0 1",
            "help extra", "exit extra", "unknown", "grid unknown -h"})
    void reportsError(String command) {
        CommandResult result = populatedCli().execute(command);
        assertEquals(1, result.statusCode());
        assertTrue(result.message().startsWith("Error:"));
    }

    /** Initialization guards return status one without preventing later success. */
    @Test
    void recoversAfterError() {
        RobotCli cli = new RobotCli();
        assertEquals(new CommandResult(1, "Error: No grid checked out."), cli.execute("robot list"));
        assertEquals(0, cli.execute("grid init 1 1").statusCode());
        assertEquals(1, cli.execute("robot list").statusCode());
        assertEquals(0, cli.execute("grid checkout G1").statusCode());
        assertEquals(new CommandResult(0, "No robots in grid."), cli.execute("robot list"));
    }

    /** A failed batch carries status one and messages for completed commands and its error. */
    @Test
    void reportsBatchFailure() {
        RobotCli cli = new RobotCli();
        CommandResult result = cli.execute("grid init 1 1; grid checkout G1; robot add 1 1; robot delete R99; robot delete R1");
        assertEquals(new CommandResult(1, "Grid G1 initialized: 1 rows, 1 columns.\nChecked out G1.\nR1 at (1, 1)"
                + "\nError: Robot not found: R99"), result);
        assertEquals(new CommandResult(0, "R1 at (1, 1)"), cli.execute("robot status R1"));
    }

    /** Successful batches retain all messages and status zero. */
    @Test
    void reportsBatchSuccess() {
        CommandResult result = new RobotCli().execute("grid init 1 1; grid checkout G1; robot add 1 1; robot delete R1");
        assertEquals(new CommandResult(0, "Grid G1 initialized: 1 rows, 1 columns.\nChecked out G1.\nR1 at (1, 1)\nDeleted R1."), result);
    }

    /** Blank input is a successful no-op with no message. */
    @Test
    void reportsEmptyInputSuccess() {
        assertEquals(new CommandResult(0, ""), new RobotCli().execute(" ; ; "));
    }

    /** Commands invoked directly also convert invalid arguments into error results. */
    @ParameterizedTest
    @MethodSource("commands")
    void commandsReturnErrorsDirectly(Command command) {
        CommandResult result = command.execute(new String[] {"unexpected"});
        assertEquals(1, result.statusCode());
        assertTrue(result.message().startsWith("Error:"));
    }

    /** Supplies every command with initialized state for direct validation checks. */
    private static Stream<Command> commands() {
        GridRegistry grids = new GridRegistry();
        grids.initialize(3, 4);
        grids.checkout("G1");
        return Stream.of(new InitGridCommand(grids), new ListGridsCommand(grids), new CheckoutGridCommand(grids),
                new AddRobotCommand(grids), new MoveRobotCommand(grids),
                new DeleteRobotCommand(grids), new StatusCommand(grids), new ListRobotsCommand(grids),
                new PrintCommand(grids), new AddObstacleCommand(grids), new DeleteObstacleCommand(grids),
                new HelpCommand(Map.of()), new ExitCommand(() -> fail("Invalid exit must not stop the session")));
    }

    /** Creates an independent CLI with a robot away from the placement-test cell. */
    private static RobotCli populatedCli() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1; robot add 2 2");
        return cli;
    }
}
