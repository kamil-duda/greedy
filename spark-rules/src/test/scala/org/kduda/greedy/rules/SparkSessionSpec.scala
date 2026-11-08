package org.kduda.greedy.rules

import org.kduda.greedy.rules.testing.TestSpark
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SparkSessionSpec extends AnyFlatSpec with Matchers {

  "TestSpark.session" should "run a query" in {
    TestSpark.session.range(10).count() shouldBe 10L
  }

  it should "be an in-process classic session when greedy.spark.session is classic" in {
    assume(TestSpark.mode == "classic")
    TestSpark.session.getClass.getName shouldBe "org.apache.spark.sql.classic.SparkSession"
  }
}
