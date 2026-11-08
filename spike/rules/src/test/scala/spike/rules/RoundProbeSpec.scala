package spike.rules

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{AfterAll, Test, TestInstance}

/**
 * The same code and assertions on a classic local session (`testClassic`) and over Spark Connect (`testConnect`);
 * the system property `spike.session` picks the session.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundProbeSpec {

  private val spark: SparkSession = sys.props.getOrElse("spike.session", "classic") match {
    case "classic" => SparkSession.builder().master("local[2]").appName("round-probe")
      .config("spark.checkpoint.dir", java.nio.file.Files.createTempDirectory("round-probe-checkpoints").toString).getOrCreate()
    case "connect" => SparkSession.builder().remote(sys.props.getOrElse("spike.connect", "sc://localhost:15002")).getOrCreate()
  }

  // a: tie at 5.0 between col_index 0 and 1; b: tie at 4.0 between col_index 1 and 2
  private def table: DataFrame = {
    import spark.implicits._
    Seq(("a", 0, 5.0), ("a", 1, 5.0), ("b", 0, 1.0), ("b", 1, 4.0), ("b", 2, 4.0), ("b", 3, 0.5))
      .toDF("group", "col_index", "score")
  }

  @Test
  def bestPerGroupBreaksTiesByColumnIndex(): Unit = {
    val best = RoundProbe.bestPerGroup(table).orderBy("group").collect().toSeq
    assertEquals(Seq(Row("a", 0, 5.0), Row("b", 1, 4.0)), best.map(r => Row(r.getString(0), r.getInt(1), r.getDouble(2))))
  }

  @Test
  def countedCachesLocalCheckpointsAndCounts(): Unit = {
    val counts = RoundProbe.counted(table).orderBy("group").collect().toSeq
    assertEquals(Seq(Row("a", 2L), Row("b", 4L)), counts.map(r => Row(r.getString(0), r.getLong(1))))
  }

  /** Records whether a reliable checkpoint works on this side; a finding for the notes, not a requirement. */
  @Test
  def reliableCheckpointOutcomeIsRecorded(): Unit = {
    val session = sys.props.getOrElse("spike.session", "classic")
    val outcome = scala.util.Try(RoundProbe.reliablyCheckpointed(table).count()) match {
      case scala.util.Success(rows) => s"ok, $rows rows"
      case scala.util.Failure(e) => s"${e.getClass.getName}: ${Option(e.getMessage).getOrElse("").linesIterator.take(2).mkString(" ")}"
    }
    val out = java.nio.file.Paths.get(sys.props("spike.results"), s"checkpoint-$session.txt")
    java.nio.file.Files.writeString(out, outcome + "\n")
  }

  @AfterAll
  def stop(): Unit = spark.stop()
}
