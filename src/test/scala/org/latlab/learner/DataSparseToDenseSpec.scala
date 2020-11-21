package org.latlab.learner

import java.util.zip.GZIPInputStream

import tm.test.BaseSpec
import tm.util.Reader
import org.latlab.util.DataSet
import org.latlab.util.DataSet.DataCase
import tm.util.Reader.ARFFToData

class DataSparseToDenseSpec extends BaseSpec {

  trait TestData {
    val data: tm.util.Data
    lazy val sparseData = data.toTupleSparseDataSet()
    lazy val wholeData = sparseData.getWholeDenseData
  }

  trait SmallTestData extends TestData {
    val data = Reader.readARFF_native(
      getClass.getResourceAsStream("/sparse-catdata.arff")).toData()
  }

  trait Papers500 extends TestData  {
    val data = Reader.readARFF_native(new GZIPInputStream(
      getClass.getResourceAsStream("/papers-500.data.arff.gz"))).toData()
  }

  describe("Papers 500 sample data") {
    it("should be read properly") {
      new Papers500 {
        data.size should equal (7718)
        sparseData.getNumOfDatacase should equal (7692)    // the value is obtained by run time
        wholeData.getTotalWeight should equal (7692)    //  same as the sparse data set
      }
    }

    it("should allow conversion from sparse data to data set") {
      new Papers500 {
        val converted = DataOps.convertToDataSet(sparseData)

        checkDataSets(converted, wholeData)
      }
    }

  }

  describe("Small data set") {
    it("should be read properly") {
      new SmallTestData {
        data.size should equal (10)
        sparseData.getNumOfDatacase should equal (7)    // the data cases with all zeros are discarded
        wholeData.getTotalWeight should equal (7)
        wholeData.getData.size should equal (3)
      }
    }

    it("should allow conversion from sparse data to data weights") {
      new SmallTestData {
        val converted = DataOps.convertToDataWeights(sparseData, null).toArrayList()
        converted.size should equal (3)

        checkDataCase(converted.get(0), Array(0,1,0,0,0), 1)
        checkDataCase(converted.get(1), Array(1,0,0,0,1), 2)
        checkDataCase(converted.get(2), Array(1,1,1,0,1), 4)
      }
    }

    it("should allow conversion from sparse data to data set") {
      new SmallTestData {
        checkDataSets(DataOps.convertToDataSet(sparseData), wholeData)
      }
    }
  }

  def checkDataSets(subject: DataSet, target: DataSet) = {
    subject.getVariables should contain theSameElementsInOrderAs target.getVariables
    subject.getTotalWeight should equal (target.getTotalWeight)

    for (i <- 0 until target.getData.size()) {
      val c1 = subject.getData.get(i)
      val c2 = target.getData.get(i)

      c1.getStates should contain theSameElementsInOrderAs c2.getStates
      c1.getWeight should equal (c2.getWeight)
    }
  }

  def checkDataCase(target: DataCase, states: Array[Int], weight: Double) = {
    target.getStates should contain theSameElementsInOrderAs states
    target.getWeight should equal (weight)
  }
}
