package org.kduda.greedy.spark.reader.csv

import java.io.File

import org.apache.spark.sql.DataFrame
import org.kduda.greedy.spark.generic.SparkAware
import org.springframework.stereotype.Service

/**
  * Reader for loading csv files into spark.
  */
@Service
class CsvFileSparkCsvReader extends SparkCsvReader with SparkAware {

  override def read(file: File, options: java.util.Map[String, String]): DataFrame = {
    if (options == null)
      sql.read.csv(file.getAbsolutePath)
    else
      sql.read.options(options).csv(file.getAbsolutePath)
  }
}
