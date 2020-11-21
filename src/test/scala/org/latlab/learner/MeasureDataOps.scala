package org.latlab.learner

import java.util.zip.GZIPInputStream

import tm.util.Reader
import tm.util.Reader.ARFFToData
import tm.util.Timer.time

object MeasureDataOps {

  def main(args: Array[String]): Unit = {
    val filename = args(0)
    val sparseData = time("Reading data")(Reader.readData(filename).toTupleSparseDataSet())

    for (i <- 0 until 5) {
      updated(sparseData)
      original(sparseData)
    }
  }

  def original(sparseData: SparseDataSet) = {
    val wholeData = time("Converting to whole dense data")(sparseData.getWholeDenseData())
    println(wholeData.getTotalWeight)
  }

  def updated(sparseData: SparseDataSet) = {
    val converted = time("Converting with DataOps")(DataOps.convertToDataSet(sparseData))
    println(converted.getTotalWeight)
  }
}
