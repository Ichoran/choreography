package ichi.mwt

import language.implicitConversions

import scala.annotation.tailrec
import scala.reflect.ClassTag

import ichi.maths._
import ichi.maths.PackedMaths._
import ichi.shipvl.ShipVL._

package object mwtmath {
  // Vector interoperability with MWT
  implicit class VectorInterop(val v: mwt.numerics.Vec2F) extends AnyVal {
    def imm = IVec2F(v.x, v.y)
    def mut = MVec2F(v.x, v.y)
    def vff = Vff(v.x, v.y)
    def ~~>(m: AVec2F) = { m.x = v.x; m.y = v.y; m }
  }
  implicit class VectorInterop2(val v: GVec2F) extends AnyVal {
    def kvv = new mwt.numerics.Vec2F(v.x, v.y)
    def vff = Vff(v.x, v.y)
    def ~~>(k: mwt.numerics.Vec2F) = { k.x = v.x; k.y = v.y; k }
  }
  class VectorInterop3(val v: Long) extends AnyVal {
    def kvv = { val u = Vff.from(v); new mwt.numerics.Vec2F(u.x, u.y) }
    def imm = { val u = Vff.from(v); IVec2F(u.x, u.y) }
    def mut = { val u = Vff.from(v); MVec2F(u.x, u.y) }
    def ~~>(m: AVec2F) = { val u = Vff.from(v); m.x = u.x; m.y = u.y; m }
    def ~~>(k: mwt.numerics.Vec2F) = { val u = Vff.from(v); k.x = u.x; k.y = u.y; k }
  }
  implicit def implicitVectorInterop3(v: Vff) = new VectorInterop3(v.repr)

  // Merges
  def sortedMerge(xss: Array[Array[Int]]): Array[Int] = {
    if (xss.length==0) new Array[Int](0)
    else {
      val is = new Array[Int](xss.length)
      val ys = new Array[Int]((0 /: xss)(_ + _.length))
      var i = 0
      while (i < ys.length) {
        var jbest = 0
        var best = Int.MaxValue
        var j = 0
        while (j < xss.length) {
          if (is(j) < xss(j).length) {
            val x = xss(j)(is(j))
            if (x <= best) { jbest = j; best = x }
          }
          j += 1
        }
        ys(i) = best
        is(jbest) += 1
        i += 1
      }
      ys
    }
  }
  def sortedMerge(xss: Array[Array[Long]]): Array[Long] = {
    if (xss.length==0) new Array[Long](0)
    else {
      val is = new Array[Int](xss.length)
      val ys = new Array[Long]((0 /: xss)(_ + _.length))
      var i = 0
      while (i < ys.length) {
        var jbest = 0
        var best = Long.MaxValue
        var j = 0
        while (j < xss.length) {
          if (is(j) < xss(j).length) {
            val x = xss(j)(is(j))
            if (x <= best) { jbest = j; best = x }
          }
          j += 1
        }
        ys(i) = best
        is(jbest) += 1
        i += 1
      }
      ys
    }
  }
  def sortedMerge(xss: Array[Array[Float]]): Array[Float] = {
    if (xss.length==0) new Array[Float](0)
    else {
      val is = new Array[Int](xss.length)
      val ys = new Array[Float]((0 /: xss)(_ + _.length))
      var i = 0
      while (i < ys.length) {
        var jbest = 0
        var best = Float.PositiveInfinity
        var j = 0
        while (j < xss.length) {
          if (is(j) < xss(j).length) {
            val x = xss(j)(is(j))
            if (x < best) { jbest = j; best = x }
          }
          j += 1
        }
        ys(i) = best
        is(jbest) += 1
        i += 1
      }
      ys
    }
  }
  def sortedMerge(xss: Array[Array[Double]]): Array[Double] = {
    if (xss.length==0) new Array[Double](0)
    else {
      val is = new Array[Int](xss.length)
      val ys = new Array[Double]((0 /: xss)(_ + _.length))
      var i = 0
      while (i < ys.length) {
        var jbest = 0
        var best = Double.PositiveInfinity
        var j = 0
        while (j < xss.length) {
          if (is(j) < xss(j).length) {
            val x = xss(j)(is(j))
            if (x < best) { jbest = j; best = x }
          }
          j += 1
        }
        ys(i) = best
        is(jbest) += 1
        i += 1
      }
      ys
    }
  }
  def sortedMergeBy[T: ClassTag](xss: Array[Array[T]])(lt: (T,T) => Boolean): Array[T] = {
    if (xss.length==0) new Array[T](0)
    else {
      val is = new Array[Int](xss.length)
      val ys = new Array[T]((0 /: xss)(_ + _.length))
      var i = 0
      while (i < ys.length) {
        var j = 0
        while (j < xss.length && is(j) >= xss(j).length) j += 1
        var jbest = j
        var best = xss(j)(is(j))
        while (j < xss.length) {
          if (is(j) < xss(j).length) {
            val x = xss(j)(is(j))
            if (lt(x,best)) { jbest = j; best = x }
          }
          j += 1
        }
        ys(i) = best
        is(jbest) += 1
        i += 1
      }
      ys
    }
  }


  // Fits
  // Fit is of form a*y + b*x + c = 0
  class FitLine {
    var Ox, Oy, Sx, Sy, Sxx, Sxy, Syy = 0.0
    var iD, Dx, Dy, Dz, iSab = 0.0
    var a,b,c = 0.0
    var n = 0
    var ready = true
    def fit: this.type = {
      Dx = n*Sxx - Sx*Sx
      Dy = n*Syy - Sy*Sy
      Dz = Sx*Sy - n*Sxy
      if (math.abs(Dy) > math.abs(Dx)) { iD = 1.0/Dy; a = Dz*iD; b = 1.0; iSab = 1.0/(a*a+1); c = (Sy*Sxy - Syy*Sx)*iD }
      else { iD = 1.0/Dx; a = 1.0; b = Dz*iD; iSab = 1.0/(1+b*b); c = (Sx*Sxy - Sxx*Sy)*iD }
      ready = true
      this
    }      
    def unfit = {
      if (!ready) fit
      val invn = if (n==0) 0.0 else 1.0/n
      iSab * (if (b==1) (Dx - Dz*a)*invn else (Dy - Dz*b)*invn)
    }
    def +~(x0: Double, y0: Double): this.type = {
      if ((x0*y0).nan) return this
      ready = false
      if (n==0) { Ox = x0; Oy = y0 }
      val x = x0 - Ox
      val y = y0 - Oy
      n += 1
      Sx += x
      Sy += y
      Sxx += x*x
      Sxy += x*y
      Syy += y*y
      this
    }
    def -~(x0: Double, y0: Double): this.type = {
      if ((x0*y0).nan) return this
      ready = false
      val x = x0 - Ox
      val y = y0 - Oy
      n -= 1
      Sx -= x
      Sy -= y
      Sxx -= x*x
      Sxy -= x*y
      Syy -= y*y
      this
    }
    def x(y: Double) = { if (!ready) { fit }; Ox - (c + a*(y-Oy)) / b }
    def y(x: Double) = { if (!ready) { fit }; Oy - (c + b*(x-Ox)) / a }
    def posErr = { if (!ready) { fit }; if (a==1) Sxx*iD else Syy*iD }
    def slope = { if (!ready) { fit }; -b/a }
    def slopeErr = { if (!ready) { fit }; if (a==1) n*iD else n*iD/math.abs(a) }
    def snap(v: MVec2D): v.type = {
      if (!ready) fit
      v -~ (Ox, Oy)
      if (a==1) {  /* y = -b*x - c */
        v.x = iSab*(v.x - b*(c + v.y))
        v.y = -b*v.x - c
      }
      else { /* x = -a*y - c */
        v.y = iSab*(v.y - a*(c + v.x))
        v.x = -a*v.y - c
      }
      v +~ (Ox, Oy)
      v
    }
    def snap(v: MVec2F): v.type = {
      if (!ready) fit
      v -~~ (Ox, Oy)
      if (a==1) {  /* y = -b*x - c */
        v.x = (iSab*(v.x - b*(c + v.y))).toFloat
        v.y = (-b*v.x - c).toFloat
      }
      else { /* x = -a*y - c */
        v.y = (iSab*(v.y - a*(c + v.x))).toFloat
        v.x = (-a*v.y - c).toFloat
      }
      v +~~ (Ox, Oy)
      v
    }
    def snap(v: Vff): Vff = {
      if (!ready) fit
      if (a==1) {  /* y = -b*x - c */
        val x = iSab*((v.x-Ox) - b*(c + (v.y-Oy)))
        val y = -b*x - c
        Vff.from(x+Ox,y+Oy)
      }
      else { /* x = -a*y - c */
        val y = iSab*((v.y-Oy) - a*(c + (v.x-Ox)))
        val x = -a*y - c
        Vff.from(x+Ox,y+Oy)
      }
    }
    def mimic(fl: FitLine): this.type = {
      ready = false
      n = fl.n
      Ox = fl.Ox
      Oy = fl.Oy
      Sx = fl.Sx
      Sy = fl.Sy
      Sxx = fl.Sxx
      Sxy = fl.Sxy
      Syy = fl.Syy
      this
    }
    def reset: this.type = {
      ready = false
      n = 0
      Sx = 0.0; Sy =0.0; Sxx = 0.0; Sxy = 0.0; Syy = 0.0
      this
    }
    override def toString = { if (!ready) { fit }; s"$a y + $b x + $c = 0" }
  }

  // i is the leftmost point of fb
  @tailrec def findBestNearSplit(x: Array[Float], y: Array[Float], fa: FitLine, fb: FitLine, i: Int, dir: Int): Int = {
    val j1 = math.min(math.abs(dir), if (dir<0) fa.n-5 else fb.n-5)
    var j = 1
    var vbest = fa.unfit + fb.unfit
    var vj = 0
    while (j < j1) {
      if (dir<0) { fa -~ (x(i-j), y(i-j)); fb +~ (x(i-j), y(i-j)) } else { fa +~ (x(i+j-1), y(i+j-1)); fb -~ (x(i+j-1), y(i+j-1)) }
      val v = fa.unfit + fb.unfit
      if (v < vbest) { vbest = v; vj = j }
      j += 1
    }
    j -= 1
    while (j > vj) {
      if (dir<0) { fa +~ (x(i-j), y(i-j)); fb -~ (x(i-j), y(i-j)) } else { fa -~ (x(i+j-1), y(i+j-1)); fb +~ (x(i+j-1), y(i+j-1)) }
      j -= 1
    }
    val vi = (if (dir<0) i - vj else i + vj)
    if (vj==0 || j1 < math.abs(dir)) vi
    else findBestNearSplit(x, y, fa, fb, vi, dir)
  }
  def growLinearly(x: Array[Float], y: Array[Float], fl: FitLine, toofar: Float, i0: Int, i1: Int) = {
    fl.reset
    var i = i0
    var err, oerr = 0.0
    while (i <= i1 && fl.n < 5) { fl +~ (x(i), y(i)); i += 1 }
    err = fl.unfit
    oerr = err
    if (i+1>=i1) i1
    else {
      do {
        i += 1
        fl +~ (x(i), y(i))
        oerr = err
        err = fl.unfit
      } while (err-oerr < toofar && i < i1)
      if ({ var j=i; var k=0; while (k<3 && j<=i1) { if (!(x(j)*y(j)).nan) { k += 1 }; j += 1 }; k < 3}) {
        while (i<=i1) { fl +~ (x(i), y(i)); i += 1 }
      } else {
        i -= 1
        fl -~ (x(i), y(i))
      }
      i - 1
    }
  }
        
  def fitLinearSegments(x: Array[Float], y: Array[Float], toofar: Float, tiny: Int = 0)(i0: Int = 0, i1: Int = x.length-1) = {
    var j,k = 0
    var z = new Array[Long](8)
    var w = new Array[Float](16)
    var removed = 0
    var old = 2
    @inline def pushz(l: Long) { if (j >= z.length) { z = java.util.Arrays.copyOf(z, z.length*2) }; z(j) = l; j += 1 }
    @inline def pushw(f: Float) { if (k >= w.length) { w = java.util.Arrays.copyOf(w, w.length*2) }; w(k) = f; k += 1 }
    val fa, fb, fz = new FitLine
    pushz((i0 packII growLinearly(x, y, fb, toofar, i0, i1)).repr)
    pushw(fb.slope.toFloat)
    while (z(j-1).packed.i1 < i1) {
      fz mimic fa
      fa mimic fb
      val i = z(j-1).packed.i1+1
      pushz((i packII growLinearly(x, y, fb, toofar, i, i1)).repr)
      val ii = findBestNearSplit(x, y, fa, fb, i, -5)
      if (ii != i) {
        z(j-old) = z(j-old).packed.i1(ii-1).repr
        z(j-1) = z(j-1).packed.i0(ii).repr
      }
      if (fa.n <= tiny) {
        removed += 1
        w(k-1) = Float.NaN
        fa.reset
        val ha = z(j-2).packed.i0
        val hb = z(j-2).packed.i1
        if (fz.n == 0) {
          var h = ha
          while (h <= hb) { if (!(x(h)*y(h)).nan) { fb +~ (x(h), y(h)) }; h += 1 }
          z(j-1) = z(j-1).packed.i0(ha).repr
        }
        else {
          old += 1
          var l = 0
          var h = hb+1
          var g = ha-1
          while (h-g > 1) {
            while (h > ha &&  ((fb.y(x(h-1))-y(h-1)).sq < (fz.y(x(h-1))-y(h-1)).sq)) h -= 1
            while (g < hb && !((fb.y(x(g+1))-y(g+1)).sq < (fz.y(x(g+1))-y(g+1)).sq)) g += 1
            if (h-g > 1) { g += 1; h -= 1 }
          }
          if (g >= h) g = h-1
          l = z(j-old).packed.i1+1; while (l <= g) { if (!(x(l)*y(l)).nan) { fz +~ (x(l), y(l)) }; l += 1 }
          l = z(j-1).packed.i0-1; while (l >= h) { if (!(x(l)*y(l)).nan) { fb +~ (x(l), y(l)) }; l -= 1 }
          z(j-old) = z(j-old).packed.i1(g).repr
          z(j-1) = z(j-1).packed.i0(h).repr
          w(k+1-old) = fz.slope.toFloat
          fa mimic fz
          old += 1
        }
      }
      else {
        w(k-1) = fa.slope.toFloat
        old = 2
      }
      pushw(fb.slope.toFloat)
    }
    if (w(k-1).nan || fb.n == 0) { w(k-1) = Float.NaN; removed += 1 }
    if (removed > 0) {
      val n = j - removed
      val zz = new Array[Long](n)
      val ww = new Array[Float](n)
      var h,i = 0
      while (h < n) {
        if (!w(i).nan) {
          zz(h) = z(i)
          ww(h) = w(i)
          h += 1
        }
        i += 1
      }
      if (zz.length == 0) (Array( (i0 packII i1).repr ), Array(0f))
      else {
        if (zz(zz.length-1).packed.i1 < i1) zz(zz.length-1) = zz(zz.length-1).packed.i1(i1).repr
        (zz, ww)
      }
    }
    else (java.util.Arrays.copyOf(z, j), java.util.Arrays.copyOf(w, k))
  }
  
  def fitSine(x: Array[Float], y: Array[Float], th0: Double = 0, th1: Double = math.Pi)(i0: Int = 0, i1: Int = x.length-1): LongPack = {
    val fl = new FitLine
    val x0 = x(i0)
    val x1 = x(i1)
    val factor = (i1-i0)*(th1-th0)/((x1-x0)*(1+i1-i0))
    val Ox = x0 - 0.5*(x1-x0)/(i1-i0)
    var i = i0
    while (i <= i1) {
      val s = math.sin(factor*(x(i)-Ox) + th0)
      fl +~ (s, y(i))
      i += 1
    }
    fl.fit
    val amplitude = if (fl.a==1) -fl.b else -1/fl.a
    val offset = { val s = math.sin(th0); fl.y(s) - amplitude*s }
    offset.toFloat packFF amplitude.toFloat
  }
  def fitCos(x: Array[Float], y: Array[Float])(i0: Int = 0, i1: Int = x.length-1) = fitSine(x, y, math.Pi*0.5, math.Pi*1.5)(i0,i1)
  
  
  // Iterating over distances, mostly; would work for times also
  def stepAhead(ds: Array[Float], dd: Float, f0: Float): Float = {
    if (f0+1 >= ds.length) Float.NaN
    else {
      var i = f0.toInt
      var dx = dd
      if (f0 > i) {
        val d = ds(i+1) - ds(i)
        if (dx <= d*(f0-i)) return (f0 + dx/d)
        else dx -= d*(f0-i)
        i += 1
      }
      while (i+1 < ds.length) {
        val d = ds(i+1) - ds(i)
        if (dx <= d) return (i + dx/d)
        dx -= d
        i += 1
      }
      Float.NaN
    }
  }
  
  // Linear interpolation of values stored in arrays (use float index instead of int)
  def subindexF(fs: Array[Float], fi: Float) = {
    val ii = fi.toInt
    if (ii==fi) fs(ii)
    else {
      val r = fi-ii
      (1-r)*fs(ii) + r*fs(ii+1)
    }
  }
  def subindexVff(vs: Int => Long, fi: Float) = {
    val ii = fi.toInt
    if (ii==fi) Vff.from(vs(ii))
    else {
      val r = fi-ii
      Vff.from(vs(ii))*(1-r) + Vff.from(vs(ii+1))*r
    }
  }
  
  
  // Signal processing routines
  def packedCplxProductIntoRight(a: Array[Float], b: Array[Float]) {
    b(0) *= a(0)
    if ((b.length&1)==0) b(1) *= a(1)
    else {
      val re = b(b.length-1)*a(a.length-1) - b(1)*a(1)
      val im = b(b.length-1)*a(1) + b(1)*a(a.length-1)
      b(b.length-1) = re
      b(1) = im
    }
    var i = 2
    while (i+1 < b.length) {
      val re = b(i)*a(i) - b(i+1)*a(i+1)
      val im = b(i)*a(i+1) + b(i+1)*a(i)
      b(i) = re
      b(i+1) = im
      i += 2
    }
  }
  def packedCplxProductIntoRight(a: Array[Double], b: Array[Double]) {
    b(0) *= a(0)
    if ((b.length&1)==0) b(1) *= a(1)
    else {
      val re = b(b.length-1)*a(a.length-1) - b(1)*a(1)
      val im = b(b.length-1)*a(1) + b(1)*a(a.length-1)
      b(b.length-1) = re
      b(1) = im
    }
    var i = 2
    while (i+1 < b.length) {
      val re = b(i)*a(i) - b(i+1)*a(i+1)
      val im = b(i)*a(i+1) + b(i+1)*a(i)
      b(i) = re
      b(i+1) = im
      i += 2
    }
  }
  
  // Gaussian blur--explicitly create Gaussian (because it's easier to get right than to multiply the FFT directly)
  def gaussianBlurF(fs: Array[Float], sigma: Float) {
    import math._
    val issq = 1.0/(sigma*sigma)
    val xf = new edu.emory.mathcs.jtransforms.fft.FloatFFT_1D(fs.length)
    val gau = new Array[Float](fs.length)
    var gs = 1.0
    var g = 1.0f
    gau(0) = 1.0f
    var i = 1
    val N = (fs.length)/2
    while (i <= N && g > 1e-6) {
      g = exp(-issq*i*i).toFloat
      gau(i) = g
      gau(gau.length-i) = g
      gs += 2*g
      i += 1
    }
    if (g > 1e-6 && 2*N == fs.length) gs -= g
    val igs = (if (gs==0) 0.0 else 1.0/gs).toFloat
    i = 0
    while (i < gau.length) { gau(i) *= igs; i += 1 }
    xf.realForward(gau)
    xf.realForward(fs)
    packedCplxProductIntoRight(gau,fs)
    xf.realInverse(fs, true)
  }
  
  trait InclusiveRange {
    def i0: Int
    def i1: Int
    def length = 1+i1-i0
  }
  
  // Reorder/duplicate/shuffle an array based on a set of ranges
  def shuffle[IR <: InclusiveRange, @specialized T: ClassTag](a: Array[T], parts: Array[IR]) = {
    var i,n = 0
    while (i < parts.length) { n += parts(i).length; i += 1 }
    val b = new Array[T](n)
    i = 0
    n = 0
    while (i < parts.length) {
      var j = parts(i).i0
      while (j <= parts(i).i1) {
        b(n) = a(j)
        n += 1
        j += 1
      }
      i += 1
    }
    b
  }
  
  // Find the optimal causal linear predictor of b given a with at most n points of history
  // Can do size 100,100,20 in about 40 us on a Xeon 5680 (~20x faster than Matlab)
  def optimalLinearFilter(a: Array[Float], b: Array[Float], n: Int) = {
    import org.apache.commons.math3
    var sa,sb = 0.0
    var i = 0
    while (i < a.length) {
      sa += a(i)
      sb += b(i)
      i += 1
    }
    if (i>0) {
      sa /= i
      sb /= i
    }
    val m = (a.length + b.length + ((a.length+b.length)&1))
    val c,d,e = new Array[Float](m)
    i = 0
    while (i < a.length) {
      c(a.length-i-1) = a(i) - sa.toFloat
      e(i) = a(i) - sa.toFloat
      d(i) = b(i) - sb.toFloat
      i += 1
    }
    val xf = new edu.emory.mathcs.jtransforms.fft.FloatFFT_1D(c.length)
    xf.realForward(c)
    xf.realForward(d)
    xf.realForward(e)
    d(0) *= c(0)
    d(1) *= c(1)
    e(0) *= c(0)
    e(1) *= c(1)    
    i = 2
    while (i < m) {
      val dre = d(i)*c(i) - d(i+1)*c(i+1)
      val dim = d(i)*c(i+1) + d(i+1)*c(i)
      val ere = e(i)*c(i) - e(i+1)*c(i+1)
      val eim = e(i)*c(i+1) + e(i+1)*c(i)
      d(i) = dre
      d(i+1) = dim
      e(i) = ere
      e(i+1) = eim
      i += 2
    }
    xf.realInverse(d,true)   // Cross covariance of a and b is now in d
    xf.realInverse(e,true)   // Auto convariance of a with itself is now in e
    val M = new math3.linear.Array2DRowRealMatrix(n,n)
    val xss = M.getDataRef
    i = 0
    while (i < n) {
      var j = 0
      while (j < n) {
        xss(i)(j) = -e(a.length-math.abs(i-j)-1);
        j += 1
      }
      i += 1
    }
    val solver = (new math3.linear.LUDecomposition(M)).getSolver
    val V = new math3.linear.ArrayRealVector(n)
    val xs = V.getDataRef
    i = 0
    while (i < n) {
      xs(i) = -d(a.length+i)
      i += 1
    }
    val Y = solver.solve(V)
    val ys = new Array[Float](n)
    i = 0
    while (i < n) {
      ys(i) = Y.getEntry(i).toFloat
      i += 1
    }
    ys
  }
  
  def convolveFilter(a: Array[Float], filt: Array[Float]) = {
    val xf = new edu.emory.mathcs.jtransforms.fft.FloatFFT_1D(a.length)
    val b,c = new Array[Float](a.length)
    var i = 0
    while (i < filt.length) { b(i) = filt(i); i += 1 }
    i = 0; while (i < a.length) { c(i) = a(i); i += 1 }
    xf.realForward(c)
    xf.realForward(b)
    packedCplxProductIntoRight(c,b)
    xf.realInverse(b,true)
    b
  }
  
  final case class Foray(i0: Int, i1: Int, sign: Int) extends InclusiveRange {}
  
  @tailrec def findForaysF(a: Array[Float], far: Float)(i0: Int = 0, i1: Int = a.length-1, result: Array[Foray] = new Array[Foray](8), n: Int = 0, dir: Int = 0): Array[Foray] = {
    if (i0 > i1) { if (n==result.length) result else java.util.Arrays.copyOf(result,n) }
    else {
      var i = i0
      var dr = dir
      if (dir==0) {
        while (i<=i1 && math.abs(a(i)) < far) i += 1
        if (i<=i1) dr = math.signum(a(i)).toInt
      }
      else while (i<=i1 && dr*a(i) < far) i += 1
      var j = i+1
      while (j<=i1 && -dr*a(j) < far) j += 1
      if (j > i1) {
        val res = (if (n < result.length) result else java.util.Arrays.copyOf(result,n+1))
        res(n) = Foray(i0, i1, 0)
        findForaysF(a, far)(i1+1, i1, res, n+1, 0)
      }
      else {
        var k = j-1
        var zci = 0L
        var zcn = 0
        while (k>i && dr*a(k) < far) {
          if (a(k)*a(k+1) <= 0) { zci += k; zcn += 1 }
          k -= 1
        }
        val m = if (zcn==0) j-1 else (zci/zcn).toInt
        val res = (if (n < result.length) result else java.util.Arrays.copyOf(result,2*n))
        res(n) = Foray(i0, m, if (n==0) 0 else dr)
        findForaysF(a, far)(m+1, i1, res, n+1, -dr)
      }
    }
  }
}
