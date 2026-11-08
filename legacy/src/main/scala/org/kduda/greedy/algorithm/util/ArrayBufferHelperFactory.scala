package org.kduda.greedy.algorithm.util

import scala.collection.mutable.ArrayBuffer

object ArrayBufferHelperFactory {
  def of[T](array: Array[(String, String)]): ArrayBuffer[(String, String)] = {
    ArrayBuffer(array: _*)
  }
}
