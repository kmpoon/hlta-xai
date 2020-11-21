package org.latlab.learner


import java.util
import java.util.Collections

import org.latlab.util.DataSet.DataCase
import org.latlab.util.{DataSet, Variable}

import scala.collection.JavaConverters._

import scala.collection.Searching._
import scala.collection.mutable.ArrayBuffer

object DataOps {
  type States = Array[Int]

  def addToList(cases: util.ArrayList[DataCase], newCase: DataCase) = {
//    println(s"add single case to list with ${cases.size()}")

    val index = Collections.binarySearch(cases, newCase)
    val inserted = if (index < 0) {
      cases.add(-index - 1, newCase)
      true
    } else {
      val c = cases.get(index)
      c.setWeight(c.getWeight + newCase.getWeight)
      false
    }

    inserted
  }

  def mergeToFirst(c1: DataCase, c2: DataCase): DataCase = {
    c1.setWeight(c1.getWeight + c2.getWeight)
    c1
  }

  /**
   * It merges two lists.  It assumes that the list of data cases in
   * the two lists are sorted with no duplicates.
   */
  def mergeLists(x: util.ArrayList[DataCase], y: util.ArrayList[DataCase]): util.ArrayList[DataCase] = {
//    println(s"merge with ${x.size()} and ${y.size()}")

    val result = new util.ArrayList[DataCase](x.size() + y.size())

    var i = 0
    var j = 0
    while (i < x.size() && j < y.size()) {
      val c1 = x.get(i)
      val c2 = y.get(j)

      val cmp = c1.compareTo(c2)
      if (cmp < 0) {
        result.add(c1)
        i += 1
      } else if (cmp > 0) {
        result.add(c2)
        j += 1
      } else {
        result.add(mergeToFirst(c1, c2))
        i += 1
        j += 1
      }
    }

    def addRemaining(source: util.ArrayList[DataCase], start: Int) = {
      for (k <- start until source.size()) {
        result.add(source.get(k))
      }
    }

    addRemaining(x, i)
    addRemaining(y, j)

    result
  }

  sealed trait DataCases {
    def missing(): Boolean

    def totalWeights(): Double

    def get(i: Int): DataCase

    def size(): Int

    def toArrayList(): util.ArrayList[DataCase]

    def buildDataSet(variables: Array[Variable]): DataSet = {
      val result = new DataSet(variables)
      result.setDataCases(toArrayList(), missing(), totalWeights())
      result
    }

    def add(other: DataCases): Multiple
  }

  object DataCases {
    def merge(x: DataCases, y: DataCases): Multiple = {
      // the checking makes sure that an Singleton object will not
      // need to add another Multiple object
      if (x.size() == 1)
        y.add(x)
      else
        x.add(y)
    }
  }

  case class Singleton(statesOp: () => States) extends DataCases {
    private lazy val states = statesOp()

    override def missing: Boolean = DataSetBuilder.checkMissing(states)

    override def totalWeights: Double = 1

    override def get(i: Int) =
      if (i == 0) DataCase.construct(states, 1)
      else throw new IllegalArgumentException

    override def toArrayList(): util.ArrayList[DataCase] = {
      val result = new util.ArrayList[DataCase](1)
      result.add(get(0))
      result
    }

    override def size() = 1

    def add(other: DataCases): Multiple = {
      if (other.size() != 1)
        throw new IllegalArgumentException

      val c1 = get(0)
      val c2 = other.get(0)

      val cases = new util.ArrayList[DataCase]()
      val cmp = c1.compareTo(c2)

      if (cmp < 0) {
        cases.add(c1)
        cases.add(c2)
      } else if (cmp > 0) {
        cases.add(c2)
        cases.add(c1)
      } else
        cases.add(mergeToFirst(c1, c2))

      Multiple(cases, missing || other.missing(), totalWeights + other.totalWeights())
    }

  }

  case class Multiple(private var cases: util.ArrayList[DataCase] = new util.ArrayList[DataCase](),
                      private var _missing: Boolean = false,
                      private var _totalWeights: Double = 0.0) extends DataCases {

    override def missing(): Boolean = _missing

    override def totalWeights(): Double = _totalWeights

    override def toArrayList(): util.ArrayList[DataCase] = cases

    override def get(i: Int) = cases.get(i)

    override def size() = cases.size()

    def add(newCase: DataCase): Multiple = {

      val inserted = addToList(cases, newCase)
      if (inserted) _missing ||= DataSetBuilder.checkMissing(newCase.getStates)
      _totalWeights += newCase.getWeight
      this
    }

    def add(states: States, additionalWeight: Double): Multiple = {
      add(DataCase.construct(states, additionalWeight))
    }

    override def add(other: DataCases): Multiple = {
      if (other.size() == 1) {
        add(other.get(0))
      } else {
        cases = mergeLists(cases, other.toArrayList())
        _missing ||= other.missing()
        _totalWeights += other.totalWeights()
        this
      }
    }
  }


  // type DataWeights = mutable.TreeMap[States, DataCase]

  trait ArrayOrdering[T] extends Ordering[Array[T]] {
    def ord: Ordering[T]

    override def compare(x: Array[T], y: Array[T]): Int = {
      val length = Math.min(x.length, y.length)
      for (i <- (0 until length)) {
        val cmp = ord.compare(x(i), y(i))
        if (cmp != 0) return cmp
      }

      x.length compare y.length
    }
  }

  implicit object IntArrayOrdering extends ArrayOrdering[Int] {
    override val ord = Ordering[Int]
  }

  implicit object DataCaseOrdering extends Ordering[DataCase] {
    override def compare(x: DataCase, y: DataCase): Int =
      IntArrayOrdering.compare(x.getStates, y.getStates)
  }


  class DataWeights(val target: DataSet) {
    //    private val cases = new mutable.TreeMap[States, DataCase]()
    //    private val cases = new util.ArrayList[DataCase]()
    private val cases = new ArrayBuffer[DataCase]()

    def updateData(states: States, additionalWeight: Double) = {
      val newCase = DataCase.construct(states, additionalWeight)

      cases.search(newCase) match {
        case Found(i) => {
          val c = cases(i)
          c.setWeight(c.getWeight + additionalWeight)
        }
        case InsertionPoint(i) => cases.insert(i, newCase)
      }

      //      val index = Collections.binarySearch(cases, newCase)
      //      if (index < 0) {
      //        cases.add(-index - 1, newCase)
      //      } else {
      //        val c = cases.get(index)
      //        c.setWeight(c.getWeight + additionalWeight)
      //      }

      //      val c = cases.getOrElseUpdate(states, DataCase.construct(target, states, 0))
      //      c.setWeight(c.getWeight + additionalWeight)
    }


    def toArrayList(): util.ArrayList[DataCase] = {
      //      val l = new util.ArrayList[DataCase](cases.size)
      //      cases.values.foreach(l.add)
      //      l

      //      cases
      new util.ArrayList(cases.asJavaCollection)
    }

    def anyMissing() = cases.exists(p => p.getStates.exists(_ == DataSet.MISSING_VALUE))

    def getTotalWeights = cases.foldLeft(0.0)(_ + _.getWeight)
  }

  object DataSetBuilder {
    def convertFrom(states: States, weight: Double) = {
      val cases = new util.ArrayList[DataCase]()
      cases.add(DataCase.construct(states, weight))
      new DataSetBuilder(Multiple(cases, checkMissing(states), weight))
    }

    def checkMissing(states: States) = states.exists(_ == DataSet.MISSING_VALUE)
  }

  class DataSetBuilder(private val cases: Multiple = Multiple()) {

    def add(newCase: DataCase) = {
      cases.add(newCase)
      this
    }

    def add(states: States, additionalWeight: Double): DataSetBuilder = {
      add(DataCase.construct(states, additionalWeight))
    }

    def add(other: DataSetBuilder): DataSetBuilder = {
      // optimise for a very common case
      if (other.cases.size() <= 1)
        return add(other.cases.get(0))


      println(s"Merging with ${cases.size()} and ${other.cases.size()}")
      cases.add(other.cases)
      this
    }



    def build(variables: Array[Variable]) = cases.buildDataSet(variables)

    def addTo(sparseData: SparseDataSet): DataSetBuilder = {
      val size = sparseData.getNumOfDatacase
      val order = Range(0, size).toIndexedSeq
      addTo(sparseData, size, 0, order, getInternalToExternalIDMapping(sparseData))
    }

    def addTo(sparseData: SparseDataSet,
              batchSize: Int, start: Int,
              order: IndexedSeq[Int], intToExtID: Map[Integer, Int]): DataSetBuilder = {
      val length = sparseData._VariablesSet.size

      def getStates(i: Int) = {
        val states = Array.fill(length)(0)
        val row = sparseData.SDataSet.userMatrix.get(order(i))

        // Filling in the positive entries
        val iter = row.iterator
        while (iter.hasNext) {
          val internal_ID = iter.nextInt // the id of the item
          states(intToExtID(internal_ID)) = 1
        }

//        println("Created: " + states.mkString(","))

        states
      }


      //      for (i <- start until start + batchSize) {
      //        val states = time("getting states")(getStates(i))
      //        time("adding states")(addToStates(states, 1))
      //      }

      val cases = (start until start + batchSize)
        .map(i => Singleton(() => getStates(i)).asInstanceOf[DataCases])
        .par
        .reduce(DataCases.merge).asInstanceOf[Multiple]

      new DataSetBuilder(cases)
    }

  }


  //  case class Instance(states: Array[Int], weight: Double)
  //
  //  trait ArrayOrdering[T] extends Ordering[Array[T]] {
  //    val ord: Ordering[T]
  //
  //    override def compare(x: Array[T], y: Array[T]): Int = {
  //      val length = Math.min(x.length, y.length)
  //      for (i <- (0 until length)) {
  //        val cmp = ord.compare(x(i), y(i))
  //        if (cmp != 0) return cmp
  //      }
  //
  //      return x.length compare y.length
  //    }
  //  }
  //
  //  object InstanceOrdering extends Ordering[Instance] {
  //
  //    object StateOrder extends ArrayOrdering[Int] {
  //      val ord = Ordering[Int]
  //    }
  //
  //    override def compare(x: Instance, y: Instance): Int =
  //      StateOrder.compare(x.states, y.states)
  //  }

  /**
   * Maps the internal ID to external ID based on the implementation in SparseDataSet
   */
  def getInternalToExternalIDMapping(sparseDataSet: SparseDataSet): Map[Integer, Int] = {
    def internalToExternal(internal: Integer): Int =
      sparseDataSet._mapNameToIndex.get(sparseDataSet._item_mapping.toOriginalID(internal))

    sparseDataSet.SDataSet.allItems.asScala.map(id => id -> internalToExternal(id)).toMap
  }

  def convertToDataWeights(sparseData: SparseDataSet, dataSet: DataSet): DataWeights = {
    val size = sparseData.getNumOfDatacase
    val order = Range(0, size).toIndexedSeq
    convertToDataWeights(sparseData, dataSet, size, 0, order,
      getInternalToExternalIDMapping(sparseData))
  }

  //  private def updateData(data: DataWeights,
  //                         dataSet: DataSet, states: States, additionalWeight: Double) = {
  ////    data.put(states, data.getOrElse(states, 0d) + 1)
  ////    data
  ////    data.update(states, data.getOrElse(states, 0d) + 1)
  //
  //    val datacase = data.getOrElseUpdate(states, DataCase.construct(dataSet, states, 0))
  //    datacase.setWeight(datacase.getWeight + additionalWeight)
  //  }

  def convertToDataWeights(sparseData: SparseDataSet, dataSet: DataSet,
                           batchSize: Int, start: Int,
                           order: IndexedSeq[Int], intToExtID: Map[Integer, Int]): DataWeights = {
    val length = sparseData._VariablesSet.size

    val weights = new DataWeights(dataSet)

    for (i <- start until start + batchSize) {
      val states = Array.fill(length)(0)

      val row = sparseData.SDataSet.userMatrix.get(order(i))

      // Filling in the positive entries
      val iter = row.iterator
      while (iter.hasNext) {
        val internal_ID = iter.nextInt // the id of the item
        states(intToExtID(internal_ID)) = 1
      }

      weights.updateData(states, 1)
    }

    weights
  }

  def convertToDataSet(sparseData: SparseDataSet): DataSet = {
    new DataSetBuilder().addTo(sparseData).build(sparseData._VariablesSet)
  }
}
