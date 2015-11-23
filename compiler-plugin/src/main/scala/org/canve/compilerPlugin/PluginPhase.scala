package org.canve.compilerPlugin
import scala.collection.{SortedSet, mutable}
import scala.tools.nsc.{Global, Phase}
import tools.nsc.plugins.PluginComponent

class PluginPhase(val global: Global)
                  extends PluginComponent
                  { t =>

  import global._

  val runsAfter = List("typer")

  override val runsRightAfter = Some("typer")
  
  val phaseName = "canve-extractor"

  def units = global.currentRun
                    .units
                    .toSeq
                    .sortBy(_.source.content.mkString.hashCode())

  override def newPhase(prev: Phase): Phase = new Phase(prev) {
    override def run() {
      
      val projectName = PluginArgs.projectName

      val projectGraph: ExtractedModel = new ExtractedModel(t.global)
      
      Log("extraction starting for project " + projectName + " (" + units.length + " compilation units)")
      
      Log(t.global.currentSettings.toString) // TODO: remove or move to new compiler plugin dedicated log file
      
      units.foreach { unit =>
        if (unit.source.path.endsWith(".scala")) {
          
          Log("examining source file" + unit.source.path + "...")
          projectGraph += TraversalExtractionWriter(t.global)(unit, projectName)
          Log("done examining source file" + unit.source.path + "...")
          
          Log(projectGraph.graph.vertexCount + " symbols so far extracted for project " + projectName)
          Log(projectGraph.graph.edgeCount + " symbol relations so far extracted for project " + projectName)

          
        } else Log("skipping non-scala source file: " + unit.source.path)
      }
      
      Output.write(projectGraph)
    }

    def name: String = "canve" 
  }

}