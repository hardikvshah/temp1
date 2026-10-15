package robot.commands;

import robot.GridRegistry;

public final class CheckoutGridCommand implements Command {
    private final GridRegistry grids;

    /** Connects this command to the session's grids. */
    public CheckoutGridCommand(GridRegistry grids) {
        this.grids = grids;
    }

    /** Returns the command's accepted syntax. */
    @Override
    public String usage() {
        return "grid checkout <grid-id>";
    }

    /** Selects an existing grid without changing its contents. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Arguments.count(args, 1, usage());
        grids.checkout(args[0]);
        return CommandResult.success("Checked out " + args[0] + ".");
    }
}
