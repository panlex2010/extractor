package org.canve.compilerPlugin.test

import org.canve.compilerPlugin._
import scala.tools.nsc.Global
import utest._
import utest.ExecutionContext.RunNow
import compilerPluginUnitTest.InjectingCompilerFactory
import org.canve.simpleGraph._
import org.canve.simpleGraph.algo.impl._

/*
 * core injectable that activates this compiler plugin's core phase and no more
 */
class TraversalExtractionTester extends compilerPluginUnitTest.Injectable {
  def apply(global: Global)(body: global.Tree) = {
    val graph: ExtractedModel = new ExtractedModel
    TraversalExtraction(global)(body)(graph)
  }   
}

/*
 * Search functions
 *  
 * TODO: consider indexing later on
 * TODO: consider moving over to the simple-graph library if this trait grows much
 */
trait NodeSearch { 
  def search(graph: ManagedGraph, name: String, kind: String): List[ManagedGraphNode] = {
    graph.vertexIterator.filter(node => node.data.name == name && node.data.kind == kind).toList
  }
  
 def findUnique(graph: ManagedGraph, name: String, kind: String): Option[ManagedGraphNode] = { 
   val finds = search(graph, name, kind)
   if (finds.size == 1) 
     Some(finds.head)
   else
     None
 }
 
 def findUniqueOrThrow(graph: ManagedGraph, name: String, kind: String): ManagedGraphNode = {
   findUnique(graph, name, kind) match {
     case None => throw new Exception(s"graph $graph has ${search(graph, name, kind)} search results for name=$name, kind=$kind rather than exactly one.") 
     case Some(found) => found
   }
 }
}

/*
 * injectable that activates this compiler's core phase, and then
 * does some testing with its output
 */
object InstantiationTester extends TraversalExtractionTester with NodeSearch {
  
  override def apply(global: Global)(body: global.Tree) = {
    
    val graph: ExtractedModel = TraversalExtraction(global)(body)(new ExtractedModel)
    val simpleGraph = new ManagedGraph(
      graph.symbols.get.map(ManagedGraphNode).toSet, 
      graph.symbolRelations.get.map(edge => ManagedGraphEdge(edge.symbolID1, edge.symbolID2, edge.relation))
    )
    
    val origin: ManagedGraphNode = findUniqueOrThrow(simpleGraph, "Foo", "object")
    val target: ManagedGraphNode = findUniqueOrThrow(simpleGraph, "Bar", "class")

    val targetMethods = simpleGraph.vertexEdgePeersFiltered(
      target.key, 
      (filterFuncArgs: FilterFuncArguments[ManagedGraphNode, ManagedGraphEdge]) => 
          (filterFuncArgs.direction == Egress && filterFuncArgs.edge.data == "declares member")) 
    
    print(s"Tracing all acceptable paths from ${shortDescription(origin)} to ${shortDescription(target)}...")
    
    def voidFilter(filterFuncArgs: FilterFuncArguments[ManagedGraphNode, ManagedGraphEdge]): Boolean = true
    def walkFilter(filterFuncArgs: FilterFuncArguments[ManagedGraphNode, ManagedGraphEdge]): Boolean = {      
      (filterFuncArgs.direction == Egress && filterFuncArgs.edge.data == "declares member") ||
      (filterFuncArgs.direction == Egress && filterFuncArgs.edge.data == "uses") ||
      (filterFuncArgs.direction == Egress && filterFuncArgs.edge.data == "is instance of")  
    }
    
    val allPaths: Set[Option[List[List[Int]]]] = targetMethods.map(target => 
      new GetPathsBetween(simpleGraph, walkFilter, origin.key, target).run)
      
    val paths: Option[List[List[Int]]] = Some(allPaths.flatten.flatten.toList)
     
    paths match {
      case None => throw new Exception("relationsnip not found")
      case Some(paths) => { 
        println(s" ${paths.size} paths found:")
        paths.foreach { path => 
          println("Path:")
          path.map(id => println("  " + shortDescription(simpleGraph.vertex(id).get)))
          println("  " + shortDescription(target))
        }
      }
    }
  }
  
  /* 
   * utility function for compact printing of symbols qualified id
   */
  def shortDescription(node: ManagedGraphNode) = {
    val symbol: ExtractedSymbol = node.data
    val shortenedQualifiedId = (symbol.qualifiedId.value.head.name match {
      case "<empty>" => symbol.qualifiedId.value.drop(1)
      case _ => symbol.qualifiedId.value
    }).map(_.name).mkString(".")
    
    s"${symbol.kind} $shortenedQualifiedId (${symbol.id})"      
  }
  
}

object MyTestSuite extends TestSuite {
  val compiler = InjectingCompilerFactory(InstantiationTester)
  
  assert(!compiler.reporter.hasErrors)
  
  val tests = TestSuite {
    
    "instantiation is reasonably captured" - { 
      
      "case class, with new keyword" - {  
        compiler.compileCodeSnippet("""
          case class Bar(a:Int) 
          object Foo {
            def get(g: Int) = {
              new Bar(g)
            }
          }
        """)
        assert(!compiler.reporter.hasErrors)
      }
      
      "case class, without new keyword" - {  
        compiler.compileCodeSnippet("""
          case class Bar(a:Int) 
          object Foo {
            def get(g: Int) = {
              Bar(g)
            }
          }
          """)
        assert(!compiler.reporter.hasErrors)
      }

      "non-case class" - {  
        compiler.compileCodeSnippet("""
          class Bar(a:Int) 
          object Foo {
            def get(g: Int) = {
              new Bar(g)
            }
          }
          """)
        assert(!compiler.reporter.hasErrors)
      }
    }
  }
  
  //val results = tests.run().toSeq.foreach(println)

  //println(results.toSeq.length) // 4
  //println(results.leaves.length) // 3
  //println(results.leaves.count(_.value.isFailure)) // 2
  //println(results.leaves.count(_.value.isSuccess)) // 1
}

