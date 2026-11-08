package org.kduda.greedy.rules

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModeAndHeuristicSpec extends AnyFlatSpec with Matchers {

  "Mode" should "use the legacy form codes, in declaration order" in {
    Mode.values.toSeq.map(_.code) shouldBe Seq("dt", "is")
  }

  it should "find every value by its code" in {
    Mode.values.foreach(mode => Mode.fromCode(mode.code) shouldBe mode)
  }

  it should "reject an unknown code, naming it" in {
    val thrown = the[IllegalArgumentException] thrownBy Mode.fromCode("xyz")
    thrown.getMessage should include("xyz")
  }

  "Heuristic" should "use the legacy form codes, in declaration order" in {
    Heuristic.values.toSeq.map(_.code) shouldBe Seq("m", "rm", "maxCov", "poly", "log")
  }

  it should "find every value by its code" in {
    Heuristic.values.foreach(heuristic => Heuristic.fromCode(heuristic.code) shouldBe heuristic)
  }

  it should "reject an unknown code, naming it" in {
    val thrown = the[IllegalArgumentException] thrownBy Heuristic.fromCode("xyz")
    thrown.getMessage should include("xyz")
  }
}
