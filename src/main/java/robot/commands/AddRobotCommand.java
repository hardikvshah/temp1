package robot.commands;

import robot.Grid;
import robot.GridRegistry;
import robot.Robot;

public final class AddRobotCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public AddRobotCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "robot add <x> <y>";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 2, usage());
        Robot robot = grid.addRobot(Arguments.integer(args[0], "X"), Arguments.integer(args[1], "Y"));
        return CommandResult.success(robot.id() + " at (" + robot.x() + ", " + robot.y() + ")");
    }
}
