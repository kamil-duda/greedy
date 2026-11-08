package org.kduda.greedy.spark.reader.mongo

import java.io.{File, IOException}
import java.util

import com.mongodb.gridfs.GridFSDBFile
import org.apache.spark.sql.{Dataset, Row, DataFrame}
import org.kduda.greedy.service.storage.FileStorageService
import org.kduda.greedy.spark.reader.csv.SparkCsvReader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SparkMongo extends SparkMongoService {
  private val log = LoggerFactory.getLogger(classOf[SparkMongo])

  @Autowired private val csvReader: SparkCsvReader = null
  @Autowired private val storage: FileStorageService = null

  @Override
  def readCsvByName(name: String): DataFrame = {
    val gridFSDBFile = storage.findFileByName(name).get

    readAsDataset(gridFSDBFile)
  }

  @Override
  def readCsvById(id: String): DataFrame = {
    val gridFSDBFile = storage.findFileById(id).get

    readAsDataset(gridFSDBFile)
  }

  private def readAsDataset(gridFSDBFile: GridFSDBFile): DataFrame = {
    val storageFile = new File("tmp-storage/" + gridFSDBFile.getFilename)

    saveToTempStorage(gridFSDBFile, storageFile)

    val data = sparkRead(storageFile)

    data.cache().show()
    deleteFromTempStorage(storageFile)

    data
  }

  private def saveToTempStorage(gridFSDBFile: GridFSDBFile, storageFile: File): Unit = {
    var writtenBytes = 0L
    try
      writtenBytes = gridFSDBFile.writeTo(storageFile)
    catch {
      case e: IOException =>
        log.error("Could not write to file:" + storageFile.getAbsolutePath, e)
    }
    log.info("Written " + writtenBytes + " bytes into " + storageFile.getAbsolutePath)
  }

  private def sparkRead(file: File): DataFrame = {
    val options = new util.HashMap[String, String]()
    options.put("header", "true")
    csvReader.read(file, options)
  }

  private def deleteFromTempStorage(file: File): Boolean = {
    val isDeleted = file.delete
    log.info(file.getAbsolutePath + " deleted: " + isDeleted)
    isDeleted
  }
}
