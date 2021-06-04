package prev.common.report;

import prev.Compiler;

/**
 * Reporting.
 */
public class Report {

	public static enum LoggingLevel {

		DEBUG(0),
		INFO(1),
		WARNING(2),
		NONE(99);

		private final int value;
		LoggingLevel(final int value) {
            this.value = value;
		}
		public int getValue() {
			return this.value;
		}
	}

	public static final LoggingLevel DEFAULT_LOGGING_LEVEL = LoggingLevel.INFO;

	/** Counter of debug messages printed out. */
	private static int numOfDebugs = 0;

	/**
	 * Returns the number of debug messages printed out.
	 * 
	 * @return The number of debug messages printed out.
	 */
	public static int numOfDebugs() {
		return numOfDebugs;
	}

	/**
	 * Prints out a debug message.
	 * 
	 * @param message The debug message to be printed.
	 */
	public static void debug(String message) {
		numOfDebugs++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.DEBUG.getValue())
			return;
		System.out.print("?> ");
		System.out.println(message);
	}

	/**
	 * Prints out an debug message relating to the specified part of the
	 * source file.
	 * 
	 * @param location Location the debug message is related to.
	 * @param message  The debug message to be printed.
	 */
	public static void debug(Locatable location, String message) {
		numOfDebugs++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.DEBUG.getValue())
			return;
		System.out.print("?> ");
		System.out.print("[" + location.location() + "] ");
		System.out.println(message);
	}

	/** Counter of information messages printed out. */
	private static int numOfInfos = 0;

	/**
	 * Returns the number of information messages printed out.
	 * 
	 * @return The number of information messages printed out.
	 */
	public static int numOfInfos() {
		return numOfInfos;
	}

	/**
	 * Prints out an information message.
	 * 
	 * @param message The information message to be printed.
	 */
	public static void info(String message) {
		numOfInfos++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.INFO.getValue())
			return;
		System.out.print(":-) ");
		System.out.println(message);
	}

	/**
	 * Prints out an information message relating to the specified part of the
	 * source file.
	 * 
	 * @param location Location the information message is related to.
	 * @param message  The information message to be printed.
	 */
	public static void info(Locatable location, String message) {
		numOfInfos++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.INFO.getValue())
			return;
		System.out.print(":-) ");
		System.out.print("[" + location.location() + "] ");
		System.out.println(message);
	}

	/** Counter of warnings printed out. */
	private static int numOfWarnings = 0;

	/**
	 * Returns the number of warnings printed out.
	 * 
	 * @return The number of warnings printed out.
	 */
	public static int numOfWarnings() {
		return numOfWarnings;
	}

	/**
	 * Prints out a warning.
	 * 
	 * @param message The warning message.
	 */
	public static void warning(String message) {
		numOfWarnings++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.WARNING.getValue())
			return;
		System.err.print(":-o ");
		System.err.println(message);
	}

	/**
	 * Prints out a warning relating to the specified part of the source file.
	 * 
	 * @param location Location the warning message is related to.
	 * @param message  The warning message to be printed.
	 */
	public static void warning(Locatable location, String message) {
		numOfWarnings++;
		if (Compiler.loggingLevel.getValue() > LoggingLevel.WARNING.getValue())
			return;
		System.err.print(":-o ");
		System.err.print("[" + location.location() + "] ");
		System.err.println(message);
	}

	/**
	 * An error.
	 * 
	 * Thrown whenever the program reaches a situation where any further computing
	 * makes no sense any more because of the erroneous input.
	 */
	@SuppressWarnings("serial")
	public static class Error extends java.lang.Error {

		/**
		 * Constructs a new error.
		 * 
		 * @param message The error message.
		 */
		public Error(String message) {
			super(message);
			System.err.print(":-( ");
			System.err.println(message);
		}

		/**
		 * Constructs a new error relating to the specified part of the source file.
		 * 
		 * @param location Location the error message is related to.
		 * @param message  The error message.
		 */
		public Error(Locatable location, String message) {
			System.err.print(":-( ");
			System.err.print("[" + location.location() + "] ");
			System.err.println(message);
		}

	}

	/**
	 * An internal error.
	 * 
	 * Thrown whenever the program encounters internal error.
	 */
	@SuppressWarnings("serial")
	public static class InternalError extends Error {

		/**
		 * Constructs a new internal error.
		 */
		public InternalError() {
			super("Internal error.");
			this.printStackTrace();
		}

	}
}
