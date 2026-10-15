package robot.commands;

/** A command outcome: status code 0 means success and 1 means error. */
public record CommandResult(int statusCode, String message) {
    /** Creates a successful outcome with its display message. */
    public static CommandResult success(String message) {
        return new CommandResult(0, message);
    }

    /** Creates an error outcome with its display message. */
    public static CommandResult error(String message) {
        return new CommandResult(1, message);
    }
}
