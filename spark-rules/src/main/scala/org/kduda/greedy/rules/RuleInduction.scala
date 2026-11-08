package org.kduda.greedy.rules

import org.apache.spark.sql.DataFrame

/** Induces decision rules from a table with a greedy heuristic. */
object RuleInduction {

  /**
   * @param data
   *   one string column per attribute, in upload order, named as uploaded
   * @param mode
   *   how `data` is read: one decision table, or an information system with one decision table per column
   * @param heuristic
   *   the score that picks each rule's next condition
   */
  def induce(data: DataFrame, mode: Mode, heuristic: Heuristic): DataFrame =
    throw new NotImplementedError("RuleInduction.induce is implemented in Phase 3")
}
