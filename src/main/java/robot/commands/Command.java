package robot.commands;

public interface Command {
    /** Provides the command syntax used by help and validation errors. */
    String usage();

    /** Converts validation failures into error results at the command boundary. */
    default CommandResult execute(String[] args) {
        try {
            return executeValidated(args);
        } catch (IllegalArgumentException error) {
            return CommandResult.error("Error: " + error.getMessage());
        }
    }

    /** Validates arguments and performs the operation, returning its result. */
    CommandResult executeValidated(String[] args);
}
