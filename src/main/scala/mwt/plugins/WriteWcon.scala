package mwt.plugins

import mwt._
import mwt.numerics._

import java.io._

import scala.collection.JavaConverters._

import org.openworm.trackercommons._

class WriteWcon extends CustomComputation {
  private var chore: Choreography = null
  def initialize(args: Array[String], chore0: Choreography) {
    chore = chore0;
  }
  def desiredExtension = "wcon";
  def validateDancer(d: Dance) = true

  def computeAll(f: File) = {
    val scale = chore.mm_per_pixel
    val mm = units.Standard.millimeter
    val sec = units.Standard.second
    val data = chore.dances.filter(_ != null).map{ d =>
      Data(
        d.ID,
        "",
        chore.times.slice(d.first_frame, d.last_frame+1).map(_.toDouble),
        d.spine.map{ s => if (s == null) Array(0f) else Array.tabulate(s.size)(i => s.get(i, new Vec2F).x * scale) },
        d.spine.map{ s => if (s == null) Array(0f) else Array.tabulate(s.size)(i => s.get(i, new Vec2F).y * scale) },
        d.centroid.map{ c => c.x.toDouble * scale },
        d.centroid.map{ c => c.y.toDouble * scale },
        json.ObjJ.empty
      )
    }
    val u = UnitMap(Map("x"->mm, "y"->mm, "t"->sec, "age"->units.Standard.hour, "taps"->sec), json.ObjJ.empty)
    val m = Metadata(
      Vector(Laboratory("Kerr", "", "Janelia Farm Research Campus", json.ObjJ.empty)),
      Vector("Rex Kerr"),
      None,
      None,
      None,
      None,
      Some("none"),
      None,
      None,
      Some("adult"),
      Some(89.0),
      Some("N2"),
      Vector("Chemotaxis + tap assay", "Ask Rex for details"),
      Vector(Software("MWT", "1.3", Set.empty, json.ObjJ.empty), Software("Choreography", "1.3", Set("@XJ"), json.ObjJ.empty)),
      None,
      json.ObjJ(Map(
        "@XJ" -> (json.ObjJ(Map(
          "taps" -> (json.ANumJ((chore.times zip chore.events).filter(x => (x._2 != null) && (x._2 contains 1)).map(_._1.toDouble)) :: Nil)
        )) :: Nil)
      ))
    )
    val ds = DataSet(m, u, data, FileSet.empty, json.ObjJ.empty)
    val lines = ds.toObjJ.toJsons
    val pw = new PrintWriter(new FileOutputStream(f))
    try {
      lines.foreach(l => pw.println(l))
    }
    finally { pw.close }
    1
  }

  def computeDancerSpecial(d: Dance, f: File) = 0

  def quantifierCount = 0
  def computeDancerQuantity(d: Dance, which: Int) {}
  def quantifierTitle(which: Int): String = throw new IllegalArgumentException("No quantified parameters present.")
}
