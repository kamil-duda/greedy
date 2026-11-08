package org.kduda.greedy.spark.reader.mongo

import org.apache.spark.sql.DataFrame

trait SparkMongoService {

  def readCsvByName(name: String): DataFrame

  def readCsvById(id: String): DataFrame
}
