package robot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import robot.commands.CommandResult;

class CommandSuggestionTest {
    /** Suggestions cover deletions, insertions, substitutions, transpositions, and case errors. */
    @ParameterizedTest
    @CsvSource({"robot ad 1 1,robot add", "robot addd 1 1,robot add", "robot axd 1 1,robot add",
            "robot mvoe R1 right 1,robot move", "robt add 1 1,robot add", "gird init 3 4,grid init",
            "grid prnt,grid print", "grid lst,grid list", "grid checout G1,grid checkout",
            "obstacl delete 1 1,obstacle delete", "hepl,help", "exi,exit",
            "robot AD 1 1,robot add", "ROBOT ADD 1 1,robot add", "  robot   ad  1 1  ,robot add"})
    void suggestsClosestCommand(String input, String suggestion) {
        assertEquals(new CommandResult(1, "Error: Unknown command. Type help for commands. Did you mean "
                + suggestion + "?"), new RobotCli().execute(input));
    }

    /** Equally close names are all suggested in stable registry order. */
    @Test
    void showsTiedSuggestions() {
        assertEquals(new CommandResult(1, "Error: Unknown command. Type help for commands."
                + " Did you mean robot add or robot move?"), new RobotCli().execute("robot adve"));
    }

    /** Distant names, unknown verbs, and unsupported resource flags receive no misleading suggestion. */
    @ParameterizedTest
    @ValueSource(strings = {"dance", "unknown", "robot xyzxyz", "grid unknown -h", "grid -H", "robot -help"})
    void avoidsUnrelatedSuggestions(String input) {
        assertEquals(new CommandResult(1, "Error: Unknown command. Type help for commands."),
                new RobotCli().execute(input));
    }

    /** Suggestions never add robots, and a mistyped exit does not end the session. */
    @Test
    void suggestionsDoNotExecuteCommands() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        assertEquals(1, cli.execute("robot ad 1 1").statusCode());
        assertEquals("No robots in grid.", cli.execute("robot list").message());
        assertEquals(1, cli.execute("exi").statusCode());
        assertEquals(new CommandResult(0, "R1 at (1, 1)"), cli.execute("robot add 1 1"));
    }

    /** A suggested command remains an error and stops the remainder of the batch. */
    @Test
    void stopsBatchAtSuggestedCommand() {
        RobotCli cli = new RobotCli();
        CommandResult result = cli.execute("grid init 3 4; grid checkout G1; robot ad 1 1; robot add 2 2");
        assertEquals(1, result.statusCode());
        assertTrue(result.message().endsWith("Did you mean robot add?"));
        assertEquals("No robots in grid.", cli.execute("robot list").message());
    }

    /** Correctly spelled commands retain their argument validation rather than suggesting another name. */
    @Test
    void preservesArgumentErrors() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        assertEquals(new CommandResult(1, "Error: X must be an integer."), cli.execute("robot add abc 1"));
        assertEquals(new CommandResult(1, "Error: Usage: robot add <x> <y>"), cli.execute("robot add"));
    }
}
