package xai.util

import scala.io.Source
import xai.util.Data.DenseInstance
import org.latlab.util.Variable
import java.util.ArrayList
import scala.collection.mutable.MutableList

/**
 * This is a fix of the Issue - DataSet reader does not allow non-ascii character
 */
object HlcmReader {
  
  def read(filename: String) = {
    val variables = MutableList[Variable]()    
    val instances = MutableList[DenseInstance]()
    var name: Option[String] = None

    val reader = Source.fromFile(filename)
    for(line <- reader.getLines()){
      if(!line.isBlank() && !line.isComment()){
        if(variables.size == 0 && line.trim.startsWith("Name")){
          name = Some(getName(line))
        }else if(line.isVariable()){
          val (variableName, statesName) = getVariable(line)
          variables += convert(variableName, statesName)
        }else{
          val (states, weight) = getSample(line)
          instances += new DenseInstance(states, weight)
        }
      }
    }
    reader.close()
    
    def convert(a: String, s: Seq[String]) = {
      val b = new ArrayList[String]()
      s.zipWithIndex.foreach { case (state, index) => b.add(0, state) }
      new Variable(a, b)
    }
    new Data(variables.toIndexedSeq, instances.toIndexedSeq, name = name.getOrElse("data"), isBinary = true)
  }
  
  private def getName(line: String) = {
    val split = line.lastIndexOf(":")
    val (_, name) = line.splitAt(split)
    name.trim
  }
  
  private def getVariable(line: String) = {
    val split = line.lastIndexOf(":")
    val (variableName, stateString) = line.splitAt(split)
    val stateNames = stateString.trim.split("[ ]+")
    (variableName, stateNames)
  }
  
  private def getSample(line: String) = {
    val parts = line.split(" ")
    val weight = parts.last.toDouble
    val states = parts.slice(0, parts.length-1).map(_.toDouble)//slice(a,b) => take [a,b)
    (states, weight)
  }
  
  private implicit class lineDiscriminator(line: String){
    def isVariable() = {
      line.contains(":") || line.exists { char => char.isLetter }
    }
    
    def isSample() = {
      !isVariable()
    }
    
    def isComment() = {
      line.startsWith("//")
    }
    
    def isBlank() = {
      line.trim().length() == 0
    }
  }
}