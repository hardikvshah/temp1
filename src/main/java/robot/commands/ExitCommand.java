package robot.commands;

public final class ExitCommand implements Command {
    private final Runnable stop;

    /** Receives the session stop action. */
    public ExitCommand(Runnable stop) {
        this.stop = stop;
    }

    /** Returns the exit command syntax. */
    @Override
    public String usage() {
        return "exit";
    }

    /** Stops the session after validating that no arguments were supplied. */
    @Override
    public CommandResult executeValidated(String[] args) {
        Arguments.count(args, 0, usage());
        stop.run();
        return CommandResult.success("Goodbye.");
    }
}
