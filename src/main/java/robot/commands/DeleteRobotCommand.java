package robot.commands;

import robot.Grid;
import robot.GridRegistry;

public final class DeleteRobotCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's current grid. */
    public DeleteRobotCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "robot delete <robot-id>";
    }

    /** Validates arguments and performs the grid operation. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Grid grid = grids.currentGrid();
        Arguments.count(args, 1, usage());
        grid.deleteRobot(args[0]);
        return CommandResult.success("Deleted " + args[0] + ".");
    }
}
