package ichi.maths

import scala.math._
import ichi.core._
import ichi.maths._

class Histribution(val center: Double, val width: Double, val nL: Int, val nR: Int) {
  def this(n: Int, l: Double, r: Double) = this(0.5*(l+r), (r-l)/((n&(-2))|1), n/2, n/2)
  if (isNaN(center) || isNaN(width) || java.lang.Double.isInfinite(center) || java.lang.Double.isInfinite(width) || !(width>0.0)) {
    throw new IllegalArgumentException("Histributions must have finite center and positive finite width.")
  }
  private[this] val iwidth = 1.0/width
  if (nL<0 || nR<0) throw new IllegalArgumentException("Must have non-negative numbers of left and right bins")
  val counts = new Array[Double](1+nL+nR+3)  // Extra bins on front and back for out-of-range, plus one last bin for NaNs
  var N = 0.0
  @inline def abscissa(i: Int) = center+width*(i-nL-1)
  @inline def i(d: Double) = if (isNaN(d)) counts.length-1 else (round((d-center)*iwidth).toInt+nL+1).bound(0, counts.length-1)
  @inline def value(d: Double) = counts(i(d))
  @inline def validN = N-counts(counts.length-1)
  @inline def niceN = validN-counts(0)-counts(counts.length-2)
  @inline def lo = center - (nL+0.5)*width
  @inline def hi = center + (nR+0.5)*width
  @inline def epsless(a: Double, b: Double) = (a<b) && ((b-a) > java.lang.Math.ulp(b-a)*1e3 + width*1e-10)
  @inline def epsmore(a: Double, b: Double) = (a>b) && ((a-b) > java.lang.Math.ulp(a-b)*1e3 + width*1e-10)
  @inline def epssame(a: Double, b: Double) = abs(a-b) > java.lang.Math.ulp(abs(a-b))*1e3 + width*1e-10
  def clear = { N = 0; java.util.Arrays.fill(counts,0); this }
  def copy = {
    val hs = new Histribution(center, width, nL, nR)
    hs.N = N
    Array.copy(counts, 0, hs.counts, 0, counts.length)
    hs
  }
  def sameShape(hs: Histribution) = (center == hs.center && width == hs.width && nL == hs.nL && nR == hs.nR)
  def +=(d: Double) = { counts(i(d)) += 1; N += 1; this }
  def +=(d: Double, m: Double) = { val i0 = i(d); if (counts(i0)+m >= 0) { counts(i0) += m; N += m } else { N -= counts(i0); counts(i0) = 0 }; this }
  def -=(d: Double) = { val i0 = i(d); if (counts(i0) > 1) { counts(i0) -= 1; N -= 1 } else { N -= counts(i0); counts(i0) = 0 }; this }
  def -=(d: Double, m: Double) = { this += (d,-m) }
  def ++=(ds: Array[Double]) = { var j = 0; while (j<ds.length) { counts(i(ds(j))) += 1; j += 1 }; N += ds.length; this }
  def ++=(ds: Array[Float]) = { var j = 0; while (j<ds.length) { counts(i(ds(j))) += 1; j += 1 }; N += ds.length; this }
  def ++=(hs: Histribution) = {
    if (hs.center==center && hs.width==width) {
      if ((hs.nL < nL && hs.counts(0)>0) || (hs.nR < nR && hs.counts(hs.counts.length-2)>0)) throw new IllegalArgumentException("Cannot sensibly distribute out-of-bounds tails into wider histribution.")
      val delta = nL-hs.nL
      var j = 0
      while (j+delta < 0 && j<hs.counts.length-1) {
        counts(0) += hs.counts(j)
        j += 1
      }
      while (j+delta < counts.length-1 && j<hs.counts.length-1) {
        counts(j+delta) += hs.counts(j)
        j += 1
      }
      while (j<hs.counts.length-1) {
        counts(counts.length-2) += hs.counts(j)
        j += 1
      }
    }
    else {
      if (epsless(lo, hs.lo) || epsmore(hi, hs.hi)) throw new IllegalArgumentException("Cannot sensibly distribute out-of-bounds tails into wider histribution")
      var j = 1
      var k = 0
      val hL = hs.lo
      val hH = hs.hi
      val tL = lo
      var a = hL
      var b = tL
      counts(0) += hs.counts(0)
      while (epsless(a,hH)) {
        var n = hs.counts(j)
        while (!epsmore(b,a)) { k += 1; b = tL + k*width }
        val a1 = hL + (j+1)*hs.width
        while (epsless(a,a1)) {
          val ai = if (epsless(b,a1)) b else a1
          counts(k) += (if (ai==a1) n else { val x = n*(b-a)/(a1-a); n -= x; k += 1; b = tL + k*width; x })
        }
        a = a1
        j += 1
      }
    }
    N += hs.N
    counts(counts.length-1) += hs.counts(hs.counts.length-1)
    this
  }
  def --=(ds: Array[Double]) = { var j = 0; while (j<ds.length) { val i0 = i(ds(j)); if (counts(i0)>1) { counts(i0) -= 1; N -= 1 } else { N -= counts(i0); counts(i0) = 0 } }; this }
  def --=(ds: Array[Float]) = { var j = 0; while (j<ds.length) { val i0 = i(ds(j)); if (counts(i0)>1) { counts(i0) -= 1; N -= 1 } else { N -= counts(i0); counts(i0) = 0 } }; this }
  def --=(hs: Histribution) = {
    hs.N = -hs.N
    var j = 0
    while (j<hs.counts.length) { hs.counts(j) = -hs.counts(j); j += 1 }
    this ++= hs
    j = 0
    while (j<hs.counts.length) { hs.counts(j) = -hs.counts(j); j += 1 }
    hs.N = -hs.N
    this
  }
  def purify = {
    counts(counts.length-1) += counts(0) + counts(counts.length-2)
    counts(0) = 0
    counts(counts.length-2)
    this
  }
  def regularize = {
    counts(counts.length-1) = 0
    var j = 0
    N = 0.0
    while (j<counts.length-1) {
      if (counts(j)<0) counts(j) = 0
      else N += counts(j)
      j += 1
    }
    if (N!=0) {
      val invN = 1.0/N
      var j=0
      while (j<counts.length-1) {
        counts(j) *= invN
        j += 1
      }
      N = 1.0
    }
    this
  }
  def deregularize(n: Double) = {
    N *= n
    var j = 0
    while (j<counts.length) {
      counts(j) *= n
      j += 1
    }
    this
  }
  def scale(c2: Double, w2: Double) = {
    val hs = new Histribution(c2,w2,nL,nR)
    hs.N = N
    var j = 0
    while (j<counts.length) {
      hs.counts(j) = counts(j)
      j += 1
    }
    hs
  }
  def shift(n: Int) = {
    val hs = new Histribution(center+n*width, width, nL, nR)
    hs.N = N
    var j = 0
    while (j< counts.length) {
      hs.counts(j) = counts(j)
      j += 1
    }
    hs
  }
  def collapse(mL: Int, mR: Int)(op: Int => Int) = {
    if (mL < 0 || nL < mL || mR < 0 || mR < nR) throw new IllegalArgumentException("Collapse requires non-negative integer numbers of bins no larger than the original")
    val hs = new Histribution(center, width, mL, mR)
    hs.N = N
    hs.counts(0) = counts(0)
    hs.counts(counts.length-2) = counts(counts.length-2)
    hs.counts(hs.counts.length-1) = counts(counts.length-1)
    var j = 1
    while (j < counts.length-2) {
      val k = op(j-(1+nL))
      hs.counts(k+1+mL) += counts(j)
      j += 1
    }
    hs
  }
  private[this] val addition = (a: Double, b: Double) => a+b
  def product(hs: Histribution)(ctr: Double = center, wid: Double = width, mL: Int = nL, mR: Int = nR)(op: (Double,Double) => Double = addition) = {
    val p = new Histribution(ctr, wid, mL, mR)
    p.counts(p.counts.length-1) = counts(counts.length-1)*hs.N + (N-counts(counts.length-1))*hs.counts(hs.counts.length-1) + counts(0)*hs.counts(hs.counts.length-2) + counts(counts.length-2)*hs.counts(0)
    p.counts(0) += counts(0)*(hs.N - hs.counts(hs.counts.length-1) - hs.counts(hs.counts.length-2)) + hs.counts(0)*(N - counts(0) - counts(counts.length-1) - counts(counts.length-2))
    p.counts(p.counts.length-2) += counts(counts.length-2)*(hs.N - hs.counts(hs.counts.length-1) - hs.counts(0)) + hs.counts(hs.counts.length-2)*(N - counts(0) - counts(counts.length-1) - counts(counts.length-2))
    p.N = p.counts(0) + p.counts(p.counts.length-2) + p.counts(p.counts.length-1)
    var j=1
    while (j<counts.length-2) {
      var k = 1
      while (k<hs.counts.length-2) {
        p += (op(center + (j-1-nL)*width, hs.center + (k-1-hs.nL)*hs.width), counts(j)*hs.counts(k))
        k += 1
      }
      j += 1
    }
    p
  }
  def remap(hss: Array[Histribution])(ctr: Double = center, wid: Double = width, mL: Int = nL, mR: Int = nR)(op: (Double,Double) => Double = addition) = {
    if (hss.length < counts.length-3) throw new IllegalArgumentException("Remap requires at least as many entries as are in-range in original distribution")
    if (N <= 0) this.copy else {
      val invN = 1.0/N
      val p = new Histribution(ctr, wid, mL, mR)
      p.counts(p.counts.length-1) = counts(counts.length-1)*invN
      p.counts(p.counts.length-2) = counts(counts.length-2)*invN
      p.counts(0) = counts(0)*invN
      var j = 1
      while (j < counts.length-2) {
        val h = hss(j-1)
        val f = counts(j)*invN
        if (h.N > 0) {
          val invNh = 1.0/N
          p.counts(p.counts.length-1) = f*h.counts(h.counts.length-1)*invNh
          p.counts(p.counts.length-2) = f*h.counts(h.counts.length-2)*invNh
          p.counts(0) = f*h.counts(0)*invNh
          var k = 1
          while (k < h.counts.length) {
            p += (op(center + (j-1-nL)*width, h.center + (k-1-h.nL)*h.width), f*h.counts(k)*invNh)
            k += 1
          }
        }
        j += 1
      }
      p
    }
  }
  def mean = {
    val n = niceN
    if (n==0) Double.NaN else {
      var s = 0.0
      var j = 1
      while (j < counts.length-2) {
        s += counts(j)*(center + (j-nL-1)*width)
        j += 1
      }
      s/niceN
    }
  }
  def std = if (niceN == 0) StdDev(0,Double.NaN,Double.NaN) else {
    var w,a,q = 0.0
    var j = 1
    var nz = 0
    while (j < counts.length-2) {
      if (counts(j)>0) {
        val xj = (center + (j-nL-1)*width)
        val aj = a + (counts(j)/(w+counts(j)))*(xj - a)
        q += counts(j)*(xj-a)*(xj-aj)
        a = aj
        w += counts(j)
        nz += 1
      }
      j += 1
    }
    StdDev(math.ceil(w).toInt, a, math.sqrt((q*nz)/(w*(nz-1))))
  }
  def icdf(p: Double, allowOutOfBounds: Boolean = false): Double = {
    val n = if (allowOutOfBounds) validN else niceN
    if (n==0) return Double.NaN
    val invN = 1.0/n
    if (allowOutOfBounds) {
      if (p < counts(0)*invN) return Double.NegativeInfinity
      if (p > counts(counts.length-2)*invN) return Double.PositiveInfinity
    }
    var c = if (allowOutOfBounds) counts(if (p>0.5) counts.length-2 else 0)*invN else 0.0
    var j = (if (p>0.5) counts.length-3 else 1)
    var dj = if (p>0.5) -1 else 1
    val q = if (p>0.5) 1.0-p else p
    while (c < q) {
      val dq = counts(j)*invN
      if (c+dq >= q) {
        val f = (q-c)/dq
        return (center + (j-nL-1+(f-0.5)*dj)*width)
      }
      c += dq
      j += dj
    }
    if (p>0.5) Double.NegativeInfinity else Double.PositiveInfinity
  }
  def cdf(x: Double, allowOutOfBounds: Boolean = true): Double = {
    val n = if (allowOutOfBounds) validN else niceN
    if (n==0) return Double.NaN
    val ix = i(x)
    var s = 0.0
    if (ix <= counts.length-2-ix) {
      var j = if (allowOutOfBounds) 0 else 1
      while (j<ix) {
        s += counts(j)
        j += 1
      }
      s += counts(ix)*(x/width - (center + (ix-nL-1.5)))
    }
    else {
      var j = counts.length - (if (allowOutOfBounds) 2 else 3)
      while (j>ix) {
        s += counts(j)
        j -= 1
      }
      s += counts(ix)*((center + (ix-nL-0.5))-x/width)
    }
    s/n
  }
  def pdf(x: Double, allowOutOfBounds: Boolean = true): Double = {
    val n = if (allowOutOfBounds) validN else niceN
    if (n==0) return Double.NaN
    val ix = i(x)
    if (allowOutOfBounds || (ix>0 && ix<counts.length-2)) counts(ix)/n else Double.NaN
  }
  def median(allowOutOfBounds: Boolean = false) = icdf(0.5)
  def fwhm(allowOutOfBounds: Boolean = false) = icdf(0.75)-icdf(0.25)
  def kolmogorovSmirnovMC(hs: Histribution)(samples: Int = 10000): StdDev = {
    if (!sameShape(hs) || N==0 || hs.N==0) throw new IllegalArgumentException("Compared histributions must be the same shape and nonempty")
    if (samples < 100) throw new IllegalArgumentException("Monte carlo sampling is meaningless with less than 100 samples--what are you doing?!")
    val deltas = new Array[Double](counts.length-1)
    var j = 0
    while (j<counts.length-1) {
      deltas(j) = counts(j)/N - hs.counts(j)/hs.N
      j += 1
    }
    var D, Dmax = 0.0
    j = 0
    while (j < deltas.length) {
      D += deltas(j)
      if (math.abs(D) > Dmax) Dmax = math.abs(D)
      j += 1
    }
    val rng = Rng.Lcg32().seedI(scala.util.Random.nextInt)
    var n = 0
    var k = 0
    while (n < samples) {
      j = 0
      D = 0
      while (j < deltas.length) {
        if ((rng.nextInt&0x2000000)==0) deltas(j) = -deltas(j)
        D += deltas(j)
        j += 1
      }
      val fix = -D/deltas.length  // Needs to sum to zero over entire distribution
      j = 0
      D = 0
      var Dm = 0.0
      while (j < deltas.length) {
        D += deltas(j)+fix
        if (math.abs(D) > Dm) Dm = math.abs(D)
        j += 1
      }
      if (Dm > Dmax) k += 1
      n += 1
    }
    StdDev.bayes(k,n-k)
  }
  def foreach(f: (Double,Double) => Unit) {
    var i = 1
    while (i < counts.length-2) {
      f(abscissa(i), counts(i))
      i += 1
    }
  }
  def incorporate(hs: Histribution)(f: (Double,Double) => Double) = {
    if (!sameShape(hs)) throw new IllegalArgumentException("Incorporated histribution must be the same shape")
    var i = 0
    while (i < counts.length) {
      counts(i) = f(counts(i),hs.counts(i))
      i += 1
    }
    this
  }
  override def toString = "Histribution(%f,%f,%d,%d){...}".format(center,width,nL,nR)
  def toText = "Histribution(%f,%f,%d,%d){%f}".format(center,width,nL,nR,N)+counts.mkString("[", ",", "]")
}
object Histribution {
  val HistributionRegex = """Histribution\(([-+eE\.0-9]+) *, *([-+eE\.0-9]+) *, *(\d+) *, *(\d+)\)\{([^\}]+)\}\[([-+eE\., 0-9]+)\]""".r
  def parse(s: String): Option[Histribution] = s match {
    case HistributionRegex(c,w,l,r,n,xs) => try {
        val hs = new Histribution(c.toDouble, w.toDouble, l.toInt, r.toInt)
        hs.N = n.toDouble
        val ys = xs.split(',').map(_.toDouble)
        if (ys.length != hs.counts.length) throw new IllegalArgumentException("Wrong number of parameters for count histogram (should be %d, was %d)".format(hs.counts.length, ys.length))
        for (i <- hs.counts.indices) hs.counts(i) = ys(i)
        Some(hs)
      } catch { case e: Exception => None }
    case _ => None
  }
  /*case class ConfidenceBCa(hist: Histribution, bias: Double, accel: Double) {
    def corrected(p: Double) = { val z = icdfNormal(p); cdfNormal(bias + (bias+z)/(1-accel*(bias+z))) }
    def icdf(p: Double) = hist.icdf(corrected(p))
  }*/
  
  def bootstrap(xs: Array[Float], nbin: Int = 100) = {
    val xav = xs.sum/xs.length
    val x0 = xs.min - xav
    val x1 = xs.max - xav
    val w = (x1-x0)/nbin
    val h = new Histribution(0, w, math.ceil(-x0/w).toInt+1, math.ceil(x1/w+1).toInt+1)
    var i = 0; while (i<xs.length) { h += xs(i)-xav; i += 1 }
    h.regularize
    val hs = Iterator.iterate((h,1,1)){ case (hx,i,p) => 
      ((if (i%2==1) hx.product(hx)(0,hx.width,hx.nL*2,hx.nR*2)() else hx.product(hx)(0,2*hx.width,hx.nL,hx.nR)()).regularize, i+1, p*2)
    }.takeTo(_._3*2 > xs.length).toArray.reverse
    /*val H = */
    ((xs.length, Vector.empty[Histribution]) /: hs){ case ((l,v),(h,i,p)) => if (p<=l) (l-p,h +: v) else (l,v) }._2.reduceLeft{ (l,r) =>
      r.product(l)(0, r.width, r.nL+math.round(l.nL*l.width/r.width).toInt, l.nR+math.round(l.nR*l.width/r.width).toInt)()
    }
    /*if (raw) ConfidenceBCa(H,0,0) else {
      val z0 = icdfNormal( H.cdf(0) )
      val a = {
        var cub,sqr = 0.0
        var i = 0
        while (i < xs.length) {
          val q = xs(i) - xav
          sqr += q*q
          cub += q*q*q
          i += 1
        }
        cub/(6.0*sqr*math.sqrt(sqr))
      }
      ConfidenceBCa(H,z0,a)
    }*/
  }
}


