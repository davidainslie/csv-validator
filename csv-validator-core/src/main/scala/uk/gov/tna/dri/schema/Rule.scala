package uk.gov.tna.dri.schema

import util.matching.Regex
import scalaz._
import Scalaz._
import uk.gov.tna.dri.metadata.Row
import util.Try

sealed trait Rule {
  def execute(columnIndex: Int, row: Row, schema: Schema): ValidationNEL[String, Any]
}

case class RegexRule(regex: Regex) extends Rule {
  override def execute(columnIndex: Int, row: Row, schema: Schema): ValidationNEL[String, Any] = {
    val reg = if (schema.columnDefinitions(columnIndex).contains(IgnoreCase())) "(?i)" + regex.pattern.pattern else regex.pattern.pattern

    if (row.cells(columnIndex).value matches reg) true.successNel[String]
    else s"regex: ${reg} fails for line ${row.lineNumber}, column: ${schema.columnDefinitions(columnIndex).id}".failNel[Any]
  }
}

case class InRule(inVal: StringProvider) extends Rule {
  override def execute(columnIndex: Int, row: Row, schema: Schema): ValidationNEL[String, Any] = {
    lazy val columnDefWithValue = schema.columnDefinitions.zip(row.cells)
    lazy val cellsByColumnId = columnDefWithValue.collect { case (x, y) => (x.id , y.value)} toMap

    val colVal = Try(inVal.getColumnValue(cellsByColumnId)).getOrElse("Invalid Column Name")

    /* Crap implementation */
    if (schema.columnDefinitions(columnIndex).contains(IgnoreCase())) {
      if (row.cells(columnIndex).value.toLowerCase().contains(colVal.toLowerCase())) true.successNel[String]
      else s"inRule: ${colVal} fails for line ${row.lineNumber}, column: ${schema.columnDefinitions(columnIndex).id}, value: ${row.cells(columnIndex).value}".failNel[Any]
    } else {
      if (row.cells(columnIndex).value.contains(colVal)) true.successNel[String]
      else s"inRule: ${colVal} fails for line ${row.lineNumber}, column: ${schema.columnDefinitions(columnIndex).id}, value: ${row.cells(columnIndex).value}".failNel[Any]
    }
  }
}

case class FileExistsRule(rootPath: Option[String] = None) extends Rule {

  override def execute(columnIndex: Int, row: Row, schema: Schema): ValidationNEL[String, Any] = {
    import java.io.File

    val filePath = row.cells(columnIndex).value

    val fileExists = rootPath match {
      case Some(root) => new File(root, filePath).exists()
      case None => new File(row.cells(columnIndex).value).exists()
    }

    if (fileExists) true.successNel else s"fileExistsRule: fails for line ${row.lineNumber}, column: ${schema.columnDefinitions(columnIndex).id}, value: ${filePath}".failNel[Any]
  }
}