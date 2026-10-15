package robot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GridTest {
    /** Identity and dimensions belong to the grid and survive operations and failed reinitialization. */
    @Test
    void ownsItsIdentityAndDimensions() {
        Grid grid = new Grid("G42");
        grid.initialize(3, 4);
        Robot robot = grid.addRobot(1, 1);
        assertEquals("R1", robot.id());
        assertEquals(robot.id(), grid.move(robot.id(), "right", 2).id());
        assertThrows(IllegalArgumentException.class, () -> grid.initialize(1, 1));
        assertEquals("G42", grid.id());
        assertEquals(3, grid.rows());
        assertEquals(4, grid.columns());
    }

    /** Listing requires initialization and returns no robots for an empty grid. */
    @Test
    void listsEmptyGridAfterInitialization() {
        Grid grid = new Grid("G1");
        assertThrows(IllegalArgumentException.class, grid::listRobots);
        grid.initialize(3, 4);
        assertTrue(grid.listRobots().isEmpty());
    }

    /** Listings reflect movement and deletion while earlier snapshots remain unchanged. */
    @Test
    void listsCurrentRobotPositions() {
        Grid grid = grid(3, 4);
        Robot first = grid.addRobot(2, 2);
        Robot second = grid.addRobot(2, 2);
        var initial = grid.listRobots();
        assertEquals(java.util.List.of(first, second), initial);
        Robot moved = grid.move(first.id(), "right", 5);
        assertEquals(java.util.List.of(moved, second), grid.listRobots());
        assertEquals(java.util.List.of(first, second), initial);
        assertThrows(UnsupportedOperationException.class, initial::clear);
        grid.deleteRobot(first.id());
        assertEquals(java.util.List.of(second), grid.listRobots());
        grid.deleteRobot(second.id());
        assertTrue(grid.listRobots().isEmpty());
    }

    /** Invalid dimensions leave initialization available for a later valid request. */
    @ParameterizedTest
    @CsvSource({"0,3", "3,0", "-1,3", "3,-1"})
    void rejectsInvalidDimensions(int rows, int columns) {
        Grid grid = new Grid("G1");
        assertThrows(IllegalArgumentException.class, () -> grid.initialize(rows, columns));
        assertFalse(grid.isInitialized());
        grid.initialize(1, 1);
        assertTrue(grid.isInitialized());
    }

    /** Reinitialization preserves both the original dimensions and its robots. */
    @Test
    void rejectsReinitializationWithoutChangingState() {
        Grid grid = grid(3, 4);
        Robot robot = grid.addRobot(4, 3);
        assertThrows(IllegalArgumentException.class, () -> grid.initialize(1, 1));
        assertEquals(robot, grid.status(robot.id()));
        assertEquals(robot, grid.move(robot.id(), "right", 1));
    }

    /** All four corners of a rectangular grid are valid positions. */
    @ParameterizedTest
    @CsvSource({"1,1", "4,1", "1,3", "4,3"})
    void acceptsCorners(int x, int y) {
        Grid grid = grid(3, 4);
        Robot robot = grid.addRobot(x, y);
        assertEquals(new Robot("R1", x, y), robot);
        assertEquals(robot, grid.status(robot.id()));
    }

    /** Zero, negative, and coordinates beyond either dimension are invalid. */
    @ParameterizedTest
    @CsvSource({"0,1", "1,0", "-1,1", "1,-1", "5,1", "1,4"})
    void rejectsOutsidePositions(int x, int y) {
        Grid grid = grid(3, 4);
        assertThrows(IllegalArgumentException.class, () -> grid.addRobot(x, y));
        assertEquals("R1", grid.addRobot(1, 1).id());
    }

    /** Each direction changes the expected axis from the bottom-left origin. */
    @ParameterizedTest
    @CsvSource({"up,3,4", "down,3,2", "left,2,3", "right,4,3"})
    void movesInEveryDirection(String direction, int x, int y) {
        Grid grid = grid(5, 5);
        Robot robot = grid.addRobot(3, 3);
        assertEquals(new Robot(robot.id(), x, y), grid.move(robot.id(), direction, 1));
    }

    /** Exact moves and oversized moves both stop at the corresponding edge. */
    @ParameterizedTest
    @CsvSource({"up,3,5", "down,3,1", "left,1,3", "right,5,3"})
    void stopsAtEveryBoundary(String direction, int x, int y) {
        for (int units : new int[] {2, 5, Integer.MAX_VALUE}) {
            Grid grid = grid(5, 5);
            Robot robot = grid.addRobot(3, 3);
            Robot edge = new Robot(robot.id(), x, y);
            assertEquals(edge, grid.move(robot.id(), direction, units));
            assertEquals(edge, grid.move(robot.id(), direction, 1));
        }
    }

    /** A one-cell grid permits shared placement but no movement. */
    @ParameterizedTest
    @ValueSource(strings = {"up", "down", "left", "right"})
    void supportsSingleCellGrid(String direction) {
        Grid grid = grid(1, 1);
        Robot robot = grid.addRobot(1, 1);
        grid.addRobot(1, 1);
        assertEquals(robot, grid.move(robot.id(), direction, Integer.MAX_VALUE));
        assertTrue(grid.print().contains("R1,R2"));
    }

    /** Zero movement succeeds without changing position. */
    @Test
    void acceptsZeroMovement() {
        Grid grid = grid(3, 4);
        Robot robot = grid.addRobot(2, 2);
        assertEquals(robot, grid.move(robot.id(), "up", 0));
    }

    /** Invalid movement leaves an existing robot unchanged. */
    @Test
    void rejectsInvalidMovementWithoutChangingState() {
        Grid grid = grid(3, 4);
        Robot robot = grid.addRobot(2, 2);
        assertThrows(IllegalArgumentException.class, () -> grid.move(robot.id(), "up", -1));
        assertThrows(IllegalArgumentException.class, () -> grid.move(robot.id(), "diagonal", 1));
        assertEquals(robot, grid.status(robot.id()));
    }

    /** Shared robots remain independent, and deleted IDs are never reused. */
    @Test
    void managesMultipleRobotsIndependently() {
        Grid grid = grid(3, 4);
        Robot first = grid.addRobot(2, 2);
        Robot second = grid.addRobot(2, 2);
        assertNotEquals(first.id(), second.id());
        grid.move(first.id(), "right", 1);
        assertEquals(second, grid.status(second.id()));
        grid.deleteRobot(first.id());
        assertEquals(second, grid.status(second.id()));
        assertThrows(IllegalArgumentException.class, () -> grid.status(first.id()));
        assertThrows(IllegalArgumentException.class, () -> grid.move(first.id(), "up", 1));
        assertThrows(IllegalArgumentException.class, () -> grid.deleteRobot(first.id()));
        assertEquals("R3", grid.addRobot(1, 1).id());
    }

    /** Rendering keeps borders, column labels, and the origin in the agreed orientation. */
    @Test
    void printsEmptyGridExactly() {
        assertEquals(String.join("\n",
                "    +----+----+",
                "y=2 |    |    |",
                "    +----+----+",
                "y=1 |    |    |",
                "    +----+----+",
                "      1    2   x"), grid(2, 2).print());
    }

    /** All four corners render in their corresponding cells. */
    @Test
    void printsRobotsAtCorners() {
        Grid grid = grid(2, 2);
        grid.addRobot(1, 1);
        grid.addRobot(2, 1);
        grid.addRobot(1, 2);
        grid.addRobot(2, 2);
        assertEquals(String.join("\n",
                "    +----+----+",
                "y=2 | R3 | R4 |",
                "    +----+----+",
                "y=1 | R1 | R2 |",
                "    +----+----+",
                "      1    2   x"), grid.print());
    }

    /** Shared-cell IDs widen every cell evenly, including multi-digit IDs. */
    @Test
    void widensCellsForMultipleRobots() {
        Grid grid = grid(1, 2);
        for (int i = 0; i < 10; i++) grid.addRobot(1, 1);
        String[] lines = grid.print().split("\n");
        assertTrue(lines[1].contains("R1,R2,R3,R4,R5,R6,R7,R8,R9,R10"));
        assertEquals(lines[0].length(), lines[1].length());
        assertEquals(lines[0], lines[2]);
    }

    /** Creates an initialized grid for each independent scenario. */
    private static Grid grid(int rows, int columns) {
        Grid grid = new Grid("G1");
        grid.initialize(rows, columns);
        return grid;
    }
}
