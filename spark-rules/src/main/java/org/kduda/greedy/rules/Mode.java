package org.kduda.greedy.rules;

/**
 * How an uploaded table is read. The codes are the legacy form values and appear in golden file names.
 */
public enum Mode {
	/** One decision table whose last column is the decision. */
	DECISION_TABLE("dt"),
	/** An information system: one decision table per column, with that column as the decision. */
	INFORMATION_SYSTEM("is");

	private final String code;

	Mode(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	/**
	 * @throws IllegalArgumentException if no mode has this code
	 */
	public static Mode fromCode(String code) {
		for (Mode mode : values()) {
			if (mode.code.equals(code)) {
				return mode;
			}
		}
		throw new IllegalArgumentException("unknown mode code: " + code + " (expected dt or is)");
	}
}
