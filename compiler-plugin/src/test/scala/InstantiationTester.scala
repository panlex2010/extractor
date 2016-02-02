package org.canve.compilerPlugin.test

import org.canve.compilerPlugin._
import scala.tools.nsc.Global
import utest._
import utest.ExecutionContext.RunNow
import compilerPluginUnitTest.InjectingCompilerFactory
import org.canve.simpleGraph._
import org.canve.simpleGraph.algo.impl._

/*
 * a compilation injectable that activates this compiler's core phase,
 * running some graph connectivity tests on it ― not currently in use 
 */
object InstantiationTester extends compilerPluginUnitTest.Injectable with NodeSearch {
  
  override def apply(global: Global)(body: global.Tree) = {
    
    val model: ExtractedModel = TraversalExtraction(global)(body)(new ExtractedModel(global))
    val graph = model.graph 
    
    val origin = findUniqueOrThrow(graph, "Foo", "object")
    val target = findUniqueOrThrow(graph, "Bar", "class")

    print(s"Tracing all acceptable paths from ${shortDescription(origin.data)} to ${shortDescription(target.data)}...")
    
    /* 
     * Get all members declared by the target class
     */
    val targetMethods = 
      graph.vertexEdges(target.key)
      .filter(edge => graph.direction(target.key, edge) == Egress && edge.data == "declares member")
      .map(edge => graph.vertexEdgePeer(target.key, edge))
    
    def voidFilter: graph.WalkStepFilter[Int, graph.Edge] = (v, e) => true
    def walkFilter: graph.WalkStepFilter[Int, graph.Edge] = (v, e) => {
      graph.direction(v, e) == Egress && (e.data match {
        case "declares member" => true
        case "uses" => true
        case "is instance of" => true
        case _ => false
      })
    }
    
    val allPaths: Set[Option[List[List[Int]]]] = targetMethods.map(target => 
      graph.getPathsBetween(origin.key, target, walkFilter))
      
    val paths: Option[List[List[Int]]] = Some(allPaths.flatten.flatten.toList)
     
    paths match {
      case None => throw new Exception("relationsnip not found")
      case Some(paths) => { 
        println(s" ${paths.size} paths found:")
        paths.foreach { path => 
          println("Path:")
          path.map(id => println("  " + shortDescription(graph.vertex(id).get.data)))
          println("  " + shortDescription(target.data))
        }
      }
    }
  }
  
  /* 
   * utility function for compact printing of symbols' qualified id
   */
  def shortDescription(symbol: ExtractedSymbol) = {
    val shortenedQualifiedId = (symbol.qualifyingPath.value.head.name.name match {
      case "<empty>" => symbol.qualifyingPath.value.drop(1)
      case _ => symbol.qualifyingPath.value
    }).map(_.name).mkString(".")
    
    s"${symbol.kind} $shortenedQualifiedId (${symbol.symbolCompilerId})"      
  }
  
}

/*
 * Search functions
 *  
 * TODO: consider indexing later on
 * TODO: consider moving over to the simple-graph library if this trait grows much
 */
trait NodeSearch { 
  def search(graph: ExtractedGraph, name: String, kind: String) = {
    graph.vertexIterator.filter(node => node.data.name.name == name && node.data.kind == kind).toList
  }
  
 def findUnique(graph: ExtractedGraph, name: String, kind: String) = { 
   val finds = search(graph, name, kind)
   if (finds.size == 1) 
     Some(finds.head)
   else
     None
 }
 
 def findUniqueOrThrow(graph: ExtractedGraph, name: String, kind: String) = {
   findUnique(graph, name, kind) match {
     case None => throw new Exception(s"graph $graph has ${search(graph, name, kind)} search results for name=$name, kind=$kind rather than exactly one.") 
     case Some(found) => found
   }
 }
}