package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RobotCliEndToEndTest {
    /** Exercises the real CLI process and verifies its complete output, including grid rendering. */
    @Test
    void completesInteractiveSession() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(RobotCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        Process process = new ProcessBuilder(java, "-cp", classes, "robot.RobotCli")
                .redirectErrorStream(true).start();
        try {
            String input = String.join("\n", "grid -h", "robot --help", "grid init -h",
                    "robot move --help", "grid init 2 2; grid checkout G1; robot list",
                    "robot add 1 1", "robot add 1 1", "robot move R1 right 5",
                    "robot status R1", "robot status R2", "robot list", "grid print",
                    "robot delete R1", "robot status R1", "robot list", "grid print",
                    "obstacle -h", "obstacle add 2 1", "obstacle add 2 1", "robot move R2 right 5",
                    "grid print", "obstacle delete 2 1", "obstacle delete 2 1",
                    "robot move R2 right 5; robot status R2; robot delete R99; robot delete R2",
                    "robot list", "grid init 1 1; grid list", "grid checkout G2; robot add 1 1; grid print",
                    "grid checkout G1; robot status R2", "exit -h", "exit; robot delete R2", "robot status R2") + "\n";
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "CLI failed to exit");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, process.exitValue());
            assertEquals(String.join("\n",
                    "Robot CLI. Type help for commands.",
                    "> grid init <rows> <columns>", "grid list", "grid checkout <grid-id>", "grid print",
                    "> robot add <x> <y>", "robot move <robot-id> <up|down|left|right> <units>",
                    "robot delete <robot-id>", "robot status <robot-id>", "robot list",
                    "> Usage: grid init <rows> <columns>",
                    "> Usage: robot move <robot-id> <up|down|left|right> <units>",
                    "> Grid G1 initialized: 2 rows, 2 columns.",
                    "Checked out G1.",
                    "No robots in grid.",
                    "[G1] > R1 at (1, 1)", "[G1] > R2 at (1, 1)", "[G1] > R1 at (2, 1)", "[G1] > R1 at (2, 1)", "[G1] > R2 at (1, 1)",
                    "[G1] > R1 at (2, 1)", "R2 at (1, 1)",
                    "[G1] >     +----+----+",
                    "y=2 |    |    |",
                    "    +----+----+",
                    "y=1 | R2 | R1 |",
                    "    +----+----+",
                    "      1    2   x",
                    "[G1] > Deleted R1.", "[G1] > Error: Robot not found: R1",
                    "[G1] > R2 at (1, 1)",
                    "[G1] >     +----+----+",
                    "y=2 |    |    |",
                    "    +----+----+",
                    "y=1 | R2 |    |",
                    "    +----+----+",
                    "      1    2   x",
                    "[G1] > obstacle add <x> <y>", "obstacle delete <x> <y>",
                    "[G1] > Obstacle added at (2, 1).",
                    "[G1] > Error: Obstacle already exists.",
                    "[G1] > R2 at (1, 1)",
                    "[G1] >     +----+----+",
                    "y=2 |    |    |",
                    "    +----+----+",
                    "y=1 | R2 | *  |",
                    "    +----+----+",
                    "      1    2   x",
                    "[G1] > Obstacle removed at (2, 1).",
                    "[G1] > Obstacle removed at (2, 1).",
                    "[G1] > R2 at (2, 1)", "R2 at (2, 1)", "Error: Robot not found: R99", "[G1] > R2 at (2, 1)",
                    "[G1] > Grid G2 initialized: 1 rows, 1 columns.",
                    "G1: 2 rows, 2 columns.", "G2: 1 rows, 1 columns.",
                    "[G1] > Checked out G2.", "R1 at (1, 1)",
                    "    +----+", "y=1 | R1 |", "    +----+", "      1   x",
                    "[G2] > Checked out G1.", "R2 at (2, 1)",
                    "[G1] > Usage: exit",
                    "[G1] > Goodbye.", ""), output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
