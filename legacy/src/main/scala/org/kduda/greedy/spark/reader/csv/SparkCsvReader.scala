package org.kduda.greedy.spark.reader.csv

import java.io.File
import javax.annotation.Nullable

import org.apache.spark.sql.{DataFrame, Dataset, Row}

trait SparkCsvReader {

  /**
    * Reads csv resource file with given optional options.
    *
    * @param file    Csv file.
    * @param options Additional options.
    * @return Spark Dataset of Row.
    */
  def read(file: File, @Nullable options: java.util.Map[String, String]): DataFrame
}
