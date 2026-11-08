import scala.collection.mutable.ArrayBuffer

val x = List(5, 5, 1)
val min = x.min
val max = x.max
val avr: Float = x.sum / x.length.toFloat

val ab = ArrayBuffer.empty[Int] += 5 += 5 += 1 += 0 += 100

ab.min
ab.max
ab.sum / ab.length.toFloat