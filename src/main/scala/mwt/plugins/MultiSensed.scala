package mwt

import java.io._

import scala.language.postfixOps

import scala.annotation.tailrec

import mwt._
import mwt.plugins._

import ichi.core._
import ichi.maths._
import ichi.maths.PackedMaths._
import ichi.shipvl.ShipVL._
import ichi.shipvl.mutable._
import ichi.muple._
import ichi.mwt.mwtmath._
import ichi.mwt.GapScanner


final class MultiSensed extends CustomOutputModification {
  //========================================================================\\
  //  Useful constants and structures and methods and initialization data   \\
  //========================================================================\\
  final private var nosefrac = 0.08f
  final private var minOmegaT = 0.1f
  private[this] var chore_to_be: Option[Choreography] = None
  lazy val chore = chore_to_be.get
  lazy val events = chore.events.zipWithIndex.filter(_._1 ne null).map(_.each(_.toSet,identity))
  val rng = (new Rng.Hybrid2).seedWithTime
  var postfix: Option[String] = None
  
  final val nosey = collection.mutable.HashMap[Int, Soft[Dance, (EndInfo, EndInfo)]]()
  final val angles = collection.mutable.HashMap[Int, Soft[Dance, Angler]]()
  final val tracks = collection.mutable.HashMap[Int, Soft[Dance, Track]]()
  
  sealed trait Unity
  case object UnitLen extends Unity
  case object UnitAng extends Unity
  case object UnitTim extends Unity
  case object Unitless extends Unity
  
  val booleans = collection.mutable.HashMap(
    "nosepredict" -> false,
    "concentrated" -> false
  )
  val quanteans = collection.mutable.HashMap(
    "nosefrac" -> (Unitless, Array(0.08f)),
    "omegamin" -> (UnitTim, Array(0.1f)),
    "statetimes" -> (UnitTim, Array(Float.NaN)),
    "stillmin" -> (UnitTim, Array(0.08f)),
    "nonsensemin" -> (UnitTim, Array(0.02f)),
    "headpositions" -> (UnitTim, Array(0f, 0f)),
    "ranges" -> (UnitLen, Array(3f, 15f)),
    "source" -> (UnitLen, Array(Float.NaN, Float.NaN)),
    "diffuse" -> (UnitLen, Array(Float.NaN))
  )
  val descripteans = collection.mutable.HashMap[String,Option[String]](
    "report" -> None
  )
  
  implicit class TimeOfDance(val d: Dance) {
    def t(i: Int) = chore.times(d.first_frame+i)
    def dt(i: Int, j: Int) = t(j) - t(i)
  }
  
  def parseV(s: String, nv: Int, nonneg: Boolean = false, angled: Unity = UnitLen): Array[Float] = {
    val ss = s.split(',')
    if ((nv>0 && ss.length != nv) || ((nv<0) && ss.length+nv < 0)) {
      val howmany = (if (nv < 0) "at least "+(-nv) else nv.toString)
      throw new IllegalArgumentException("Expected "+howmany+" numbers, but found"+ss.mkString("\n  ","\n  ",""))
    }
    val vs = ss.map{ i => angled match {
      case UnitAng =>
        (new chore.PhysicalAngle(i,Choreography.AngularUnit.CIR)).tap(x => if (x.isError) { 
          throw new IllegalArgumentException("Angle format incorrect, should be value plus deg, rad, or circ (no space)\n  found: "+i)
        }).getRad
      case UnitLen => 
        (new chore.PhysicalLength(i,Choreography.LengthUnit.MM)).tap(x => if (x.isError || x.isRelative) { 
          throw new IllegalArgumentException("Position format incorrect, should be value plus px, mm, etc. (bl not allowed)\n  found: "+i)
        }).getMm(null)
      case UnitTim =>
        (new chore.PhysicalTime(i,Choreography.TemporalUnit.SEC)).tap(x => if (x.isError) { 
          throw new IllegalArgumentException("Time format incorrect, should be value plus s, ms, min, etc.\n  found: "+i)
        }).getSec()
      case _ =>
        i.toDouble
    }}
    if (nonneg && vs.exists(_ < 0)) throw new IllegalArgumentException("Values must be positive, but found" + vs.filter(_ < 0).mkString("\n  ","\n  ",""))
    vs.map(_.toFloat)
  }

  abstract class MsGeom {
    def dist(loc: MVec2F): Float
    def ang(loc: MVec2F, vel: GVec2F): Float
  }
  case class MsPoint(ori: IVec2F, rs: Array[Float], ths: Array[Float], solo: Option[Array[Float]]) extends MsGeom {
    def dist(loc: MVec2F) = loc dist ori
    def ang(loc: MVec2F, vel: GVec2F) = ((loc -~ ori) negEq) angle vel
  }
  case class MsLine(ori: IVec2F, vec: IVec2F, ds: Array[Float], ths: Array[Float], solo: Option[Array[Float]]) extends MsGeom {
    def dist(loc: MVec2F) = ((loc -~ ori) orthEq vec) len
    def ang(loc: MVec2F, vel: GVec2F) = (((loc -~ ori) orthEq vec) negEq) angle vel
  }
  
  val origin = MVec2F(0,0)
  
  final val kvx = new mwt.numerics.Vec2F(0,0)
  final def spt(s: Spine, i: Int, v: Vec2F) = {
    s.get(i,kvx)
    v :~ (kvx.x, kvx.y)
  }
  final def sfc(s: Spine, i: Int, v: Vec2F) = {
    s.get(i + (s.size+1)/2, kvx)
    v :~ (kvx.x, kvx.y)
  }
  final def nuv(ov: mwt.numerics.Vec2F, mult: Float = chore.mm_per_pixel) = IVec2F(mult*ov.x, mult*ov.y)
  final def c2v(nv: MVec2F, ov: mwt.numerics.Vec2F, mult: Float = chore.mm_per_pixel) = nv :~ (mult*ov.x, mult*ov.y)
  final def vpc(v: MVec2F, ov: mwt.numerics.Vec2F) = v +~ (ov.x, ov.y)
  
  def concentrator(L: Float): (Long => Double) = {
    val u = (quanteans("source")._2(0) vff quanteans("source")._2(1)) * (1/chore.mm_per_pixel)
    val iL2 = (chore.mm_per_pixel/L).sq
    if (L.finite) {
      (repr: Long) => {
        val d = (u distSq (new Vff(repr)))
        math.exp(-d*iL2)*iL2*OneOverPi
      }
    }
    else {
      (repr: Long) => -(u dist (new Vff(repr)))*chore.mm_per_pixel
    }
  }
  

  
  //========================================================================\\
  //                 Initialization and error-reporting code                \\
  //========================================================================\\
  def printHelp {
    println(
"""
Usage: MultiSensed[::sources][::values][::postfix=xyz][::dt=time][::data]
                  [::custom=option,[option,...]][::curvd=bodylens][::help]
  Provides output relevant to multisensory integration behavioral experiments.
  Requires Reoutline, Respine, and SpinesForward.
  Output types are compiled in and cannot be selected; data files end with
    .abc.ms
  where abc is one of
    omega - Parameterized omega turns
    rev   - Parameterized reversals
    curvf - Parameterized curves while moving forward
    curvr - Parameterized curves while moving backwards
    distp0 - Histogrammed distances from point 0 (1, 2, ..., resp.)
    distl0 - Histogrammed distances from line 0 (1, 2, ..., resp.)
    hist  - Histogrammed statistics
  Valid sources are
    select=type/v0,v1,...
      type is any one of the output types from Choreography (but no statistic)
        or evt1...evt4 for times relative to events 1-4 (can merge: evt124)
        or curvf / curvr for curving angle while moving forwards / backwards
        Use the default units for everything (fraction of circle for curvf/r)
      v0, v1, ... are bin borders for that type
    line=x0,y0,x1,y1/d0,d1,...[/th0,th1...][/solo,d,dd,n]
      x0,y0 and x1,y1 are points on the line
      d0, d1, ... are distances (default mm) from the line to bin
        note: must specify both positive and negative distances
        negative means x0,y0 origin gives negative cross prod with x1,y1
      th0, ... is a list of angles (default 1/2pi, deg & rad okay)
        angles all positive (0 to Pi) because of symmetry
      solo indicates that a n-bin histogram of distances should be produced
        estimating the number of animals closer than d in the first bin,
        and then between di and di+dd for following bins (default units mm)
    point=x,y/r0,r1,...[/th0,th1,...][/solo,d,dd,n]
      x,y are coordinates (default units mm; px, um also okay)
      r0, r1, ... is a list of radii into which to bin the data (default, mm)
      th0, ... is a list of angles to bin (default, 1/2pi; deg & rad okay)
        these are angles between animal's bearing and the vector to the point
        values are from -Pi to Pi in radians (-1/2 to 1/2)
      solo is as for line.
  Valid values are any output type for Choreography, separated by commas for
    long form (leave a trailing comma for long form with one entry).
    These will appear in histograms _after_ the default outputs, which are
      reversalpossible / omegapossible ratio (omega is time & num)
      omega fraction closed
      omega turn angle (signed)
      omega turn angle (absolute value)
      reversal distance
      reversal turn angle (signed)
      reversal turn angle (absolute value)
      forward curve turn angle (signed)
      forward curve turn angle (absolute value)
      backward curve turn angle (signed)
      backward curve turn angle (absolute value)
      animal bearing as assigned to reverse (one per select)
    Each entry is a trio: mean time num (yes, you get a minimum of 33 cols!)
    Note: omega and reversal durations can be calculated as time/num;
      use reversal distance for a clean calculation; omega doesn't have one yet
  postfix=xyz adds another suffix to the filename (xyz.abc.ms).
  data dumps all individual measurements to file; default is histograms
  custom will add custom outputs, in the order requested, from this list:
    headangle - the angle between the first and 4th body segments
    headblob - the width of the three head segments over 3 tail segments
    foldedness - the measure of curving used for open omega detection]
    perimarea - ratio of approximate perimeter to area
    scoredomega - 0 for not an omega, 1 for open, 1.5 for closed
    scoredreversal - 0 for not a reversal, 1 for reversal
    distance - distance (in mm) to first point or line (point preferred)
      distanceP is point only, distanceL is line only
      distanceP2 is distance to point 2 (counting starts at 0)
    angle - angle of motion relative to vector to first point or line
      angleP is point only, angleL is line only
      angleP1 is distance to point 1 (counting starts at 0)
  curvd sets the distance over which histograms of curve angles are computed.
    default is 0.5 (approximately one full cycle of sinusoid)
  help prints this message and quits.
Output:
  histogram format is [label bin [bin] ...] n0 v0 n1 v1 ... vN nN+1
    where the label and bin numbers are listed first
    and the vi are cut values and nj are numbers per bin (may be fractional)
  solo will produce file(s) with the time in the first column and estimated
    animal counts in each successive column, from innermost to outermost.  The
    count is not cumulative across area; the 3rd bin are animals in a strip
    between d+dd and d+2*dd, for instance.
""".trim
    )
    throw new CustomHelpException
  }
  
  def initialize(args: Array[String], chore0: Choreography) {
    chore_to_be = Some(chore0)

    def lcid(id: String)(s: String) = s.toLowerCase == id
    def lcsub(pre: String)(s: String) = if (s.toLowerCase.startsWith(pre)) Some(s.substring(pre.length)) else None
    
    val (helps,rest1) = args.partition(lcid("help") _)
    val (nosed,rest1a) = rest1.collectBoth((lcsub("nose=") _).drop)
    val (minomt,rest1b) = rest1a.collectBoth((lcsub("min_omega_t=") _).drop)
    val rest1c = rest1b.partition(s => booleans contains s.toLowerCase).fold{ (a,b) => a.foreach(x => booleans(x.toLowerCase) = true); b }
    val rest1d = rest1c.partition(s => quanteans exists{ case (t,_) => s.toLowerCase startsWith t+"="}).fold{ (a,b) =>
      a.foreach{ x => 
        val (u,ps) = quanteans(x.takeWhile(_ != '='))
        val qs = parseV(x.dropWhile(_ != '=').drop(1), ps.length, false, u)
        System.arraycopy(qs,0,ps,0,ps.length)
      }
      b
    }
    val rest1e = rest1d.partition(s => descripteans exists{ case (t,_) => s.toLowerCase startsWith t}).fold{ (a,b) =>
      a.foreach( x => descripteans(x.takeWhile(_ != '=')) = Some(x.dropWhile(_ != '=').drop(1)) )
      b
    }   
    val (dumps,rest2) = rest1e.partition(lcid("dump") _)
    val (posts,rest3) = rest2.collectBoth((lcsub("postfix=") _).drop)
    val (points,rest4) = rest3.collectBoth((lcsub("point=") _).drop)
    val (lines,rest5) = rest4.collectBoth((lcsub("line=") _).drop)
    
    nosed.headOption.map(_.toFloat).foreach(nosefrac = _)
    minomt.headOption.map(_.toFloat).foreach(minOmegaT = _)
    
    if (!helps.isEmpty) printHelp
    val required = Vector("mwt.plugins.Reoutline","mwt.plugins.Respine","mwt.plugins.SpinesForward")
    val actual = required.toArray.tap(chore.requirePlugins)
    (actual zip required).filter(x => x._1==null || x._1.length==0).when(!_.isEmpty).foreach{ ar =>
      throw new IllegalArgumentException("Plugin(s) "+ar.map(_._2).mkString(", ")+" (required by MultiSensory) are missing.")
    }
    
    val dump = !dumps.isEmpty
    postfix = posts.lastOption
    val emptyIa = new Array[Int](0)
    
    def parsePoint(s: String) = {
      val ss = s.split('/')
      if (ss.length<2 || ss.length>4) throw new IllegalArgumentException("Point requires two or three blocks separated by / but found"+ss.mkString("\n  ","\n  ",""))
      val xy = parseV(ss(0),2)
      val loc = BVec2F(xy)
      val rs = parseV(ss(1),-1,true)
      val ths = if (ss.length>3 || (ss.length==3 && !ss(2).startsWith("solo"))) parseV(ss(2),-1,false,UnitAng) else Array[Float]()
      val solo = if (ss.length>2 || ss.last.startsWith("solo")) Some(parseV(ss.last.drop(5),3,true)) else None
      val vec = loc.next / chore.mm_per_pixel
      if (origin === (0,0)) origin :~ vec
      MsPoint(vec, rs, ths, solo)
    }
    def parseLine(s: String) = {
      val ss = s.split('/')
      if (ss.length<2 || ss.length>4) throw new IllegalArgumentException("Line requires two or three blocks separated by / but found"+ss.mkString("\n  ","\n  ",""))
      val xyxy = parseV(ss(0),4)
      val loc = BVec2F(xyxy)
      val rs = parseV(ss(1),-1)
      val ths = if (ss.length>3 || (ss.length==3 && !ss(2).startsWith("solo"))) parseV(ss(2),-1,true,UnitAng) else Array[Float]()
      val solo = if (ss.length>2 && ss.last.startsWith("solo")) Some(parseV(ss.last.drop(5),3,true)) else None
      MsLine(loc.next / chore.mm_per_pixel, loc.next / chore.mm_per_pixel, rs, ths, solo)
    }
  }
  
  
  //========================================================================\\
  //                            Preparatory work                            \\
  //========================================================================\\
  class EndInfo(tip: Array[Long], trunk: Array[Long], val radius: Array[Float], val errXY: Float) {
    def tipv(i: Int): Vff = Vff.from(tip(i))
    def trunkv(i: Int): Vff = Vff.from(trunk(i))
    def arrow(i: Int): Vff = (Vff.from(tip(i)) - Vff.from(trunk(i))).^
  }
  
  final class NoseFitter(d: Dance, i: Int, op: Array[mwt.numerics.Vec2F], val origin: Vff) {
    @inline final def R(j: Int) = { if (j+1 < op.length) j+1 else 0 }
    @inline final def L(j: Int) = { if (j > 0) j-1 else op.length-1 }
    val fit = new mwt.numerics.Fitter
    fit.resetAt(origin.x, origin.y)
    def at(indices: LongPack): this.type = {
      fit.resetAt(origin.x, origin.y)
      add(indices)
    }
    def add(indices: LongPack): this.type = {
      val i0 = indices.i0
      val i1 = indices.i1
      var i = i0
      fit.addC(op(i).x, op(i).y)
      while (i != i1) {
        val j = R(i)
        fit.addC(op(j).x, op(j).y)
        i = j
      }
      this
    }
    def drop(indices: LongPack): this.type = {
      val i0 = indices.i0
      val i1 = indices.i1
      var i = i0
      fit.subC(op(i).x, op(i).y)
      while (i != i1) {
        val j = R(i)
        fit.subC(op(j).x, op(j).y)
        i = j
      }
      this
    }
    def farthest(c: LongPack): Int = {
      val i0 = c.i0
      val i1 = c.i1
      var i = i0
      var ix = i0
      var dx = 0.0
      while (i != i1) {
        val d = (op(i) dist2 op(i0)) + (op(i) dist2 op(i1))
        if (d > dx) { dx = d; ix = i }
        i = R(i)
      }
      ix
    } 
    def wrap(c: LongPack, w: Double, unwrap: Boolean = false, quiet: Boolean = true): LongPack = {
      def pr[A](a: A) = { if (!quiet) println(a); a }
      var bias = 0
      var d = 0.0
      var j0 = c.i0
      var j1 = c.i1
      while (d < w) {
        val k0 = if (unwrap) R(j0) else L(j0)
        val k1 = if (unwrap) L(j1) else R(j1)
        pr((j0,j1))
        pr((k0,k1))
        pr((d,w))
        if ((op(k0) dist2 op(j1)) < (op(k1) dist2 op(j0))) {
          d += pr(op(k0) dist op(j0))
          if (bias == 3) {
            bias = 0
            d += pr(op(k1) dist op(j1))
            j1 = k1
          }
          j0 = k0
          bias = math.max(bias+1, 1)
        }
        else {
          d += pr(op(k1) dist op(j1))
          if (bias == -3) {
            bias = 0
            d += pr(op(k0) dist op(j0))
            j0 = k0
          }
          j1 = k1
          bias = math.min(bias-1, -1)
        }
        pr((d,w))
      }
      j0 packII j1
    }
    def consistency(r: LongPack, c: Vff): Double = if (r.i0 == r.i1) 0.0 else {
      var n,m = 0
      var i = r.i0
      var j = R(i)
      val j1 = r.i1
      while (j != j1) {
        val a = op(i).vff - c
        val b = op(j).vff - op(i).vff
        if (math.signum(a X b) < 0) m += 1 else n += 1
        i = j
        j = R(j)
      }
      math.abs(n-m)/math.max(1.0, (n+m).toDouble)
    }
    def center(r: LongPack): Vff = {
      val u = MVec2D.zero
      var i = r.i0
      val i1 = r.i1
      u +~ (op(i).x, op(i).y)
      var n = 1
      while (i != i1) {
        i = R(i)
        u +~ (op(i).x, op(i).y)
        n += 1
      }
      u *~ (1.0/n)
      Vff.from(u.x, u.y)
    }
  }
  object NoseFitter {
    def closest(op: Array[mwt.numerics.Vec2F], ov: mwt.numerics.Vec2F): Int = {
      var d = op(0) dist2 ov
      var i = 0
      var ii = 1
      while (ii < op.length) {
        val di = op(ii) dist2 ov
        if (di < d) { i = ii; d = di }
        ii += 1
      }
      i
    }
    def fitNoseAndTail(d: Dance): (EndInfo, EndInfo) = {
      val hs,ks,ts,rs = new Array[Long](d.centroid.length)
      val cs,ds = new Array[Float](d.centroid.length)
      var i = 0
      val ov = mwt.numerics.Vec2F.zero
      val w = nosefrac*d.meanBodyLengthEstimate  //(math.Pi*0.15).toFloat*d.meanBodyLengthEstimate*0.1f
      while (i < hs.length) {
        if (d.spine==null || d.spine.length <= i || d.spine(i)==null || d.outline==null || d.outline.length <= i || d.outline(i)==null) {
          hs(i) = Vff.NaN.repr
          ks(i) = Vff.NaN.repr
          cs(i) = Float.NaN
          ts(i) = Vff.NaN.repr
          rs(i) = Vff.NaN.repr
          ds(i) = Float.NaN
        }
        else for (h <- List(true, false)) {
          d.spine(i).get(if (h) 1 else d.spine(i).size-2,ov).eqPlus(d.centroid(i))
          val s1 = Vff(ov.x, ov.y)
          d.spine(i).get(if (h) 0 else d.spine(i).size-1,ov).eqPlus(d.centroid(i))
          val s0 = Vff(ov.x, ov.y)
          val op = d.outline(i).unpack(new Array[mwt.numerics.Vec2F](d.outline(i).size))
          val tipi = NoseFitter.closest(op, ov)
          
          val nf = new NoseFitter(d, i, op, (s0 + s1) * 0.5f)
          val j0j1: LongPack = nf.wrap(nf.wrap(tipi packII tipi, 4*w), 2*w, unwrap = true)
          nf.at(j0j1)
          nf.fit.circ.fit()
          var cV = Vff.from(nf.fit.circ.params.x0, nf.fit.circ.params.y0)
          var uV = cV
          var cR = nf.fit.circ.params.R.toFloat
          nf.fit.spot.fit()
          val sV = Vff.from(nf.fit.spot.params.x0, nf.fit.spot.params.y0)
          val sS = nf.fit.spot.params.sigma.toFloat
          val toofar = (cV distSq sV) > sS*sS
          if (toofar && nf.consistency(j0j1, cV) > 0.9) {
            uV = (sV - cV).^
          }
          else {
            if (toofar) {
              cV = sV
              cR = sS
            }
            val k0k1: LongPack = nf.wrap(j0j1, w)
            nf.at(k0k1.i0 packII j0j1.i0)
            nf.add(j0j1.i1 packII k0k1.i1)
            nf.fit.circ.fit()
            val nci = Vff.from(nf.fit.circ.params.x0, nf.fit.circ.params.y0)
            nf.fit.spot.fit()
            val nsi = Vff.from(nf.fit.spot.params.x0, nf.fit.spot.params.y0)
            if ((nci distSq nsi) > nf.fit.spot.params.sigma.sq) {
              val vl = nf.center(k0k1.i0 packII j0j1.i0)
              val vr = nf.center(j0j1.i1 packII k0k1.i1)
              uV = (cV - (vl + vr)*0.5f).^
            }
            else uV = (cV - nci).^
          }
          if (h) {
            hs(i) = cV.repr
            ks(i) = (cV + cR*uV).repr
            cs(i) = cR
          }
          else {
            ts(i) = cV.repr
            rs(i) = (cV + cR*uV).repr
            ds(i) = cR
          }
        }
        i += 1
      }
      val q = new Array[Float](d.centroid.length)
      val List(he,te) = for (vs <- List(ks, rs)) yield {
        var j = 1
        var n = 0
        var m = 0
        while (j+1 < vs.length) {
          val n1 = Vff.from(vs(j-1)) - d.centroid(j-1).vff
          val p1 = Vff.from(vs(j+1)) - d.centroid(j+1).vff
          val v0 = (n1+p1)*0.5f
          val v = (Vff.from(vs(j)) - d.centroid(j).vff)
          val dv = v - v0
          if (!dv.nan) { 
            q(n) = dv.lenSq
            n += 1
          }
          j += 1
        }
        if (n<=0) 0f else math.sqrt(rankFractionF(q, 0.5, 0, n)).toFloat
      }
      (new EndInfo(ks, hs, cs, he), new EndInfo(rs, ts, ds, te))
    }
  }
  
  class Angler(val body: Array[Float], val head: Array[Float], val tail: Array[Float], val track: Array[Float]) {
    import java.util.Arrays.copyOfRange
    def this(n: Int) = this(new Array[Float](n), new Array[Float](n), new Array[Float](n), Array.fill(n)(Float.NaN))
    def slice(i0: Int, i1: Int) = new Angler(copyOfRange(body,i0,i1+1), copyOfRange(head,i0,i1+1), copyOfRange(tail,i0,i1+1), copyOfRange(track,i0,i1+1))
    def findSwings(sigma: Float, useHead: Boolean = true, useTrack: Boolean = true, storeDelta: Option[Array[Float]] = None) = {
      val delta = {
        val xs = { 
          val qs = (if (useHead) head else tail)
          if (storeDelta.isEmpty || storeDelta.get.length != qs.length) java.util.Arrays.copyOf(qs,qs.length)
          else { val ds = storeDelta.get; System.arraycopy(qs, 0, ds, 0, qs.length); ds }
        }
        val ws = (if (useTrack) track else body)
        var i = 0
        while (i < body.length) { xs(i) -= ws(i); if (isNaN(xs(i))) { xs(i) = 0.0f }; i += 1 }
        xs
      }
      val enough = -sigma*icdfNormal(0.05).toFloat
      findForaysF(delta, enough)()
    }
  }
  object Angler {
    def measureAngles(d: Dance) = {
      def threshsign(s: Float, t: Float) = if (math.abs(s) >= t) math.signum(s).toInt else 0
      def consistentlyNear(a: Float, b: Float, s: Int) = {
        val c = a angleNear b
        if (s != 0 && s != math.signum(c-b)) {
          if (s < 0) (c - 2*math.Pi).toFloat
          else (c + 2*math.Pi).toFloat
        }
        else c
      }
      val (hi, ti) = nosey(d.ID)()
      val o = mwt.numerics.Vec2F.zero
      val ans = new Angler(d.centroid.length)
      var i = 0
      while (i < d.centroid.length) {
        if (d.spine(i)==null || !d.spine(i).oriented) {
          ans.body(i) = Float.NaN
          ans.head(i) = Float.NaN
          ans.tail(i) = Float.NaN
          ans.track(i) = Float.NaN
        }
        else {
          val j = math.floor(d.spine(i).size*0.401).toInt
          val k = math.ceil(d.spine(i).size*0.599).toInt
          val vj = d.spine(i).get(j,o).eqPlus(d.centroid(i)).vff
          val vk = d.spine(i).get(k,o).eqPlus(d.centroid(i)).vff
          val c = (vj-vk)
          val ch = hi.trunkv(i) - vj
          val h = hi.tipv(i) - hi.trunkv(i)
          val ct = vk - ti.trunkv(i)
          val t = ti.trunkv(i) - ti.tipv(i)
          val sh = threshsign(math.signum( c X ch ) + math.signum( ch X h ), 1.5f)
          val st = -threshsign(math.signum( ct X c ) + math.signum( t X ct ), 1.5f)
          ans.body(i) = if (i>0 && !isNaN(ans.body(i-1))) c.theta angleNear ans.body(i-1) else c.theta
          ans.head(i) = consistentlyNear(h.theta, ans.body(i), sh)
          ans.tail(i) = consistentlyNear(t.theta, ans.body(i), st)
          ans.track(i) = Float.NaN
        }
        i += 1
      }
      ans
    }
  }
  
  trait Chunk extends InclusiveRange { def ch: Char }
  trait SegChunk extends Chunk {
    def s0: Int
    def s1: Int
  }
  trait MovChunk extends SegChunk {
    def fwd: Array[Long]
    def pts: Array[Long]
    def ds: Array[Float]
  }
  trait WigChunk extends MovChunk {
    def wig: Array[Wiggle]
  }
  final case class Undeclared(i0: Int, i1: Int) extends Chunk { def ch = 'u' }
  final case class ClosedOmega(i0: Int, i1: Int) extends Chunk { def ch = 'o' }
  final case class NotOmega(i0: Int, i1: Int) extends Chunk { def ch = 'O' }
  final case class NotValid(i0: Int, i1: Int) extends Chunk { def ch = 'n' }
  final case class Behaving(i0: Int, i1: Int) extends Chunk { def ch = 'B' }
  final case class Steady(i0: Int, i1: Int) extends Chunk { def ch = 's' }
  final case class Moving(i0: Int, i1: Int, s0: Int, s1: Int) extends SegChunk { def ch = 'M' }
  final case class Advance(i0: Int, i1: Int, s0: Int, s1: Int)(val fwd: Array[Long], val pts: Array[Long], val ds: Array[Float]) extends MovChunk { def ch = 'A' }
  final case class Retreat(i0: Int, i1: Int, s0: Int, s1: Int)(val fwd: Array[Long], val pts: Array[Long], val ds: Array[Float]) extends MovChunk { def ch = 'R' }
  final case class Fore(i0: Int, i1: Int, s0: Int, s1: Int)(val fwd: Array[Long], val pts: Array[Long], val ds: Array[Float], val wig: Array[Wiggle]) extends WigChunk { def ch = 'f' }
  final case class Back(i0: Int, i1: Int, s0: Int, s1: Int)(val fwd: Array[Long], val pts: Array[Long], val ds: Array[Float], val wig: Array[Wiggle]) extends WigChunk { def ch = 'b' }
  final case class Wiggle(i0: Int, i1: Int, t0: Float, t1: Float, y0: Float, y1: Float, phi0: Float, phi1: Float) extends InclusiveRange {
    val idcph = 1.0/(math.cos(phi1) - math.cos(phi0))
    val cph0 = math.cos(phi0)
    def y(t: Float) = (y0 + (y1-y0)*(math.cos(((t-t0)/(t1-t0))*(phi1-phi0) + phi0) - cph0)*idcph).toFloat
  }
  
  final class Track(d: Dance)(val parts: Array[Chunk] = Array(Undeclared(0, d.centroid.length-1)), val headnoise: Float = Float.NaN) {
    
    def omegize = nosey.get(d.ID).filter(_ => !parts.isEmpty).map(_()).map{ case (h,t) =>
      d.quantityIsMidline(false)
      val mmed = rankFractionF(d.quantity, 0.5f)
      val mstd = fwhm2sigma( rankFractionF(d.quantity,0.75f) - rankFractionF(d.quantity,0.25f) )
      def msig = icdfNormal(0.1 / math.sqrt(d.quantity.length))*mstd
      val q = { var xs = new Array[Float](h.radius.length); var i = 0; while (i<xs.length) { xs(i) = math.max(h.radius(i), t.radius(i)); i += 1 }; xs }
      def rmed = rankFractionF(q, 0.5f)
      def rstd = fwhm2sigma( rankFractionF(q,0.75f)  - rankFractionF(q,0.25f) )
      def rsig = -icdfNormal(0.01 / q.length)*rstd
      val chunks = Array.newBuilder[Chunk]
      @tailrec def inner(a: Array[Chunk])(current: Chunk, index: Int)(accept: Int = current.i0) { current match {
        case Undeclared(i0, i1) =>
          var i = accept
          var ix = i0
          do {
            while (i <= i1 && !(d.quantity(i) - mmed < msig && q(i) - rmed > rsig)) i += 1
            ix = i
            while (i <= i1 && d.quantity(i) - mmed < msig && q(i) - rmed > rsig) i += 1
          } while (i <= i1 && d.dt(ix, i-1) < minOmegaT);
          if (i <= i1) {
            if (ix > i0) chunks += NotOmega(i0, ix-1)
            chunks += ClosedOmega(ix, i-1)
          }
          else chunks += NotOmega(i0, i1)
          if (i <= i1) inner(a)(Undeclared(i,i1), index)(i) else if (index < a.length) inner(a)(a(index), index+1)()
        case x =>
          chunks += x
          if (index < a.length) inner(a)(a(index), index+1)()
      }}
      inner(parts)(parts(0), 1)()
      chunks.result
    }.map(x => new Track(d)(x)).getOrElse(this)
    
    def invalidize = optIn(!parts.isEmpty){
      val unambiguousDist = math.abs(d.noise_estimate.average * icdfNormal(0.05f/d.centroid.length))
      if (!d.loaded_bias.already) d.quantityIsBias(chore.times, chore.speed_window, unambiguousDist.toFloat, false)
      val chunks = Array.newBuilder[Chunk]
      @tailrec def inner(a: Array[Chunk])(current: Chunk = a(0), index: Int = 1) { current match {
        case NotOmega(i0, i1) =>
          var i = i0
          while (i <= i1 && !isNaN(d.quantity(i))) i += 1
          if (i > i0) chunks += Behaving(i0, i-1)
          if (i <= i1) {
            val ix = i
            while (i <= i1 && isNaN(d.quantity(i))) i += 1
            chunks += NotValid(ix, i-1)
          }
          if (i <= i1) inner(a)(NotOmega(i,i1), index) else if (index < a.length) inner(a)(a(index), index+1)
        case x =>
          chunks += x
          if (index < a.length) inner(a)(a(index), index+1)
      }}
      inner(parts)(parts(0), 1)
      chunks.result
    }.map(x => new Track(d)(x.compacted{ (l,r) => 
      l match {
        case ClosedOmega(i0, i1) =>
          r match { case NotValid(j0, j1) if (d.dt(j0,j1) < quanteans("nonsensemin")._2(0)) => Some(ClosedOmega(i0,j1)); case _ => None }
        case NotValid(i0, i1) if (d.dt(i0,i1) < quanteans("nonsensemin")._2(0)) =>
          r match { case ClosedOmega(j0, j1) => Some(ClosedOmega(i0,j1)); case _ => None }
        case _ => None
      }
    }.compacted{ (l,r) =>
      l match { case ClosedOmega(i0, i1) => r match { case ClosedOmega(j0, j1) => Some(ClosedOmega(i0,j1)); case _ => None }; case _ => None }
    })).getOrElse(this)
    
    def mobilize = optIn(!parts.isEmpty){
      if (d.segmentation == null) d.findSegmentation
      val chunks = Array.newBuilder[Chunk]
      @tailrec def inner(a: Array[Chunk])(current:Chunk = a(0), index: Int = 1){ current match {
        case Behaving(i0, i1) =>
          val s0 = d.indexToSegment(i0)
          val s1 = d.indexToSegment(i1)
          var i = i0
          var s = s0
          while (s <= s1) {
            val isLine = d.segmentation(s).isLine
            var r = s+1
            while (r <= s1 && isLine==d.segmentation(r).isLine) r += 1
            val j = math.min(i1, d.segmentation(r-1).i1)
            chunks += (if (isLine) Moving(i, j, s, r-1) else Steady(i, j))
            s = r
            i = j+1
          }
          if (index < a.length) inner(a)(a(index), index+1)
        case x =>
          chunks += x
          if (index < a.length) inner(a)(a(index), index+1)
      }}
      inner(parts)(parts(0), 1)
      chunks.result
    }.map(x => new Track(d)(x)).getOrElse(this)
    
    def directionalize = optIn(!parts.isEmpty){
      val ov = mwt.numerics.Vec2F.zero
      val ua, ub, uc, ud, ue, uf = MVec2F.zero
      val unambiguousDist = math.abs(d.noise_estimate.average * icdfNormal(0.05f/d.centroid.length))
      if (!d.loaded_bias.already) d.quantityIsBias(chore.times, chore.speed_window, unambiguousDist.toFloat, false)
      if (d.segmentation == null) d.findSegmentation
      val chunks = Array.newBuilder[Chunk]
      val ps = { val x = new Array[Long](d.centroid.length); java.util.Arrays.fill(x, Vff.NaN.repr); x }
      val qs = { val x = new Array[Long](d.segmentation.length); java.util.Arrays.fill(x, Vff.NaN.repr); x }
      val ds = { val x = new Array[Float](d.centroid.length); java.util.Arrays.fill(x, Float.NaN); x }
      val bs = angles(d.ID)().body
      val ts = angles(d.ID)().track
      var lastDs = 0f
      @tailrec def inner(a: Array[Chunk])(current: Chunk = a(0), index: Int = 1){ current match {
        case Moving(i0, i1, s0, s1) =>
          var i = i0
          var s = s0
          while (s <= s1) {
            val seg = d.segmentation(s)
            val arc = seg.kind==Style.Styled.Arc
            var ath = 0.0  // For CW/CCW determination along arcs
            if (arc) ud :~~ (seg.fit.circ.params.x0, seg.fit.circ.params.y0)
            i = seg.i0
            seg.snapToLine(ov.eq(d.centroid(i))); ov ~~> ua
            if (arc) (uf :~ ua) -~ ud
            ps(i) = ua.vff.repr
            i += 1
            while (i <= seg.i1) {
              seg.snapToLine(ov.eq(d.centroid(i))); ov ~~> ub
              val bias = math.round((d.quantity(i)+d.quantity(i-1))*0.3) bound (-1, 1)
              if (arc) {
                ue :~ uf
                (uf :~ ub) -~ ud
                ath += bias*(ue X uf)
              }
              ps(i) = ub.vff.repr
              (ua -~ ub) *~ bias
              uc -~ ua  // uc += bias*(ub-ua), but destroying ua instead of ub
              ua :~ ub
              i += 1
            }
            if (uc.lenSq==0) { val x = bs((seg.i1+seg.i0)/2); uc :~~ (math.cos(x), math.sin(x)) } else uc.normEq
            qs(s) = uc.vff.repr
            if (arc) {
              var j = seg.i0
              while (j <= seg.i1) {
                (Vff.from(ps(j)) ~~> ue) -~ ud
                if (ath < 0) ue.cwEq else ue.ccwEq
                ts(j) = ue.theta angleNear bs(j)
                j += 1
              }
            }
            else {
              val th = uc.theta
              var j = seg.i0
              while (j <= seg.i1) { ts(j) = th angleNear bs(j); j += 1 }
            }
            s += 1
          }
          var dmax, dist = 0f
          i = i0
          var imax = i
          var j = i
          s = s0
          var smax = s
          var r = s
          ds(i) = lastDs
          while (i < i1) {
            i += 1
            val nexts = (i > d.segmentation(s).i1)
            val v = Vff.from(ps(i)) - Vff.from(ps(i-1))
            val u = if (i > d.segmentation(s).i1) (Vff.from(qs(1+s))+Vff.from(qs(s))).^ else Vff.from(qs(s))
            if (nexts) s += 1
            dist += v*u
            ds(i) = lastDs+dist
            if ( math.abs(dmax) <= unambiguousDist || (math.signum(dist)==math.signum(dmax) && math.abs(dist) >= math.abs(dmax)) ) {
              dmax = dist
              imax = i
              smax = s
            }
            else if (math.abs(dist-dmax) > unambiguousDist) {
              chunks += {
                val qx = java.util.Arrays.copyOfRange(qs, r, smax+1)
                val px = java.util.Arrays.copyOfRange(ps, j, imax+1)
                val dx = java.util.Arrays.copyOfRange(ds, j, imax+1)
                if (dmax < 0) Retreat(j, imax, r, smax)(qx, px, dx) else Advance(j, imax, r, smax)(qx, px, dx)
              }
              lastDs += dmax
              dist -= dmax
              dmax = dist
              j = imax+1
              r = if (d.segmentation(smax).i1<j) smax+1 else smax
              imax = i
              smax = s
            }
          }
          if (j <= i1) {
            val qx = java.util.Arrays.copyOfRange(qs, r, s1+1)
            val px = java.util.Arrays.copyOfRange(ps, j, i1+1)
            val dx = java.util.Arrays.copyOfRange(ds, j, i1+1)
            chunks += (if (dist < 0) Retreat(j, i1, r, s1)(qx, px, dx) else Advance(j, i1, r, s1)(qx, px, dx))
            lastDs = ds(i1)
          }
          if (index < a.length) inner(a)(a(index), index+1)          
        case x =>
          chunks += x
          if (index < a.length) inner(a)(a(index), index+1)
      }}
      inner(parts)(parts(0), 1)
      chunks.result
    }.map(x => new Track(d)(x)).getOrElse(this)
    
    def wiggling = {
      val nos = nosey(d.ID)()._1
      val ang = angles(d.ID)()
      val u = java.util.Arrays.copyOfRange(chore.times, d.first_frame, d.last_frame+1)
      val y = ang.head
      val xScaled = new Array[Float](d.centroid.length)
      val yScaled = java.util.Arrays.copyOf(y, y.length)
      val asd = new AccumulateSD
      parts.foreach {
        case m: MovChunk =>
          val dic = m.ds((m.i1 - m.i0)/2)
          var i = m.i0
          while (i <= m.i1) {
            xScaled(i) = (m.ds(i-m.i0) - dic)
            i += 1
          }
          i = m.i0+1
          while (i < m.i1) {
            val dy = (y(i)-0.5*(y(i+1)+y(i-1)))
            if (!dy.nan) asd +~ dy
            i += 1
          }
        case _ =>
      }
      val noisy = (asd.sd*math.sqrt(2.0/3.0)).toFloat
      val inoisey = 1.0f/noisy
      val inoisex = (1.0/d.noise_estimate.average).toFloat
      val toofar = -icdfNormal(0.05/d.centroid.length).toFloat
      var k = 0
      while (k < y.length) {
        xScaled(k) *= inoisex
        yScaled(k) *= inoisey
        k += 1
      }
      new Track(d)(
        parts.map{
          case m: MovChunk =>
            val (lins, slopes) = fitLinearSegments(xScaled, yScaled, toofar)(m.i0, m.i1)
            
            val ob = Array.newBuilder[Int]
            var dir = 0
            ob += 0
            slopes.forpieces(_ < 0){ (_, b, _, j) => if (dir==0) { dir = if (b) 1 else -1 }; ob += j }
            val o = ob.result
            
            val p = new Array[Float](math.max(0,o.length-1))
            var oi = 1
            while (oi < o.length) {
              val i = o(oi-1)
              val j = o(oi)-1
              val ha = (y(lins(i).packed.i0) + y(lins(j).packed.i1))*0.5f
              var steep = -1f
              var t = -1f
              var k = i
              while (k <= j) {
                val w = lins(k).packed
                if (steep < 0 && k>i && (y(lins(k-1).packed.i1) - ha)*(ha - y(w.i0)) >= 0) { t = 0.5f*(w.i0 + lins(k-1).packed.i1); steep = 0 }
                if (math.abs(slopes(k)) > steep && (y(w.i1) - ha)*(ha - y(w.i0)) >= 0) {
                  steep = math.abs(slopes(k))
                  t = (if (y(w.i1) == y(w.i0)) 0.5f*(w.i0+w.i1) else w.i0 + (w.i1-w.i0)*(ha-y(w.i0))/(y(w.i1)-y(w.i0))).toFloat
                }
                k += 1
              }
              p(oi-1) = (if (t >= lins(i).packed.i0 && t <= lins(j).packed.i1) t else 0.5f*(lins(i).packed.i0 + lins(j).packed.i1))
              //q(oi-1) = dir*((oi&1)*2-1) > 0
              oi += 1
            }
            
            val r = new Array[Float](math.max(0, p.length-1))
            val s = new Array[Float](math.max(0, p.length-1))
            var i = m.i0
            var j = 0
            if (j < p.length) i = math.ceil(p(j)).toInt
            while (i <= m.i1) {
              j += 1
              val v = if (j >= p.length) 0 else dir*(((j-1)&1)*2-1) // if (j >= q.length) 0f else if (q(j)) 1 else -1
              val n = if (j >= p.length) m.i1 else math.min(m.i1, math.floor(p(j)).toInt)
              if (j-1 < p.length) i = math.max(i, math.ceil(p(j-1)).toInt)
              var findpt = v != 0
              var yt = if (v>0) y(i) upper y(n) else if (v<0) y(i) lower y(n) else Float.NaN
              var k = n
              var maxwidth = 3+k-i
              while (findpt) {
                if (v > 0) {
                  while (y(i) < yt) i += 1
                  while (y(k) < yt) k -= 1
                }
                else {
                  while (y(i) > yt) i += 1
                  while (y(k) > yt) { k -= 1 }
                }
                val zs = (if (k > i+3) { val p = fitSine(u, y)(i, k); p.f0+p.f1 } else Float.NaN)
                val zm = {
                  var zz = y(i)
                  var h = i+1
                  while (h <= k) {
                    zz = if (v>0) zz upper y(h) else zz lower y(h)
                    h += 1
                  }
                  zz
                }
                val usemax = zs.nan || (1+k-i) > maxwidth || math.abs(zm-yt).sq < noisy.sq/math.max(1+k-i,1)
                val z = (if (usemax) zm else zs)
                if (!usemax && (math.abs(zs-yt) > 1.2*math.abs(zm-yt)  && math.signum(zs-yt) == math.signum(zm-yt))) {
                  maxwidth -= 2
                  yt = 0.5f*(yt+zm)
                }
                else {
                  findpt = false
                  var t = 0L
                  var nt = 0
                  var ti = i
                  while (ti <= k) { if (v*(y(ti)-z) >= 0) { t += ti; nt += 1 }; ti += 1 }
                  r(j-1) = z
                  s(j-1) = if (nt==0) 0.5f*(i+k) else (t.toDouble/nt).toFloat
                }
              }
              i = n+1
            }
            /*
            if (d.ID==1) {
              println
              println(o.mkString(" "))
              println
              println(p.mkString(" "))
              println
              println(s.mkString(" "))
              println
              println(r.mkString(" "))
              println
            }
            */
            val ws = new Array[Wiggle](r.length+1)
            ws(0) = {
              if (r.length==0) Wiggle(m.i0, m.i1, m.i0, m.i1, y(m.i0), y(m.i1), Float.NaN, Float.NaN)
              else Wiggle(m.i0, math.floor(s(0)).toInt, m.i0, s(0), y(m.i0), r(0), Float.NaN, 0)
            }
            if (r.length > 0) ws(ws.length-1) = Wiggle(math.floor(s(s.length-1)).toInt+1, m.i1, s(s.length-1), m.i1, r(s.length-1), y(m.i1), 0, Float.NaN)
            i = 1
            while (i < r.length) {
              ws(i) = Wiggle(math.floor(s(i-1)).toInt+1, math.floor(s(i)).toInt, s(i-1), s(i), r(i-1), r(i), 0, math.Pi.toFloat)
              i += 1
            }
            
            m match {
              case Advance(i0, i1, s0, s1) => Fore(i0, i1, s0, s1)(m.fwd, m.pts, m.ds, ws)
              case Retreat(i0, i1, s0, s1) => Back(i0, i1, s0, s1)(m.fwd, m.pts, m.ds, ws)
            }
          case x => x
        },
        noisy
      )
    }
  }
  
  case class GoF(unfit: Float, fit: Float, wigfit: Float, backfit: Float, ranfit: Float, shape: Array[Float]) {}
  def estimateNosePrediction(d: Dance, m: MovChunk, ang: Angler, sigma: Float): Option[GoF] = {
    if (m.ds(m.ds.length-1)-m.ds(0) < 20*d.body_length.average || !m.isInstanceOf[WigChunk]) None
    else {
      val wig = (m.asInstanceOf[WigChunk]).wig
      val i0 = m.i0
      val i1 = m.i1
      val anglet = ang.slice(i0,i1)
      val fitlet = new Array[Float](anglet.track.length)
      val at0 = anglet.track(0)
      val at1 = anglet.track(anglet.track.length-1)
      val af = 1f/(i1-i0)
      var i, iw = 0
      while (i < anglet.track.length) {
        while (iw < wig.length && wig(iw).t1 < i+i0) iw += 1
        val delta = at0 + (at1-at0)*af*i
        anglet.track(i) -= delta
        anglet.head(i) -= delta
        fitlet(i) = wig(iw).y(i+i0) - delta
        if (fitlet(i).nan) fitlet(i) = anglet.head(i)
        i += 1
      }
      val bits = anglet.findSwings(sigma)
      val inc = (d.body_length.average*0.05).toFloat
      var indices = new Array[Float]((math.ceil(m.ds(m.ds.length-1)-m.ds(0))/inc).toInt)
      i = 0
      var fi = 0f
      while (i < indices.length && !isNaN(fi)) {
        indices(i) = fi
        fi = stepAhead(m.ds, inc, fi)
        i += 1
      }
      val n = (if (isNaN(fi)) i-1 else i)
      i = 0
      while (i<n && indices(i) < bits(0).i0) i += 1
      val (rebit, lo, hi) = bits.map{ es =>
        val j0 = i
        var j = i
        while (j<n && indices(j) < es.i1) j += 1
        i = j
        if (j>1) Foray(j0,j-1,es.sign) else Foray(j0,j-1,0)
      }.compacted{ (l,r) =>
        if (l.sign==0) Some(r)
        else if (r.sign==0) Some(l)
        else if (l.sign==r.sign) Some(Foray(l.i0, r.i1, l.sign))
        else None
      }.filter(r => r.i0 > 60 && r.i1 < indices.length-60).zap{ r => (
          r.map{ case Foray(a,b,c) => Foray(a-r(0).i0, b-r(0).i0, c) },
          if (r.length > 0) r(0).i0 else 0,
          if (r.length > 0) r(r.length-1).i1 else 0
        )
      }
      if (rebit.length==0 || rebit(0).sign==0) None
      else {
        val nn = 1+hi-lo
        val backtrack, backhead, smalltrack, smallhead, smallfit, smalldiff = new Array[Float](nn)
        i = 0
        while (i < nn) {
          smalltrack(i) = subindexF(anglet.track, indices(i+lo))
          backtrack(nn-i-1) = smalltrack(i)
          smallhead(i) = subindexF(anglet.head, indices(i+lo))
          backhead(nn-i-1) = smallhead(i)
          smallfit(i) = subindexF(fitlet, indices(i+lo))
          smalldiff(i) = smallhead(i) - smalltrack(i)
          i += 1
        }
        val hfilt = optimalLinearFilter(smallhead, smalltrack, 40)
        val hest = convolveFilter(smallhead, hfilt)
        val bfilt = optimalLinearFilter(backhead, backtrack, 40)
        val best = convolveFilter(backhead, bfilt)
        val ffilt = optimalLinearFilter(smallfit, smalltrack, 40)
        val fest = convolveFilter(smallfit, ffilt)
        var rms,rmsh,rmsb,rmsf,rmsr = 0.0
        i = hfilt.length
        while (i < hest.length-hfilt.length) {
          rms += (smallhead(i)-smalltrack(i)).sq
          rmsh += (hest(i)-smalltrack(i)).sq
          rmsf += (fest(i)-smalltrack(i)).sq
          rmsb += (best(i)-backtrack(i)).sq
          i += 1
        }
        /*
        if (d.ID==1) {
          println
          println(smalltrack.mkString(" "))
          println
          println(smallhead.mkString(" "))
          println
          println(smallfit.mkString(" "))
          println
          println(hest.mkString(" "))
          println
          println(fest.mkString(" "))
          println
          println(best.mkString(" "))
          println          
        }
        */
        var h = 0
        while (h < 20) {
          Rng.permuteInPlace(rebit, rng, 0, rebit.length-1, 2)
          Rng.permuteInPlace(rebit, rng, 1, rebit.length-1, 2)
          val oneran = shuffle(smalldiff, rebit)
          i = 0
          while (i < oneran.length) { oneran(i) += smalltrack(i); i += 1 }
          val rfilt = optimalLinearFilter(oneran, smalltrack, 40)
          val rest = convolveFilter(oneran, rfilt)
          i = rfilt.length
          while (i < rest.length - rfilt.length) { rmsr += (rest(i)-smalltrack(i)).sq; i += 1 }
          h += 1
        }
        rmsr /= h
        Some(GoF(math.sqrt(rms).toFloat, math.sqrt(rmsh).toFloat, math.sqrt(rmsf).toFloat, math.sqrt(rmsb).toFloat, math.sqrt(rmsr).toFloat, hfilt))
      }
    }
  }
  
  def quantityIsNosePhase(d: Dance): (Array[(Array[Float], Array[Float])]) = {
    java.util.Arrays.fill(d.quantity, Float.NaN)
    if (!(tracks contains d.ID)) return Array()
    val angs = angles(d.ID)().head
    val times = java.util.Arrays.copyOfRange(chore.times, d.first_frame, d.last_frame+1)
    Array()
    /*
    tracks(d.ID)().parts.collect{ case wc: WigChunk =>
      val ob = Array.newBuilder[Int]
      var dir = 0
      ob += 0
      wc.wig().forpieces(w => w.change < 0){ (_, b, _, j) => if (dir==0) { dir = if (b) 1 else -1 }; ob += j }
      val o = ob.result
      val p = new Array[Float](math.max(0,o.length-1))
      val q = new Array[Boolean](p.length)
      var oi = 1
      while (oi < o.length) {
        val i = o(oi-1)
        val j = o(oi)-1
        val ha = (angs(wc.wig(i).i0) + angs(wc.wig(j).i1))*0.5f
        var steep = -1f
        var t = -1f
        var k = i
        while (k <= j) {
          val w = wc.wig(k)
          if (steep < 0 && k>i && (angs(wc.wig(k-1).i1) - ha)*(ha - angs(w.i0)) >= 0) { t = 0.5f*(w.i0 + wc.wig(k-1).i1); steep = 0 }
          if (math.abs(w.change) > steep && (angs(w.i1) - ha)*(ha - angs(w.i0)) >= 0) {
            steep = math.abs(w.change)
            t = (if (angs(w.i1)==angs(w.i0)) 0.5f*(w.i0+w.i1) else w.i0 + (w.i1-w.i0)*(ha-angs(w.i0))/(angs(w.i1)-angs(w.i0))).toFloat
          }
          k += 1
        }
        p(oi-1) = (if (t >= wc.wig(i).i0 && t <= wc.wig(j).i1) t else 0.5f*(wc.wig(i).i0 + wc.wig(j).i1))
        q(oi-1) = dir*((oi&1)*2-1) > 0
        oi += 1
      }
      val r = new Array[Float](math.max(0, p.length-1))
      val s = new Array[Float](math.max(0, p.length-1))
      var i = wc.i0
      var j = 0
      if (j < p.length) while (i < p(j)) { d.quantity(i) = 0f; i += 1 }
      while (i <= wc.i1) {
        j += 1
        val v = if (j >= q.length) 0f else if (q(j)) 1 else -1
        val n = if (j >= p.length) wc.i1 else math.min(wc.i1, math.floor(p(j)).toInt)
        if (j-1 < p.length) i = math.max(i, math.ceil(p(j-1)).toInt)
        val xt = if (v>0) math.max(angs(i),angs(n)) else if (v<0) math.min(angs(i),angs(n)) else Float.NaN
        val m = i
        var k = n
        if (v > 0) {
          while (angs(i) < xt) i += 1
          while (angs(k) < xt) k -= 1
        }
        else if (v < 0) {
          while (angs(i) > xt) i += 1
          while (angs(k) > xt) k -= 1
        }
        if (v != 0) {
          val x = if (k > i+3) { val p = fitSine(times, angs)(i, k); p.f0+p.f1 } else { var xx = angs(i); var h = i; while (h <= k) { xx = if (v>0) math.max(xx,angs(h)) else math.min(xx,angs(h)); h += 1 }; xx }
          var t = 0L
          var nt = 0
          var ti = i
          while (ti <= k) { if (v*(angs(ti)-x) >= 0) { t += ti; nt += 1 }; ti += 1 }
          r(j-1) = x
          s(j-1) = if (nt==0) 0.5f*(i+k) else (t.toDouble/nt).toFloat
          k = m
          while (k <= n) { d.quantity(k) = v; k += 1 }
        }
        i = n+1
      }
      if (r.length>40 && d.ID==61) {
        println
        println(s"${d.ID} ${wc.i0} ${wc.i1}")
        println
        wc.wig().foreach{ w => println(s"${w.i0} ${w.i1} ${w.change} ${angs.slice(w.i0,w.i1+1).mkString(" ")}") }
        println
        j = 1
        while (j < r.length) {
          val ia = math.floor(s(j-1)).toInt+1
          val ib = math.floor(s(j)).toInt
          val di = s(j)-s(j-1)
          val io = s(j-1)
          print(angs.slice(ia,ib+1).mkString(""," "," "))
          j += 1
        }
        println; println
        j = 1
        while (j < r.length) {
          val ia = math.floor(s(j-1)).toInt+1
          val ib = math.floor(s(j)).toInt
          val di = s(j)-s(j-1)
          val io = s(j-1)
          print((ia to ib).map(x => math.Pi*(x - io)/di).map(ph => r(j-1) + 0.5*(r(j)-r(j-1))*(1 - math.cos(ph))).map(_.toFloat).mkString(""," "," "))
          j += 1
        }
        println
        println(s"${d.ID} ${wc.i0} ${wc.i1}")
        println(r.mkString(" "))
        println(s.mkString(" "))
        println(p.mkString(" "))
        println(q.mkString(" "))
        println(o.mkString(" "))
        println(wc.wig().map(_.i0).mkString(" "))
        println(wc.wig().map(_.i1).mkString(" "))
        println(wc.wig().map(_.change).mkString(" "))
        println
      }
      (r, p)
    }.toArray
    */
  }
  
  def quantityIsConcentration(d: Dance, L: Double) {
    java.util.Arrays.fill(d.quantity, Float.NaN)
    val conc = concentrator(L.toFloat)
    var i = 0
    while (i < d.centroid.length) { d.quantity(i) = conc(d.centroid(i).vff.repr).toFloat; i += 1 }
  }
  
  def inRange(d: Dance, i: Int): Boolean = {
    val s = quanteans("source")._2
    val r = quanteans("ranges")._2
    if (s(0).finite && s(1).finite && r(0).finite && r(1).finite) {
      val dist = ((d.centroid(i).vff * chore.mm_per_pixel) - (s(0) vff s(1))).len
      r(0) < dist && dist < r(1)
    }
    else true
  }

  

  def prepareDance(d: Dance) {
    if (d.spine eq null) return
    if (d.segmentation eq null) d.findSegmentation
    if (d.segmentation eq null) return
    val unambiguousDist = math.sqrt((d.noise_estimate.average * mwt.numerics.Statistic.invnormcdf_tail(0.05f/d.centroid.length)).zap(x => x*x))
    d.quantityIsBias(chore.times, chore.speed_window, unambiguousDist.toFloat, false)
    nosey(d.ID) = Soft(d)(NoseFitter.fitNoseAndTail)
    angles(d.ID) = Soft(d)(Angler.measureAngles)
    tracks(d.ID) = Soft(d)(x => (new Track(x))().omegize.invalidize.mobilize.directionalize.wiggling)
  }
  
  
  //========================================================================\\
  //            Implementation of the CustomComputation interface           \\
  //========================================================================\\
  def desiredExtension = "ms"
  def validateDancer(d: Dance) = true
  
  def computeAll(f: File) = {
    def reext(s: String) = f.extly(e => postfix.map(_+".").getOrElse("")+s+"."+e)
    
    val danced = chore.dances.filter(_ ne null).tap(_.foreach(prepareDance)).filter(d => nosey contains d.ID)
    if (booleans("nosepredict")) { Print.toFile(reext("nosepred")) { pr => danced.foreach{ d =>
      val ang = angles(d.ID)()
      val trk = tracks(d.ID)()
      trk.parts.collect{ case fo: Fore => estimateNosePrediction(d, fo, ang, trk.headnoise).map(_.also(_ => (fo.i0, fo.i1))) }.flatten.foreach{ case (gof,(i0,i1)) =>
        if (!(gof.fit.nan || gof.backfit.nan || gof.ranfit.nan || gof.unfit.nan || gof.wigfit.nan)) {
          pr.println(s"${d.ID} ${d.t(i0)} ${d.t(i1)} ${gof.fit/gof.unfit} ${gof.wigfit/gof.unfit} ${gof.backfit/gof.unfit} ${gof.ranfit/gof.unfit} ${gof.unfit} "+gof.shape.mkString(" "))
        }
      }
    }}}
    quanteans.get("headpositions").filter(x => x._2(0) < x._2(1)).foreach{ case (_,Array(t0,t1)) => Print.toFile(reext("headpos")) { pr => 
      val hall, hfe, hbs, hbe = new Histribution(math.Pi/59.999, math.Pi/30, 0, 45)
      danced.foreach{ d =>
        val ang = angles(d.ID)()
        val trk = tracks(d.ID)()
        for (i <- trk.parts.indices) { trk.parts(i) match {
          case bk: Back if t0 < d.t(bk.i0) && t1 > d.t(bk.i1) =>
            val x0 = ang.head(bk.i0) - ang.track(bk.i0)
            if (x0.finite && inRange(d,bk.i0)) hbs += math.abs(x0)
            val x1 = ang.head(bk.i1) - ang.track(bk.i1)
            if (x1.finite && inRange(d,bk.i1)) hbe += math.abs(x1)
            if (i>0) trk.parts(i-1) match {
              case fo: Fore if t0 < d.t(fo.i0) && t1 > d.t(fo.i1) =>
                val x = ang.head(fo.i1) - ang.track(fo.i1)
                if (x.finite && inRange(d,fo.i1)) hfe += math.abs(x)
              case _ =>
            }
          case fo: Fore =>
            val n = math.floor(10.0*(fo.ds(fo.ds.length-1) - fo.ds(0))/d.meanBodyLengthEstimate).toInt
            var k = 0
            while (k < n) {
              val i = fo.i0 + rng.roll(fo.length)
              val x = ang.head(i) - ang.track(i)
              if (x.finite && t0 < d.t(i) && t1 > d.t(i) && inRange(d,i)) hall += math.abs(x)
              k += 1
            }
          case _ =>
        }}
      }
      pr.println(hall.toText)
      pr.println(hfe.toText)
      pr.println(hbs.toText)
      pr.println(hbe.toText)
    }}
    if (quanteans("statetimes")._2(0).finite) { Print.toFile(reext("statet")) { pr => danced.foreach{ d =>
      val T = quanteans("statetimes")._2(0)
      tracks.get(d.ID).foreach{ tr => 
        val vt = tr().parts.filter(es => d.t(es.i0) < T)
        if (vt.length>0) pr.println(s"${d.ID} + ${vt.map(es => f"${es.ch} ${d.t(es.i0)} ${d.t(es.i1)-d.t(es.i0)}%.3f").mkString(" ")}")
      }
    }}}
    if (!(descripteans contains "report")) {
      Print.toFile(reext("omg")){pr =>
        danced.foreach{ d => 
          (new Track(d)()).omegize.parts.collect{ case ClosedOmega(i0, i1) => (i0,i1) }.foreach{
            case (i0, i1) => pr.println(f"${d.ID} ${d.t(i0)}%.3f ${d.t(i1)}%.3f")
          }
        }
      }
      Print.toFile(reext("agree")){ pr =>
        var glue = 0.0f
        var idx = 0
        danced.filter(d => tracks contains d.ID).foreach{ d =>
          val dinc = (d.body_length.average * 0.05).toFloat
          val tr = tracks(d.ID)().parts.collect{ case w: WigChunk => w }.filter{ w => math.abs(w.ds(0) - w.ds(w.ds.length-1)) > 10*d.body_length.average }
          val an = angles(d.ID)().body
          val ep = nosey(d.ID)()
          val eph = ep._1
          val ept = ep._2
          tr.foreach{ w =>
            var fi = 0f
            var s = 0
            val o = w.i0
            var thx = glue
            val fix = glue - Vff.from(w.fwd(0)).theta
            while (fi+1 < w.ds.length /* Handles NaN also */) {
              val i = fi.toInt
              while (s+1 < w.fwd.length && d.segmentation(s+w.s0).i1 < o+i) {
                s += 1
                thx = Vff.from(w.fwd(s)).theta-fix angleNear thx
              }
              val ai = subindexF(an, o+fi) - fix angleNear thx
              val hi = subindexVff(j => eph.arrow(o+j).repr, fi).theta - fix angleNear thx
              val ti = subindexVff(j => (-ept.arrow(o+j)).repr, fi).theta - fix angleNear thx
              pr.println(s"$idx $thx $ai $hi $ti")
              fi = stepAhead(w.ds, dinc, fi)
            }
            glue=thx
            idx = (idx+1)&0x7
          }
          idx = (idx+1)&0x7
        }
      }
    }
    else {
      val included = descripteans("report").map(x => if (x.length==0) "bfos" else x).get.toSet
      var counts: Array[Int] = null
      var actions: Array[(Int, Int, Float, Chunk)] = null
      Print.toFile(reext("rpt")){ pr => 
        counts = new Array[Int](chore.times.length)
        actions = danced.flatMap{ d => 
          var j = d.first_frame
          tracks(d.ID)().parts.flatMap{ p => 
            p match {
              case _: ClosedOmega | _: Steady | _: Fore | _: Back => 
                if (j < d.first_frame + p.i0) j = d.first_frame + p.i0
                var n = d.first_frame + p.i1 - j
                while (n >= 0) {
                  counts(j) += 1
                  j += 1
                  n -= 1
                }
                val D = p match {
                  case w: WigChunk => 
                    val x = w.ds(w.ds.length-1) - w.ds(0)
                    math.abs(x)
                  case _ =>
                    d.centroid(p.i0).dist(d.centroid(p.i1))
                }
                Some((d.ID, d.first_frame + p.i0, D, p))
              case _ => 
                None
            }
          } 
        }.sortBy(_._2)
        actions.foreach{
          case (id, i, d, p) if included contains p.ch =>
            pr.println("%.3f %d %s %.3f %.3f %d".format(chore.times(i), id, p.ch.toString, chore.times(i+p.length-1) - chore.times(i), d*chore.mm_per_pixel, counts(i)))
          case _ =>
        }
      }
      if (chore.triggers != null && chore.triggers.length > 0) {
        Print.toFile(reext("trp")){ pr =>
          (chore.trigger_start, chore.trigger_end).zipped.foreach{ (t0, t1) =>
            val i0 = actions.bisectByR(t0)(x => chore.times(x._2))
            val i1 = actions.bisectByL(t1)(x => chore.times(x._2))
            val j0 = chore.times.bisectR(t0)
            val j1 = chore.times.bisectL(t1)
            val n: Float = {
              if (j1==j0) counts(j0)
              else if (j0 < j1) {
                var s = 0
                var j = j0
                while (j <= j1) {
                  s += counts(j)
                  j += 1
                }
                s / (j1-j0+1).toFloat
              }
              else if (j1 >= 0 && j1 < counts.length) counts(j1)
              else if (j0 >= 0 && j0 < counts.length) counts(j0)
              else 0
            }
            included.foreach{ c =>
              val tsd, dsd = AccumulateSD()
              var i = i0
              while (i <= i1) {
                val (id, ix, dd, p) = actions(i)
                if (p.ch == c) {
                  dsd +~ dd
                  tsd +~ (chore.times(ix + p.length - 1) - chore.times(ix))
                }
                i += 1
              }
              pr.println("%.3f %.3f %.2f %s %d %.3f %.3f %.3f %.3f".format(t0, t1-t0, n, c, tsd.n, tsd.mean, tsd.sd, dsd.mean*chore.mm_per_pixel, dsd.sd*chore.mm_per_pixel))
            }
          }
        }
      }
    }
    1
  }
  
  def computeDancerSpecial(d: Dance, f: File) = 0
  def quantifierCount = 6 + (if (booleans("concentrated")) 2 else 0)
  def computeDancerQuantity(d: Dance, which: Int) {
    if (d.quantity==null || d.quantity.length < d.area.length) d.quantity = new Array[Float](d.area.length)
    java.util.Arrays.fill(d.quantity,Float.NaN)
    if (which > 5) quantityIsConcentration(d, if (which==6) Float.NaN else quanteans("diffuse")._2(0))
    else if (which == 5) quantityIsNosePhase(d)
    else if (which >= 4) {
      val ang = angles.get(d.ID).map(_().head)
      tracks.get(d.ID).map(_()).foreach{ t =>
        for (c <- t.parts) c match {
          case w: WigChunk =>
            for (wi <- w.wig) {
              var i = wi.i0
              while (i <= wi.i1) { d.quantity(i) = wi.y(i); i += 1 }
            }
          case c: Chunk => if (c.i1 < c.i0) println(s"What the? ${d.ID} ${c.getClass} ${c.i0} ${c.i1}"); java.util.Arrays.fill(d.quantity, c.i0, c.i1+1, Float.NaN)
        }
      }
    }
    else {
      val oa = angles.get(d.ID).map(_()).map{x => which match { case 0 => x.body; case 1 => x.head; case 2 => x.tail; case 3 => x.track }}
      oa match {
        case Some(a) => System.arraycopy(a, 0, d.quantity, 0, a.length)
        case _ => java.util.Arrays.fill(d.quantity, Float.NaN)
      }
    }
  }
  def quantifierTitle(which: Int) = "indicator"+which//params.custs(which).name
  def modifyQuantity(d: Dance, ds: Choreography.DataSource): Boolean = false
  def modifyMapDisplay(position: mwt.numerics.Vec2D, dimensions: mwt.numerics.Vec2I, pixelSize: Double, buffer: java.awt.image.BufferedImage, time: Double, dm: DataMapper) = if (chore == null) false else {
    val pv = Vff(position.x.toFloat, position.y.toFloat)
    val um_pp = chore.mm_per_pixel * 1e3f
    val ips = (1/pixelSize).toFloat
    lazy val g2 = buffer.getGraphics.asInstanceOf[java.awt.Graphics2D]
    val bg = dm.background_picker.getSelectedItem.asInstanceOf[dm.Backgrounder]
    val high = bg.highlight
    val mid = bg.midlight
    val avg = ((high>>>1)&0x7F7F7F7F) + ((mid>>>1)&0x7F7F7F7F)
    val hiC = new java.awt.Color(high)
    val avgC = new java.awt.Color(avg)
    val midC = new java.awt.Color(mid)
    g2.setColor(avgC)
    chore.dances.filter(_ != null).filter{ d =>
      chore.times(d.first_frame) <= time && chore.times(d.last_frame) >= time
    }.map{ _.also{ d =>
      java.util.Arrays.binarySearch(chore.times, time.toFloat).zap(i => if (i<0) -1 - i else i).zap{ i =>
        if (i>0 && math.abs(chore.times(i)-time)>math.abs(chore.times(i-1)-time)) i-1
        else if (i+1 < chore.times.length && math.abs(chore.times(i)-time)>math.abs(chore.times(i+1)-time)) i+1
        else i
      } - d.first_frame
    }}.foreach{ case (d,i) => if (i >= 0 && i < d.centroid.length) {
      val rr = math.max(1,(5.0/pixelSize).toFloat)
      nosey.get(d.ID).map(_()).foreach{ case (head,tail) =>
        for ((end,brite) <- List((head,true), (tail,false))) {
          val v = (end.trunkv(i)*um_pp - pv) * ips
          if (!v.nan) {
            g2.setColor(if (d.spine(i).oriented) { if (brite) hiC else midC } else avgC)
            val r = end.radius(i) * um_pp * ips
            g2.drawOval(math.round(v.x-r), math.round(v.y-r), math.ceil(2*r).toInt, math.ceil(2*r).toInt)
            val u = (end.tipv(i)*um_pp - pv) * ips
            if (!u.nan) {
              g2.drawLine(math.round(v.x), math.round(v.y), math.round(u.x), math.round(u.y))
              val s = end.errXY * um_pp * ips
              g2.fillOval(math.round(u.x-s), math.round(u.y-s), math.ceil(2*s).toInt, math.ceil(2*s).toInt)
            }
          }
        }
      }
    }}
    true
  }
}
