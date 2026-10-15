package robot.commands;

final class Arguments {
    /** Prevents construction of this utility class. */
    private Arguments() {}

    /** Rejects missing or extra arguments before any state changes. */
    static void count(String[] args, int expected, String usage) {
        if (args.length != expected) throw new IllegalArgumentException("Usage: " + usage);
    }

    /** Parses a signed decimal integer with a readable error. */
    static int integer(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
    }
}
