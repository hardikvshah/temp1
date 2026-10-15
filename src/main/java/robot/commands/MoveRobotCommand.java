package robot.commands;

import robot.Grid;
import robot.GridRegistry;
import robot.Robot;

public final class MoveRobotCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public MoveRobotCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "robot move <robot-id> <up|down|left|right> <units>";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 3, usage());
        Robot robot = grid.move(args[0], args[1], Arguments.integer(args[2], "Units"));
        return CommandResult.success(robot.id() + " at (" + robot.x() + ", " + robot.y() + ")");
    }
}
