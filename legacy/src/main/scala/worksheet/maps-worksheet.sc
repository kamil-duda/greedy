def f(a: Map[String, Int]): Map[String, Int] = {
  a.map {
    case (k, v) =>
      if (v == 10)
        (k, v * 2)
      else
        (k, v)
  }
}

f(Map("a" -> 10, "b" -> 2))

val b = Map("a" -> 10, "b" -> 2)
b.size