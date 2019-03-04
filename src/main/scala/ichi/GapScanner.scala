package ichi.mwt

trait GapScanner {
  def N: Int
  protected def gaps: Array[Int]
  protected def k0: Int
  protected def k0_=(k: Int): Unit
  protected def k1: Int
  protected def k1_=(k: Int): Unit
  protected def g0: Int
  protected def g0_=(g: Int): Unit
  protected def g1: Int
  protected def g1_=(g: Int): Unit
  protected def safe = (g0+1==g1) && ((g0&1)==1)
  def findGaps(): this.type = {
    if (gaps.length==0) { g0 = -1; g1 = 0; return this }
    var k = k0
    var gl = 0
    var gr = gaps.length-1
    if (k < gaps(gl)) g0 = -1
    else if (k > gaps(gr)) { g0 = gr; g1 = g0+1; return this }
    else {
      while (gl+1 < gr) {
        val g = (gl+gr)>>>1
        if (k < gaps(g)+(g&1)) gr = g
        else gl = g
      }
      g0 = gl
    }
    k = k1
    gl = g0
    gr = gaps.length-1
    if (k > gaps(gr)) { g1 = gr+1; return this }
    else {
      while (gl+1 < gr) {
        val g = (gl+gr)>>>1
        if (k > gaps(g)-1+(g&1)) gl = g
        else gr = g
      }
      g1 = gr
    }
    this
  }
  def pr: this.type = { println(s"$k0 $k1 $g0 ${if (g0<0) 0 else gaps(g0)} $g1 ${if (g1>=gaps.length) N else gaps(g1)}"); this }
  def tapRanges[A](f: (Int,Int) => A): this.type = {
    if (safe) f(k0,k1)
    else {
      if ((g0&1)==1) f(k0, gaps(g0+1)-1)
      var g = g0+(g0&1)
      while (g < gaps.length && g+1 < g1 && (gaps(g+1)+1 <= k1)) {
        val r = if (g+2 < gaps.length) gaps(g+2)-1 else N
        f(gaps(g+1)+1, math.min(r, k1))
        g += 2
      }
    }
    this
  }
  def outerValid: (Int,Int) = if (safe) (k0,k1) else ({if ((g0&1)==1) k0 else gaps(g0+1)+1}, {if ((g1&1)==0) k1 else gaps(g1-1)-1})
  def loL: this.type = {
    if (k0<=0) throw new IndexOutOfBoundsException("Start < 0")
    k0 -= 1
    if (g0>=0 && k0<gaps(g0)+(g0&1)) g0 -= 1
    this
  }
  def loR: this.type = {
    if (k0>=k1) throw new IndexOutOfBoundsException("Start > End (%d)".format(k1))
    k0 += 1
    if (g0+1 < gaps.length && k0 >= gaps(g0+1)+((g0+1)&1)) g0 += 1
    this
  }
  def hiL: this.type = {
    if (k1<=k0) throw new IndexOutOfBoundsException("End < Start (%d)".format(k0))
    k1 -= 1
    if (g1>0 && k1<gaps(g1-1)+((g1-1)&1)) g1 -= 1
    this
  }
  def hiR: this.type = {
    if (k1>=N) throw new IndexOutOfBoundsException("End > Last (%d)".format(N))
    k1 += 1
    if (g1 < gaps.length && k1 > gaps(g1)-1+(g1&1)) g1 += 1
    this
  }
  def iL: this.type = loL.hiL
  def iR: this.type = hiR.loR
  def goto(lo: Int, hi: Int): this.type = {
    if (lo>hi) throw new IndexOutOfBoundsException("Start > End")
    if (lo<0) throw new IndexOutOfBoundsException("Start < 0")
    if (hi>N) throw new IndexOutOfBoundsException("End > Last")
    k0 = lo
    k1 = hi
    findGaps()
  }
  def lo2hi: this.type = { g0=g1-1; k0=k1; this }
  def hi2lo: this.type = { g1=g0+1; k1=k0; this }
}
object GapScanner {
  def union(ga: Array[Int], gb: Array[Int]): Array[Int] = {
    val aib = Array.newBuilder[Int]
    var i,j = 0
    while (i < ga.length && j < gb.length) {
      var g = 0
      if (ga(i) < gb(j)) {
        aib += ga(i)
        g = ga(i+1)
        i += 2
      }
      else {
        aib += gb(j)
        g = gb(j+1)
        j += 2
      }
      while ((i < ga.length && ga(i)<=g+1) || (j < gb.length && gb(j)<=g+1)) {
        if (i<ga.length && ga(i)<=g+1) {
          g = math.max(ga(i+1),g)
          i += 2
        }
        else {
          g = math.max(gb(j+1),g)
          j += 2
        }
      }
      aib += g
    }
    while (i < ga.length) { aib += ga(i); aib += ga(i+1); i += 2 }
    while (j < gb.length) { aib += gb(j); aib += gb(j+1); j += 2 }
    aib.result
  }
  def intersection(ga: Array[Int], gb: Array[Int]) = {
    val aib = Array.newBuilder[Int]
    var i,j = 0
    while (i < ga.length && j < gb.length) {
      val n0 = math.max(ga(i), gb(j))
      val n1 = math.min(ga(i+1), gb(j+1))
      if (n0 <= n1) {
        aib += n0
        aib += n1
        if (ga(i+1) < gb(j+1)) i += 2
        else j += 2
      }
      else if (ga(i) < gb(j)) i += 2
      else j += 2
    }
    aib.result
  }
  def complement(ga: Array[Int], N: Int) = {
    val aib = Array.newBuilder[Int]
    if (ga.length==0) Array(0,N)
    else {
      var i,g = 0
      while (i < ga.length) {
        if (g<ga(i)) {
          aib += g
          aib += ga(i)-1
        }
        g = ga(i+1)+1
        i += 2
      }
      if (ga(i-1)<N) {
        aib += ga(i-1)+1
        aib += N
      }
      aib.result
    }
  }
}

/*
Example:
class Gs(protected var k0: Int, protected var k1: Int, val N: Int, val gaps: Array[Int]) extends GapScanner {}
val gs = new Gs(2,4,12,Array(4,6)); gs.findGaps; gs.pr
union(Array(0,3,8,9), Array(-1,2,9,12))
intersection(Array(1,3,7,9),Array(2,8))

*/


