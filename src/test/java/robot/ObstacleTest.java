package robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObstacleTest {
    /** Neither obstacle operation is allowed before grid initialization. */
    @Test
    void requiresInitialization() {
        Grid grid = new Grid("G1");
        assertThrows(IllegalArgumentException.class, () -> grid.addObstacle(1, 1));
        assertThrows(IllegalArgumentException.class, () -> grid.deleteObstacle(1, 1));
    }

    /** Both operations reject coordinates outside a rectangular grid. */
    @ParameterizedTest
    @CsvSource({"0,1", "1,0", "-1,1", "1,-1", "5,1", "1,4"})
    void rejectsOutOfBoundsCoordinates(int x, int y) {
        Grid grid = grid(3, 4);
        String before = grid.print();
        assertThrows(IllegalArgumentException.class, () -> grid.addObstacle(x, y));
        assertThrows(IllegalArgumentException.class, () -> grid.deleteObstacle(x, y));
        assertEquals(before, grid.print());
    }

    /** Robots retain progress and stop before obstacles in all four directions. */
    @ParameterizedTest
    @CsvSource({"up,4,6,4,5", "down,4,2,4,3", "left,2,4,3,4", "right,6,4,5,4"})
    void stopsBeforeObstacles(String direction, int obstacleX, int obstacleY, int x, int y) {
        Grid grid = grid(7, 7);
        Robot robot = grid.addRobot(4, 4);
        grid.addObstacle(obstacleX, obstacleY);
        Robot expected = new Robot(robot.id(), x, y);
        assertEquals(expected, grid.move(robot.id(), direction, Integer.MAX_VALUE));
        assertEquals(expected, grid.move(robot.id(), direction, 1));
        assertEquals(expected, grid.move(robot.id(), direction, 0));
    }

    /** The first obstacle blocks the path, and removing it exposes the next obstacle. */
    @Test
    void respectsNearestObstacleAndDeletion() {
        Grid grid = grid(1, 7);
        Robot robot = grid.addRobot(1, 1);
        grid.addObstacle(4, 1);
        grid.addObstacle(6, 1);
        assertEquals(new Robot(robot.id(), 2, 1), grid.move(robot.id(), "right", 1));
        assertEquals(new Robot(robot.id(), 3, 1), grid.move(robot.id(), "right", 6));
        grid.deleteObstacle(4, 1);
        assertEquals(new Robot(robot.id(), 5, 1), grid.move(robot.id(), "right", 6));
        grid.deleteObstacle(6, 1);
        assertEquals(new Robot(robot.id(), 7, 1), grid.move(robot.id(), "right", 6));
    }

    /** Obstacles on other rows do not interfere with movement. */
    @Test
    void ignoresObstaclesOutsideThePath() {
        Grid grid = grid(3, 4);
        Robot robot = grid.addRobot(1, 1);
        grid.addObstacle(2, 2);
        assertEquals(new Robot(robot.id(), 4, 1), grid.move(robot.id(), "right", 10));
    }

    /** Duplicate additions fail without removing or changing the existing obstacle. */
    @Test
    void rejectsDuplicateObstacles() {
        Grid grid = grid(2, 2);
        grid.addObstacle(2, 2);
        String before = grid.print();
        assertEquals("Obstacle already exists.", assertThrows(IllegalArgumentException.class,
                () -> grid.addObstacle(2, 2)).getMessage());
        assertEquals(before, grid.print());
    }

    /** Robots and obstacles cannot overlap, including cells shared by multiple robots. */
    @Test
    void rejectsOccupiedPlacementWithoutChangingState() {
        Grid grid = grid(3, 4);
        grid.addObstacle(1, 1);
        assertThrows(IllegalArgumentException.class, () -> grid.addRobot(1, 1));
        Robot first = grid.addRobot(2, 2);
        Robot second = grid.addRobot(2, 2);
        assertEquals("R1", first.id());
        assertThrows(IllegalArgumentException.class, () -> grid.addObstacle(2, 2));
        grid.deleteRobot(first.id());
        assertThrows(IllegalArgumentException.class, () -> grid.addObstacle(2, 2));
        assertEquals(second, grid.status(second.id()));
        grid.deleteRobot(second.id());
        grid.addObstacle(2, 2);
        grid.deleteObstacle(1, 1);
        assertEquals(new Robot("R3", 1, 1), grid.addRobot(1, 1));
    }

    /** Repeated deletion succeeds and deleting a robot's cell leaves that robot intact. */
    @Test
    void deletesObstaclesIdempotently() {
        Grid grid = grid(1, 2);
        Robot robot = grid.addRobot(1, 1);
        grid.deleteObstacle(1, 1);
        assertEquals(robot, grid.status(robot.id()));
        grid.addObstacle(2, 1);
        grid.deleteObstacle(2, 1);
        grid.deleteObstacle(2, 1);
        assertEquals(new Robot(robot.id(), 2, 1), grid.move(robot.id(), "right", 1));
    }

    /** Stars use the correct coordinates and disappear when obstacles are deleted. */
    @Test
    void printsObstaclesAsStars() {
        Grid grid = grid(2, 2);
        grid.addObstacle(1, 2);
        grid.addObstacle(2, 1);
        assertEquals(String.join("\n", "    +----+----+", "y=2 | *  |    |",
                "    +----+----+", "y=1 |    | *  |", "    +----+----+",
                "      1    2   x"), grid.print());
        grid.deleteObstacle(1, 2);
        grid.deleteObstacle(2, 1);
        assertFalse(grid.print().contains("*"));
    }

    /** A one-cell grid supports an obstacle until it is replaced by a robot. */
    @Test
    void supportsSingleCellGrid() {
        Grid grid = grid(1, 1);
        grid.addObstacle(1, 1);
        assertThrows(IllegalArgumentException.class, () -> grid.addRobot(1, 1));
        grid.deleteObstacle(1, 1);
        assertEquals(new Robot("R1", 1, 1), grid.addRobot(1, 1));
    }

    /** Both commands reject malformed arguments without changing any state. */
    @ParameterizedTest
    @MethodSource("invalidCommands")
    void validatesCommandArguments(String command) {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        cli.execute("robot add 2 2");
        cli.execute("obstacle add 3 2");
        String before = cli.execute("grid print").message();
        assertTrue(cli.execute(command).message().startsWith("Error:"));
        assertEquals(before, cli.execute("grid print").message());
        assertEquals("R1 at (2, 2)", cli.execute("robot status R1").message());
    }

    /** Supplies invalid counts, numeric values, and coordinates for both commands. */
    private static Stream<String> invalidCommands() {
        return Stream.of("add", "delete").flatMap(verb -> Stream.of("", " 1", " 1 1 extra",
                " -1 1", " 1 -1", " 0 1", " 1 0", " 5 1", " 1 4", " abc 1", " 1 1.5", " 2147483648 1")
                .map(arguments -> "obstacle " + verb + arguments));
    }

    /** Resource help is derived from the registry and works before initialization. */
    @ParameterizedTest
    @ValueSource(strings = {"-h", "--help"})
    void showsObstacleResourceHelp(String flag) {
        assertEquals("obstacle add <x> <y>\nobstacle delete <x> <y>",
                new RobotCli().execute("obstacle " + flag).message());
    }

    /** CLI commands report successful and failed operations while preserving state. */
    @Test
    void executesObstacleCommands() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 3 4; grid checkout G1");
        cli.execute("robot add 1 1");
        assertEquals("Obstacle added at (3, 1).", cli.execute("obstacle add 3 1").message());
        assertEquals("Error: Obstacle already exists.", cli.execute("obstacle add 3 1").message());
        assertEquals("Error: Cannot place an obstacle on a robot.", cli.execute("obstacle add 1 1").message());
        assertEquals("Error: Cannot place a robot on an obstacle.", cli.execute("robot add 3 1").message());
        assertEquals("R1 at (2, 1)", cli.execute("robot move R1 right 10").message());
        assertEquals("Obstacle removed at (3, 1).", cli.execute("obstacle delete 3 1").message());
        assertEquals("Obstacle removed at (3, 1).", cli.execute("obstacle delete 3 1").message());
        assertEquals("R1 at (4, 1)", cli.execute("robot move R1 right 10").message());
        assertEquals("R2 at (3, 1)", cli.execute("robot add 3 1").message());
    }

    /** Help never removes an existing obstacle or places a new one. */
    @Test
    void helpPreservesObstacles() {
        RobotCli cli = new RobotCli();
        cli.execute("grid init 2 2; grid checkout G1");
        cli.execute("obstacle add 1 1");
        String before = cli.execute("grid print").message();
        assertEquals("Usage: obstacle delete <x> <y>", cli.execute("obstacle delete 1 1 -h").message());
        assertEquals("Usage: obstacle add <x> <y>", cli.execute("obstacle add 2 2 --help").message());
        assertEquals(before, cli.execute("grid print").message());
    }

    /** Creates an initialized grid for each independent scenario. */
    private static Grid grid(int rows, int columns) {
        Grid grid = new Grid("G1");
        grid.initialize(rows, columns);
        return grid;
    }
}
