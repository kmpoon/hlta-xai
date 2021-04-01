package xai.hlta

import collection.JavaConversions._
import org.latlab.util.Variable
import xai.util.ArffWriter
import java.text.DecimalFormat
import xai.util.Data
import org.latlab.model.LTM
import java.nio.file.Files
import java.nio.file.Paths
import java.io.PrintWriter
import scala.io.Source
import org.json4s._
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.ArrayList
import org.latlab.graph.DirectedNode
import org.latlab.reasoner.CliqueTreePropagation
import org.latlab.model.BeliefNode
import org.apache.commons.text.StringEscapeUtils

import org.rogach.scallop._
import xai.util.Arguments
import xai.util.Reader
import xai.hlta.HLTA._

object Doc2VecAssignment {
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: Doc2VecAssignment [OPTION]... model data output_name
             |E.g. Doc2VecAssignment model.bif data.arff output
             |The output file will be """+getFileName("output", "js")+""" and """+getFileName("output", "arff"))
             
    val model = trailArg[String](descr = "Model file (e.g. model.bif)")
    val data = trailArg[String](descr = "Data file, .hlcm file is not allowed")
    val outputName = trailArg[String](descr = "Name of the output file")
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val decimalPlaces = opt[Int](descr="Significant figure, only used for intermediate data storage (.topics.arff)", default = Some(2))
    val layer = opt[List[Int]](descr = "Layer number, i.e. 2 3 4", default = None)
    val keywords = opt[Int](descr = "Number of keywords to describe each topic, only used for broad option is off", default = Some(7))
    val confidence = opt[Double](descr = "Only document with P(topic|document)>c will be listed in the list, default 0.5", default = Some(0.5))
    val broad = opt[Boolean](descr = "Use broad topic definition, speed up the process but more document will be categorized into the topic")

    verify
    checkDefaultOpts()
  }
  
  val logger = LoggerFactory.getLogger(Doc2VecAssignment.getClass)

  def main(args: Array[String]) {
     val conf = new Conf(args)

    if(conf.data().endsWith(".hlcm"))
      throw new Exception("Invalid data format")
     
     run(conf.model(), conf.data(), conf.ldaVocab.getOrElse(""), conf.outputName(), conf.decimalPlaces(), conf.layer.toOption, conf.confidence(), conf.keywords())
  }

  def run(modelFile: String, dataFile: String, ldaVocabFile: String, outputName: String, decimalPlaces: Int, layer: Option[List[Int]], threshold : Double, keywords: Int): Unit = {
    val topicDataFile = getFileName(outputName, "arff")
    val precomputedTopicData = if (Files.exists(Paths.get(topicDataFile))) {
      logger.info("Topic data file ({}) exists.  Check if variable matches.", topicDataFile)
      val (model, topicData) = Reader.readModelAndData(modelFile, topicDataFile)
      model.synchronize(topicData.variables.toArray)
      val topicNeeded = if(layer.isDefined){
          val variableNameLevels = model.getVariableNameLevels
          topicData.variables.filter { variable => 
            val level = variableNameLevels(variable.getName)
            layer.get.contains(level)
          }
        }else{
          model.getInternalVars.toIndexedSeq
        }
      if(topicData.variables.containsAll(topicNeeded)){
        logger.info("Variable matches.  Use topic data file instead")
        Some(topicData.project(topicNeeded))
      }else{
        logger.info("Variable missing.  Compute topic data.")
        None
      }
    }else
      None

    val topicData = if(precomputedTopicData.isDefined){
        precomputedTopicData.get
      }else{
        logger.info("reading model and data")
        val (model, data) = Reader.readModelAndData(modelFile, dataFile, ldaVocabFile = ldaVocabFile)
        val variableNames = data.variables.map(_.getName)
    
        logger.info("binarizing data")
        val binaryData = data.binary()
        //model.synchronize(binaryData.variables.toArray) //Since variable is always with cardinality of 2, no need this line anymore
    
        logger.info("Computing topic distribution")
        val topicData = computeBroadTopicData(model, binaryData, layer)

        logger.info("Saving topic data")
  
        val df = new DecimalFormat("#0." + "#" * decimalPlaces)
        topicData.saveAsArff(getFileName(outputName, "arff"), df)
        topicData
      }

    logger.info("Generating document catalog")
    val catalog = topicData.toCatalog(threshold = threshold)

    logger.info("Saving topic map")
    catalog.saveAsJs(getFileName(outputName, "js"), decimalPlaces)
    catalog.saveAsJson(getFileName(outputName, "json"), decimalPlaces)

    logger.info("Done")
  }
    
  def getFileName(output: String, ext: String) = s"${output}.topics.${ext}"
  
  /**
   * Assign broadly defined topics to the documents.
   * It computes the probabilities of the latent variables in a LTM and
   * assign those topics that have a higher probability of state 1
   * to each document.
   */
  def computeBroadTopicData(model: LTM, binaryData: Data, layer: Option[List[Int]]): Data = { 
    //get the list of variables to be computed
    val variables = if(layer.isEmpty)
      model.getInternalVars.toSeq
    else{
      val variablesByLevel = model.getLevelVariables
      //variablesByLevel: {0->word, 1->topic, ... , topic_height-1 ->root}
      //but layer could be negative or 0, where root=0, root's child=-1, etc.
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      _layer.map(variablesByLevel.get(_)).flatten.flatten.toSeq
    }

    // find the probabilities of state 1 for each variable
    val topicProbabilities =
      HLTA.computeProbabilities(model, binaryData, variables).map(p => Data.DenseInstance(p._1.toArray.map(_(1)), p._2, p._3))

    new Data(variables.toIndexedSeq, topicProbabilities.toIndexedSeq)
  }
  

  implicit final class toCatalog(data: Data){
    /**
     * Generates a list of documents for each topic.
     *
     * Each map value is a sequence of pairs where first element indicates
     * the probability and second element the document index.
     * The sequence is sorted in descending order of probability.
   	 */
    def toCatalog(threshold: Double): DocumentCatalog = {
      val map = (0 until data.variables.size).map { v =>
        val documents = data.instances.view.zipWithIndex.map{case (d, i) =>
          // map to value of v
          if(d.name.isEmpty)
            (i.toString(), d.values(v))//use index as document name
          else
            (d.name, d.values(v))//use the provided instance name
          } 
          .filter(_._2 >= threshold).force // keep only those values >= threshold
          .sortBy(-_._2) // sort by descending values
        (data.variables(v), documents)
      }.toMap
      DocumentCatalog(map)
    }
  }
  
}



/**
 * A sparse matrix form of P(z|d)
 * 
 * Each row is one topic, each column is one document
 */
object DocumentCatalog{
  case class Entry(topic: String, doc: List[List[Any]])
   def readJson(fileName: String) = {
    import org.json4s.native.JsonMethods._
    implicit val formats = DefaultFormats
    val jsonString = Source.fromFile(fileName).mkString
    val entries = parse(jsonString).extract[List[Entry]]
    val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
    val map = entries.map { e => 
      (new Variable(e.topic, b) -> e.doc.map { x => (x.get(0).asInstanceOf[String], x.get(1).asInstanceOf[Double]) })
    }.toMap
    DocumentCatalog(map)
  }
}

case class DocumentCatalog(map: Map[Variable, Seq[(String, Double)]]){
  
  def apply(variable: String): Seq[(String, Double)] = map.find(v=>v._1.getName.equals(variable)).get._2
  
  def apply(variable: Variable): Seq[(String, Double)] = map.get(variable).get
  
  def apply(variable: String, docName: String): Double = apply(variable).find{case (d, p) => d.equals(docName)}.get._2
  
  def apply(variable: Variable, docName: String): Double = apply(variable).find{case (d, p) => d.equals(docName)}.get._2
  
  def saveAsJs(outputFile: String, decimalPlaces: Int = 2, jsVarName: String = "topicMap"){
    
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeEcmaScript(str)
    }
    
    val writer = new PrintWriter(outputFile)

    writer.println("var "+jsVarName+" = {")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"""["${p._1.escape}%s", ${p._2}%.2f]""").mkString(", ")
      s"  ${variable.getName.escape}: [${documents}]"
    }.mkString(",\n"))

    writer.println("};")

    writer.close
  }

  def saveAsJson(outputFile: String, decimalPlaces: Int = 2){
    val writer = new PrintWriter(outputFile)

    writer.println("[")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"""["${p._1}%s", ${p._2}%.2f]""").mkString(",")
      "{\"topic\":\"" + variable.getName + "\",\"doc\":[" + documents + "]}"
    }.mkString(",\n"))

    writer.println("]")

    writer.close
  }
}
