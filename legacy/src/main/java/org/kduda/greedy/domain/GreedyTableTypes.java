package org.kduda.greedy.domain;

public enum GreedyTableTypes {
	NONE(""),
	DATA("data"),
	INFORMATION_SYSTEM("information-system"),
	DECISION_TABLE("decision-table"),
	RULES("association-rules");

	private final String type;

	GreedyTableTypes(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}
}
