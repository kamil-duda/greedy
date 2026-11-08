package org.kduda.greedy.rules;

/**
 * The greedy heuristic that picks the next condition of a rule. The codes are the legacy form values and appear in
 * golden file names.
 */
public enum Heuristic {
	M("m"),
	RM("rm"),
	MAX_COV("maxCov"),
	POLY("poly"),
	LOG("log");

	private final String code;

	Heuristic(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	/**
	 * @throws IllegalArgumentException if no heuristic has this code
	 */
	public static Heuristic fromCode(String code) {
		for (Heuristic heuristic : values()) {
			if (heuristic.code.equals(code)) {
				return heuristic;
			}
		}
		throw new IllegalArgumentException("unknown heuristic code: " + code + " (expected m, rm, maxCov, poly or log)");
	}
}
