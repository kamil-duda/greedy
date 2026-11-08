package org.kduda.greedy.algorithm.parser

import scala.collection.mutable.ArrayBuffer

object ToStringParser {

  /**
    * Creates String in a .rul RSES format ready to be saved in a file or DB.
    *
    * @param dtsRules Decision rules to be parsed.
    * @return String containing rules in .rul RSES format.
    */
  def buildStringRSES(dtsRules: Map[String, List[List[(String, String)]]]): String = {
    val builder = StringBuilder.newBuilder

    for (dtRules <- dtsRules.values) {
      for (rule <- dtRules) {
        val support = rule.head._2
        val decision = rule.tail.head
        val conditions = rule.tail.tail

        val formatted = conditions.map {
          case (attr, value) =>
            s"($attr=$value)"
        }

        builder.append(formatted.mkString("&"))

        builder.append("=>")
        builder.append(s"(${decision._1}=${decision._2}[$support])")
        builder.append(s" $support\n")
      }
    }
    builder.mkString
  }

  /**
    * Creates String in .csv format ready to be saved in a file or DB.
    *
    * @param dtsRules Decision rules to be parsed.
    * @return String containing rules .csv format.
    */
  def buildStringCSV(dtsRules: Map[String, List[List[(String, String)]]]): String = {
    val builder = StringBuilder.newBuilder

    var counter = 0
    for (dtRules <- dtsRules.values) {
      for (rule <- dtRules) {
        val decision = rule.tail.head._2
        val conditions = rule.tail.tail

        val formatted = conditions.map {
          case (attr, value) =>
            s"($attr = $value)"
        }

        builder.append(s"$counter,")
        builder.append(formatted.mkString(""))

        builder.append(" -> ")
        builder.append(s"$decision,")
        builder.append(s"${conditions.length}\n")

        counter += 1
      }
    }
    builder.mkString
  }

  /**
    * Creates String in .csv with research metadata ready to be saved in a file or DB.
    *
    * @param dtsRules Decision rules to be parsed.
    * @return String containing rules .csv string.
    */
  def buildStringCSVWithMeta(dtsRules: Map[String, List[List[(String, String)]]]): String = {
    val builder = StringBuilder.newBuilder
    builder.append("index,rule,coverage,length\n")

    val coverages = ArrayBuffer.empty[Int]
    val lengths = ArrayBuffer.empty[Int]

    var counter = 0
    for (dtRules <- dtsRules.values) {
      for (rule <- dtRules) {
        val coverage = rule.head._2
        val decision = rule.tail.head
        val conditions = rule.tail.tail
        val length = conditions.length

        coverages += coverage.toInt
        lengths += length

        val formatted = conditions.map {
          case (attr, value) =>
            s"($attr = $value)"
        }

        builder.append(s"$counter")
        builder.append(',')
        builder.append(formatted.mkString(" & "))

        builder.append(" -> ")
        builder.append(s"(${decision._1} = ${decision._2})")
        builder.append(',')
        builder.append(s"$coverage,$length\n")

        counter += 1
      }
    }

    val minCoverage = coverages.min
    val avgCoverage = coverages.sum / coverages.length.toFloat
    val maxCoverage = coverages.max
    builder.append("coverage\n")
    builder.append("min,avg,max\n")
    builder.append(s"$minCoverage,$avgCoverage,$maxCoverage\n")

    val minLength = lengths.min
    val avgLength = lengths.sum / lengths.length.toFloat
    val maxLength = lengths.max
    builder.append("length\n")
    builder.append("min,avg,max\n")
    builder.append(s"$minLength,$avgLength,$maxLength\n")

    builder.mkString
  }

}
