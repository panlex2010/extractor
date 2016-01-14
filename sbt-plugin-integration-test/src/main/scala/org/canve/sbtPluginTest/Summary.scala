package org.canve.sbtPluginTest
import org.canve.shared.Execution._

/*
 * Summary Output formatter
 */
object Summary {
  def apply(results: List[Result]) = {
    println(Console.YELLOW + Console.BOLD + "\n\n  Summary  \n-----------")
    
    val output = results map { result => 
      Console.BOLD +  
      (result.result match {
        case Okay =>  
          Console.GREEN + "Worked Ok for project " + result.projectName +
          Console.RESET + elapsed(result.elapsed)
        case Failure => 
          Console.RED   + "Failed for project " + result.projectName +
          Console.RESET + elapsed(result.elapsed)
        case Skipped => 
          Console.YELLOW  + "Skipped testing against project " + result.projectName
      }) +
      Console.RESET
    }
    
    println(output.mkString("\n"))
    
    println()
  }
  
  private def elapsed(time: Long) = f" (took $time%,.0f milliseconds)" 
}
