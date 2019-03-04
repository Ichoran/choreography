package mwt.plugins

import java.io._
import java.lang.Math._

import scala.language.postfixOps

import scala.annotation.tailrec

import mwt._
import mwt.numerics._
import mwt.plugins._

import kse.flow._
import kse.coll.packed._
import kse.maths._
import kse.maths.stats._
import kse.maths.fits._

final class Stabilize extends CustomSegmentation {
  private[this] var chore: Choreography = null
  private[this] var timeAv: Float = Float.NaN
  private[this] var numAv: Int = Int.MaxValue

  def printHelp() {
    println("Usage: --plugin mwt.plugins.Stabilize")
    println("  Stabilize tries to heal path segments disrupted by vibration")
  }

  def initialize(args: Array[String], theChore: Choreography) {
    chore = theChore
    if (args != null && args.length != 0) { println("Stabilize does not use arguments.\n"); printHelp(); throw new CustomHelpException }
  }
  def computeAll(x$1: java.io.File): Int = 0
  def computeDancerQuantity(x$1: mwt.Dance,x$2: Int): Unit = throw new IllegalArgumentException("Stabilize has no quantities")
  def computeDancerSpecial(x$1: mwt.Dance,x$2: java.io.File): Int = 0
  def desiredExtension(): String = ""
  def quantifierCount(): Int = 0
  def quantifierTitle(x$1: Int): String = throw new IllegalArgumentException("Stabilize has no quantities and thus has no titles")
  def validateDancer(x$1: mwt.Dance): Boolean = true

  private[this] def crisp(style: Style): Boolean = style.kind match {
    case Style.Styled.Straight | Style.Styled.Arc => true
    case _ => false
  }
  private[this] def called(style: Style): Boolean = style.kind match {
    case Style.Styled.Weird | Style.Styled.Clutter => false
    case _ => true
  }

  private[this] def findExplanatoryDrifts(id: Int, t: Array[Float], c: Array[Vec2F], s: Array[Style]): Array[Long] = {
    // Constants of 5 all over the place are that we need at least 5 points outside a cluster
    val explained = Array.newBuilder[Long]
    var i, ii = 0
    while (i < s.length) {
      val si = s(i)
      if (!crisp(si)) {
        ii = i - 1; while (ii > 0 && !crisp(s(ii)) && si.i0 - s(ii).i0 < 5) ii -= 1
        val ci0 =
          if (ii < i && ii >= 0 && !crisp(s(ii)) && (si.i0 - s(ii).i1) < 5) s(ii).i0
          else if (ii < i && ii >= 0) s(ii).i1+1
          else si.i0
        ii = i + 1; while (ii+1 < s.length && !crisp(s(ii)) && s(ii).i1 - si.i1 < 5) ii += 1
        val ci1 =
          if (ii > i && ii < s.length && !crisp(s(ii)) && (s(ii).i0 - si.i1) < 5) s(ii).i1
          else if (ii > i && ii < s.length) s(ii).i0-1
          else si.i1
        if (si.i0 - ci0 >= 5 && ci1 - si.i1 >= 5) {
          // First find the center of everything; measure relative to there
          val et, ex, ey = EstM.empty
          var ci = ci0
          while (ci <= ci1) {
            val cvi = c(ci)
            if (cvi != null && cvi.x.finite && cvi.y.finite) {
              et += t(ci)
              ex += cvi.x
              ey += cvi.y
            }
            ci += 1
          }
          val txy = new FitTXY
          if (et.n > 2*5 + 2) {
            // Number of points are reasonable
            txy.origin(et.mean, ex.mean, ey.mean)
            ci = ci0
            while (ci <= ci1) {
              val cvi = c(ci)
              if (cvi != null && cvi.x.finite && cvi.y.finite) txy += (t(ci), cvi.x, cvi.y)
              ci += 1
            }
            var sse = 0.0
            var n = 0
            var ssp = 0.0
            var pn = 0
            var tmin = Double.NaN
            var tmax = Double.NaN
            ci = ci0
            ii = i; while (ii > 0 && s(ii).i0 > ci) ii -= 1
            ex.reset
            ey.reset
            while (ci < ci1) {
              val cvi = c(ci)
              if (cvi != null && cvi.x.finite && cvi.y.finite) {
                ex += cvi.x
                ey += cvi.y
                sse += (cvi.x - txy.xt(t(ci))).sq + (cvi.y - txy.yt(t(ci))).sq
                val estt = txy.inverse(cvi.x, cvi.y)
                if (!tmin.finite) {
                  tmin = estt
                  tmax = estt
                }
                else if (tmin > estt) tmin = estt
                else if (tmax < estt) tmax = estt
                n += 1
              }
              ci += 1
              if (ci == s(ii).i0 || ci == s(ii).i1+1) {
                ssp += ex.sse + ey.sse
                ex.reset
                ey.reset
                pn += 1
              }
              if (ci == s(ii).i1+1 && ii+1 < s.length) ii += 1
            }
            val lineerr = sse/max(1, n-2)
            val pointerr = ssp/max(1, n-pn)
            val ratio = if (pointerr > 0) lineerr/pointerr else 1
            val threshold = 1.0 - 1/max(1, n-max(2, pn)).sqrt   // Favor multiple points somewhat
            if (pointerr > 0 && ratio < threshold && ((t(ci1) - t(ci0))/(tmax-tmin)).in(2/3.0, 3/2.0)) {
              // Rough estimate of 2 SD improvement in error, assuming random errors
              explained += (ci0 <> ci1).L
            }
          }
        }
      }
      i += 1
    }
    explained.result
  }

  /*
  private[this] def checkReasonable(id: Int, ijs: Array[Long], prefix: String, ref: Array[Long] = null) {
    if (id == 29075) {
      var i = 0
      while (i < ijs.length) {
        val ij = ijs(i).asInts
        if (ij.i1 - ij.i0 < 4) {
          val b4 =
            if (i == 0) ""
            else {
              val ijl = ijs(i-1).asInts
              s" <<${ijl.i0}-${ijl.i1}"
            }
          val af =
            if (i+1 == ijs.length) ""
            else {
              val ijr = ijs(i+1).asInts
              s" >>${ijr.i0}-${ijr.i1}"
            }
          println(s"Oop! $prefix $i ${ij.i0} ${ij.i1}$b4$af")
          if (ref != null) println(s"     from ${ref(i).asInts.i0}-${ref(i).asInts.i1}")
        }
        i += 1
      }
    }
  }
  */

  private[this] def resegmentRanges(id: Int, t: Array[Float], c: Array[Vec2F], ijs: Array[Long], store: Style => Unit) {
    var k = 0
    while (k < ijs.length) {
      val ij = ijs(k).asInts
      val s = new Style(c, Style.Styled.Straight, ij.i0, ij.i1, (new Fitter()).tap(_.shiftZero(true)))
      var i = ij.i0; while (i <= ij.i1) i = s.addRight(i)
      s.fit.line.fit();  // Fit is normally completed, so do it here
      store(s)
      k += 1
    }
  }

  private[this] def resegmentSomeDrifters(
    id: Int, t: Array[Float], c: Array[Vec2F],
    s: Array[Style], si: Int,
    ij: Array[Long], k0: Int, k1: Int,
    store: Style => Unit
  ): Int = {
    var lefts: List[Int] = Nil
    var rights: List[Int] = Nil
    var l0 = k0; while (l0 < k1 && ij(l0+1).asInts.i0 == ij(l0).asInts.i0) l0 += 1
    var l1 = k1; while (l1 > k0 && ij(l1-1).asInts.i1 == ij(l1).asInts.i1) l1 -= 1
    def tL() = if (lefts.isEmpty) 0 else t(ij(lefts.head).asInts.i1) - t(ij(k0).asInts.i0)
    def tR() = if (rights.isEmpty) 0 else t(ij(k1).asInts.i1) - t(ij(rights.head).asInts.i0)
    while (l0 <= l1) {
      if (lefts.isEmpty || (rights.nonEmpty && tL() <= tR())) {
        // Pick next thing for left
        lefts = l0 :: lefts
        l0 += 1; while (l0 <= l1 && ij(l0).asInts.i0 < ij(lefts.head).asInts.i1) l0 += 1
      }
      else {
        rights = l1 :: rights
        l1 -= 1; while (l1 >= l0 && ij(l1).asInts.i1 > ij(rights.head).asInts.i0) l1 -= 1
      }
    }
    val lij = if (lefts.nonEmpty)  ij(lefts.head).asInts  else -1 <> -1
    val rij = if (rights.nonEmpty) ij(rights.head).asInts else -1 <> -1
    val total: Array[Long] = 
      if (lefts.isEmpty) Array((ij(k0).asInts.i0 <> ij(k1).asInts.i1).L)
      else if (rights.isEmpty) Array(ij(lefts.head))
      else if (lij.L == rij.L) (lefts.tail.reverse ::: rights).toArray.map(ix => ij(ix))
      else if ((rij.i0 - lij.i1).in(-1, 1)) (lefts.reverse ::: rights).toArray.map(ix => ij(ix))
      else if ((rij.i0 < lij.i1)) {
        // Overlap
        if (rij.i1 - lij.i1 < 5 || rij.i0 - lij.i0 < 5) {
          // Combine into a single segment
          val lhs = lefts.tail.reverse.map(ix => ij(ix)).toArray
          val rhs = rights.tail.reverse.map(ix => ij(ix)).toArray
          lhs ++ Array[Long]((min(lij.i0, rij.i0) <> max(lij.i1, rij.i1)).L) ++ rhs
        }
        else {
          // Pare back existing segments
          var lij1 = lij.i1
          var rij0 = rij.i0
          while (lij1 >= rij0) {
            if (t(lij1) - t(lij.i0) > t(rij.i1) - t(rij0)) lij1 -= 1
            else rij0 += 1
          }
          val lhs = lefts.tail.reverse.map(ix => ij(ix)).toArray
          val rhs = rights.tail.reverse.map(ix => ij(ix)).toArray
          lhs ++ Array(lij.i1To(lij1).L, rij.i0To(rij0).L) ++ rhs
        }
      }
      else {
        // Gap
        if (rij.i0 - lij.i1 >= 5+2) {
          // Create new segment
          val lhs = lefts.reverse.map(ix => ij(ix)).toArray
          val rhs = rights.map(ix => ij(ix)).toArray
          lhs ++ Array(((lij.i1 + 1) <> (rij.i0 -1)).L) ++ rhs
        }
        else {
          // Absorb into existing segments
          var lij1 = lij.i1
          var rij0 = rij.i0
          while (lij1+1 < rij0) {
            if (t(lij1) - t(lij.i0) > t(rij.i1) - t(rij0)) rij0 -= 1
            else lij1 += 1
          }
          val lhs = lefts.tail.reverse.map(ix => ij(ix)).toArray
          val rhs = rights.tail.reverse.map(ix => ij(ix)).toArray
          lhs ++ Array(lij.i1To(lij1).L, rij.i0To(rij0).L) ++ rhs
        }
      }
    // Clumsily heal inaccurate joins (really shouldn't create them in the first place...)
    var i = 1
    while (i < total.length) {
      if (total(i).asInts.i0 != total(i-1).asInts.i1+1) {
        var r = total(i).asInts
        var l = total(i-1).asInts
        while (r.i0 - l.i1 < 1) {
          if (t(l.i1) - t(l.i0) > t(r.i1) - t(r.i0)) l = l.i1To(l.i1 - 1)
          else r = r.i0To(r.i0 + 1)
        }
        while (r.i0 - l.i1 > 1) {
          if (t(l.i1) - t(l.i0) > t(r.i1) - t(r.i0)) r = r.i0To(r.i0 - 1)
          else l = l.i1To(l.i1 + 1)
        }
        total(i-1) = l.L
        total(i) = r.L
      }
      i += 1
    }
    resegmentRanges(id, t, c, total, store)
    var k = si
    while (k < s.length && s(k).i0 - total.last.asInts.i1 < 0) k += 1
    k
  }

  private[this] def resegmentDrifters(id: Int, t: Array[Float], c: Array[Vec2F], s: Array[Style], ij: Array[Long]): Array[Style] =
    if (ij.length == 0) s
    else {
      val reseg = Array.newBuilder[Style]
      var si = 0
      var k = 0
      while (k < ij.length) {
        var l = k
        while (l+1 < ij.length && ij(l+1).asInts.i0 - ij(l).asInts.i1 < 2) l += 1
        while (si < s.length && s(si).i1 <= ij(k).asInts.i0) { reseg += s(si); si += 1 }
        si = resegmentSomeDrifters(id, t, c, s, si, ij, k, l, (x: Style) => {reseg += x})
        k = l+1
      }
      while (si < s.length) { reseg += s(si); si += 1 }
      reseg.result
    }

  def resegment(d: Dance, segmentation: Array[Style], credibleDistSq: Double): Array[Style] = {
    if (segmentation == null || segmentation.length < 3) return segmentation
    val c = d.centroid
    val t = java.util.Arrays.copyOfRange(chore.times, d.first_frame, d.first_frame + c.length)
    val improvements = findExplanatoryDrifts(d.ID, t, c, segmentation)
    if (improvements.isEmpty) segmentation
    else resegmentDrifters(d.ID, t, c, segmentation, improvements)
  }
}
