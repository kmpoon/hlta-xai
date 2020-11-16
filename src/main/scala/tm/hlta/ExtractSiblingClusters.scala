package tm.hlta

import java.io.PrintWriter
import java.nio.file._

import tm.util.manage
import tm.util.Reader

import scala.collection.JavaConverters._

object ExtractSiblingClusters {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("ExtractSiblingClusters model_file")
      println("ExtractSiblingClusters directory")
    }  else {
      val path = Paths.get(args(0))
      if (Files.isDirectory(path))
        extractDirectory(path)
      else
        extractFile(path)

    }
  }

  def extractDirectory(directory: Path): Unit = {
    val files = Files.list(directory).iterator().asScala.filter(_.toString.endsWith(".bif"))
    files.toList.par.foreach(extractFile)
  }

  def extractFile(file: Path) = {
    val filename = file.toString
    try {
      val model = Reader.readLTM(filename)

      val output = filename.replace(".bif", "-siblings.txt")
      manage(new PrintWriter(output)) { writer =>

        val observedNodes = model.getManifestVars.asScala.toList.map(model.getNode)
        val pairs = observedNodes.map(n => (n.getParent.getName, n.getName))
        val groupByPairs = pairs.groupBy(_._1)
        for (g <- groupByPairs) {
          writer.print(g._1)
          writer.print(": ")
          writer.println(g._2.map(_._2).mkString(", "))
        }
        println(s"${output} saved.")
      }
    } catch {
      case e: Exception =>
        println(s"Cannot process ${file}: ${e.getMessage}")
    }
  }
}
