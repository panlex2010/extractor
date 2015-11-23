package org.canve.compilerPlugin
import tools.nsc.Global
import Logging.Utility._

// TODO: capture leading comment lines, as a separate `comments` property.
//       surely the user will appreciate them, if they can be optionally showed to them.

object CodeExtract {

  /*
   * Extract the source code of the symbol using compiler supplied ranges 
   *
   * In the obvious case it just mirrors the original indentation in the source code,  
   * which is not included in the range given by the compiler, so it is here "added it back". 
   * 
   * In the less obvious case a definition may not start on a line of its own in the source
   * code (think anonymous definitions). In that case unrelated preceding text will be removed and 
   * replaced by leading spaces rather than only removed. Not optimal for anonymous classes
   * defined with the `new` keyword, but that's a compiler story... 
   */      
  private def grabSymbolCode(global: Global)(symbol: global.Symbol, span: Span) = {
    val sourceFile = symbol.sourceFile.toString
    val sourceFileContents = scala.io.Source.fromFile(sourceFile).mkString
    val firstLineIdentLength = sourceFileContents.slice(0, span.start).reverse.takeWhile(_ != '\n').length   
    
    " " * firstLineIdentLength + sourceFileContents.slice(span.start, span.end)
  }

  
  @deprecated("the heuristics are not needed, as long as -Yrangepos is used",
              "but since -Yrangepos may crash on scala 2.10 code that uses macros, this might come back") 
  private def getSymbolCodeHeuristically(global: Global)(symbol: global.Symbol): List[String] = {
    
    def getStartCol(s: String) = s.indexWhere(c => c !=' ')

    val startLine = symbol.pos.line
    val source = symbol.sourceFile.toString
    val sourceText = scala.io.Source.fromFile(source).getLines
    val sourceTextLines = scala.io.Source.fromFile(source).getLines.length
    
    if (sourceTextLines < startLine) {
      println(Console.YELLOW + Console.BOLD + "symbol " + symbolWithId(global)(symbol) + " " +
                                            "has line " + startLine + " " +
                                            "for source " + source + " " +
                                            "but that source file has only " +
                                            sourceTextLines + " lines!" +
                                            Console.RESET)
      return List()    
    }
    
    var body: List[String] = sourceText.drop(startLine-1).toList
    var done = false
    
    var inBracesNest  = 0
    var inQuote       = false
    
    val initialStartCol = getStartCol(body.head)
    
    while(sourceText.hasNext && !done) {
      val line = sourceText.next
      val startCol = getStartCol(line)
      
      if (startCol > initialStartCol) {  
        
        for (char <- line) // keep track of block nesting level,
                           // in case we want to use it later
          if (!inQuote) char match {
          case '{' => inBracesNest += 1
          case '}' => inBracesNest -= 1
          case '"' => inQuote = !inQuote
          case _ =>
          }

        body = body :+ line                  // consider a line further indented as belonging
        
      }
      else if (startCol == initialStartCol) 
        if (line(startCol) == '}') {         // consider first closing brace at initial indentation column
                                             // as the last line to belong
          body = body :+ line
          done = true
        }
        else 
          done = true                        // a line that is indented same as the initial indentation
                                             // indentation, but doesn't brace-close it, means we're done
                                             // (e.g. think a case class without an explicit body)
    }
    body
    
  }
  
  def apply(global: Global)(symbol: global.Symbol): ExtractedCode = {
    
    def logCantDetermine(reason: String) = {
      println(s"Could not determine source definition for symbol ${symbol.nameString} (${symbol.id}) because $reason") 
    }
    
    assert (symbol.sourceFile!=null)
        
    // guard statements necessary given all kinds of special cases
    
    if (symbol.isSynthetic) 
      return ExtractedCode(symbol.id, symbol.sourceFile.toString, NoLocationInfo, None)                   

    if (symbol.pos.toString == "NoPosition") { 
      // the above can be the case for Scala 2.10 projects, 
      // or just when macros are involved.
      logCantDetermine("pos property is NoPosition") 
      return ExtractedCode(symbol.id, symbol.sourceFile.toString, NoLocationInfo, None)
    }

    val sourceFilePath = symbol.sourceFile.toString
    val line   = symbol.pos.line
    val column = symbol.pos.column
    val start  = symbol.pos.startOrPoint // plain start may crash for scala 2.10 projects
    val end    = symbol.pos.endOrPoint   // plain end may crash for scala 2.10 projects

    if (line == 0) {
      // the compiler provides a line position 0 sometimes,
      // whereas line numbers are confirmed to start from 1. 
      // Hence we can't extract source here.         
      logCantDetermine("line=0")
      return ExtractedCode(symbol.id, sourceFilePath, NoLocationInfo, None)
    }
    
    if (start == end) {
      logCantDetermine(s"start=end ($start)")
      println(scala.io.Source.fromFile(sourceFilePath).mkString.slice(start, start + 20) + "[in ..." + sourceFilePath.takeRight(30) + "]")
      return ExtractedCode(symbol.id, sourceFilePath, NoLocationInfo, None)
    }

    /*
     * heuristic based extraction - may be necessary for supporting scala 2.10 so keep it alive in compilation
     * (c.f. https://github.com/scoverage/scalac-scoverage-plugin/blob/5d0c92479dff0055f2cf7164439f838b803fe44a/2.10.md)
     */        
    val blockFromHeuristic = getSymbolCodeHeuristically(global)(symbol)

    ExtractedCode(
      id = symbol.id,
      sourcePath = sourceFilePath, 
      location = Span(start, end), 
      code = Some(grabSymbolCode(global)(symbol, Span(start, end))))
  }
}