package tm.util

import java.io.File
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Path
import java.nio.file.FileVisitResult._
import java.nio.file.Files
import java.util.EnumSet
import java.nio.file.FileVisitOption
import java.nio.file.Paths

object FileHelpers {
  val filenameRegex = "(?s)(.*?)(?:\\.([^.]+))?".r

  def getPath(components: String*) = components.mkString(File.separator)
  def getNameAndExtension(filename: String) = filename match {
    case filenameRegex(name, ext) => (name, ext)
  }

  /**
   * Find files with the specified extension (e.g. pdf) in the specified
   * directory.
   */
  def findFiles(directory: Path, extension: String): Vector[Path] = findFiles(directory, List(extension))
  
  /**
   * Find files with the specified extensions (e.g. pdf, txt) in the specified
   * directory.
   */
  def findFiles(directory: Path, extensions: List[String]): Vector[Path] = {
    val files = collection.mutable.Buffer.empty[Path]
    val suffixes = extensions.map("." + _)
    val visitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attr: BasicFileAttributes) = {
        if (suffixes.isEmpty || suffixes.exists(file.toString.endsWith(_)))
          files += directory.relativize(file)

        CONTINUE
      }
    }

    Files.walkFileTree(directory,
      EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor)
    files.map(directory.resolve(_)).toVector
  }

  def exists(s: String) = Files.exists(Paths.get(s))

  def mkdir(d: String): Unit = mkdir(Paths.get(d))

  def mkdir(p: Path): Unit = {
    if (!Files.exists(p))
      Files.createDirectories(p)
  }
  
  def deleteFolder(folderName: String, recurssive: Boolean = false) {
    val folder = new File(folderName)
    val files = folder.listFiles()
    if(files!=null) { //some JVMs return null for empty dirs
        files.map{f =>
            if(f.isDirectory()) {
              if(recurssive) deleteFolder(f.toString)
              else throw new Exception("There exists subdirectory in "+ folderName)
            } else {
                f.delete()
            }
        }
    }
    folder.delete()
  }

}