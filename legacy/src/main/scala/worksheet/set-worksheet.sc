import scala.collection.mutable

val a = mutable.HashSet.empty[(Int, String)]

a += Tuple2(0, "a")
a += Tuple2(0, "a")
a += Tuple2(0, "b")
a += Tuple2(1, "a")
