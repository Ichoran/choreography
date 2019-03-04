/* Copyright 2011-2012 Rex Kerr and the Howard Hughes Medical Institute */
/* This file is covered by the BSD license with HHMI as the organization */
/* See http://www.opensource.org/licenses/bsd-license.php for licence template text. */

package ichi

import scala.language.implicitConversions
import scala.util.hashing.{MurmurHash3 => mh3}

package object muple {
  // Single-var specialized traits
  trait MupleA[@specialized A] { def a: A; def a_=(a0: A): Unit; def A(a0: A): this.type = { a_=(a0); this }; def fA(fn: A => A): this.type = { a_=(fn(a)); this } }
  trait MupleB[@specialized B] { def b: B; def b_=(b0: B): Unit; def B(b0: B): this.type = { b_=(b0); this }; def fB(fn: B => B): this.type = { b_=(fn(b)); this } }
  trait MupleC[@specialized C] { def c: C; def c_=(c0: C): Unit; def C(c0: C): this.type = { c_=(c0); this }; def fC(fn: C => C): this.type = { c_=(fn(c)); this } }
  trait MupleD[@specialized D] { def d: D; def d_=(d0: D): Unit; def D(d0: D): this.type = { d_=(d0); this }; def fD(fn: D => D): this.type = { d_=(fn(d)); this } }
  trait MupleE[E] { def e: E; def e_=(e0: E): Unit; def E(e0: E): this.type = { e_=(e0); this }; def fE(fn: E => E): this.type = { e_=(fn(e)); this } }
  trait MupleF[F] { def f: F; def f_=(f0: F): Unit; def F(f0: F): this.type = { f_=(f0); this }; def fF(fn: F => F): this.type = { f_=(fn(f)); this } }
  trait MupleG[G] { def g: G; def g_=(g0: G): Unit; def G(g0: G): this.type = { g_=(g0); this }; def fG(fn: G => G): this.type = { g_=(fn(g)); this } }
  trait Fuple { def go: Unit; final def fx = { go; this } }

  // Multi-var traits and classes with companion object
  trait Mupled1[A] extends MupleA[A] with Product {
    def mup: this.type = this
    def tup = Tuple1(a)
    def productArity = 1
    def productElement(idx: Int) = idx match { case 0=>a; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple1[_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple1[Any]]; m.a==a } else false
    override def toString = "<"+a.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(0x59f64753,a.##),1)
  }
  trait Mupled2[A,B] extends MupleA[A] with MupleB[B] with Product {
    def mup: this.type = this
    def tup = (a, b)
    def productArity = 2
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple2[_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple2[Any,Any]]; m.a==a && m.b==b } else false
    override def toString = "<"+a.toString+", "+b.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(0x67ad71c7,b.##),a.##),2)
  }
  trait Mupled3[A,B,C] extends MupleA[A] with MupleB[B] with MupleC[C] with Product {
    def mup: this.type = this
    def tup = (a, b, c)
    def productArity = 3
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case 2=>c; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple3[_,_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple3[Any,Any,Any]]; m.a==a && m.b==b && m.c==c } else false
    override def toString = "<"+a.toString+", "+b.toString+", "+c.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(mh3.mix(0xfd4cf715,b.##),c.##),a.##),3)
  }
  trait Mupled4[A,B,C,D] extends MupleA[A] with MupleB[B] with MupleC[C] with MupleD[D] with Product {
    def mup: this.type = this
    def tup = (a, b, c, d)
    def productArity = 4
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case 2=>c; case 3=>d; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple4[_,_,_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple4[Any,Any,Any,Any]]; m.a==a && m.b==b && m.c==c && m.d==d } else false
    override def toString = "<"+a.toString+", "+b.toString+", "+c.toString+", "+d.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(mh3.mix(mh3.mix(0xaeb52fb4,b.##),c.##),d.##),a.##),4)
  }
  trait Mupled5[A,B,C,D,E] extends MupleA[A] with MupleB[B] with MupleC[C] with MupleD[D] with MupleE[E] with Product {
    def mup: this.type = this
    def tup = (a, b, c, d, e)
    def productArity = 5
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case 2=>c; case 3=>d; case 4=>e; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple5[_,_,_,_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple5[Any,Any,Any,Any,Any]]; m.a==a && m.b==b && m.c==c && m.d==d && m.e==e } else false
    override def toString = "<"+a.toString+", "+b.toString+", "+c.toString+", "+d.toString+", "+e.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(mh3.mix(mh3.mix(mh3.mix(0x5a03a7a7,b.##),c.##),d.##),e.##),a.##),5)
  }
  trait Mupled6[A,B,C,D,E,F] extends MupleA[A] with MupleB[B] with MupleC[C] with MupleD[D] with MupleE[E] with MupleF[F] with Product {
    def mup: this.type = this
    def tup = (a, b, c, d, e, f)
    def productArity = 6
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case 2=>c; case 3=>d; case 4=>e; case 5=>f; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple6[_,_,_,_,_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple6[Any,Any,Any,Any,Any,Any]]; m.a==a && m.b==b && m.c==c && m.d==d && m.e==e && m.f==f } else false
    override def toString = "<"+a.toString+", "+b.toString+", "+c.toString+", "+d.toString+", "+e.toString+", "+f.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(mh3.mix(mh3.mix(mh3.mix(mh3.mix(0xbcc857aa,b.##),c.##),d.##),e.##),f.##),a.##),6)
  }
  trait Mupled7[A,B,C,D,E,F,G] extends MupleA[A] with MupleB[B] with MupleC[C] with MupleD[D] with MupleE[E] with MupleF[F] with MupleG[G] with Product {
    def mup: this.type = this
    def tup = (a, b, c, d, e, f, g)
    def productArity = 7
    def productElement(idx: Int) = idx match { case 0=>a; case 1=>b; case 2=>c; case 3=>d; case 4=>e; case 5=>f; case 6=>g; case _ => new IndexOutOfBoundsException(idx.toString) }
    def canEqual(that: Any) = that.isInstanceOf[Muple7[_,_,_,_,_,_,_]]
    override def equals(that: Any) = if (canEqual(that)) { val m = that.asInstanceOf[Muple7[Any,Any,Any,Any,Any,Any,Any]]; m.a==a && m.b==b && m.c==c && m.d==d && m.e==e && m.f==f && m.g==g } else false
    override def toString = "<"+a.toString+", "+b.toString+", "+c.toString+", "+d.toString+", "+e.toString+", "+f.toString+", "+g.toString+">"
    override def hashCode = mh3.finalizeHash(mh3.mixLast(mh3.mix(mh3.mix(mh3.mix(mh3.mix(mh3.mix(mh3.mix(0xc461e69e,b.##),c.##),d.##),e.##),f.##),g.##),a.##),7)
  }
  class Muple1[@specialized A](var a: A) extends Mupled1[A] with MupleA[A] with Fuple {
    def this(m0: Muple1[A]) = this(m0.a)
    def go {}
  }
  class Muple2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](var a: A, var b: B) extends Mupled2[A,B] with MupleA[A] with MupleB[B] with Fuple {
    def this(m0: Muple2[A,B]) = this(m0.a, m0.b)
    def this(t0: (A,B)) = this(t0._1, t0._2)
    def go {}
  }
  class Muple3[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](var a: A, var b: B, var c: C) extends Mupled3[A,B,C] with MupleA[A] with MupleB[B] with Fuple {
    def this(m0: Muple3[A,B,C]) = this(m0.a, m0.b, m0.c)
    def this(t0: (A,B,C)) = this(t0._1, t0._2, t0._3)
    def go {}
  }
  class Muple4[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](var a: A, var b: B, var c: C, var d: D) extends Mupled4[A,B,C,D] with MupleA[A] with MupleB[B] with Fuple {
    def this(m0: Muple4[A,B,C,D]) = this(m0.a, m0.b, m0.c, m0.d)
    def this(t0: (A,B,C,D)) = this(t0._1, t0._2, t0._3, t0._4)
    def go {}
  }
  class Muple5[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](var a: A, var b: B, var c: C, var d: D, var e: E) extends Mupled5[A,B,C,D,E] with MupleA[A] with MupleB[B] with Fuple {
    def this(m0: Muple5[A,B,C,D,E]) = this(m0.a, m0.b, m0.c, m0.d, m0.e)
    def this(t0: (A,B,C,D,E)) = this(t0._1, t0._2, t0._3, t0._4, t0._5)
    def go {}
  }
  class Muple6[@specialized(Int,Long,Double) A, B, C, D, E, F](var a: A, var b: B, var c: C, var d: D, var e: E, var f: F) extends Mupled6[A,B,C,D,E,F] with MupleA[A] with Fuple {
    def this(m0: Muple6[A,B,C,D,E,F]) = this(m0.a, m0.b, m0.c, m0.d, m0.e, m0.f)
    def this(t0: (A,B,C,D,E,F)) = this(t0._1, t0._2, t0._3, t0._4, t0._5, t0._6)
    def go {}
  }
  class Muple7[@specialized(Int,Long,Double) A, B, C, D, E, F, G](var a: A, var b: B, var c: C, var d: D, var e: E, var f: F, var g: G) extends Mupled7[A,B,C,D,E,F,G] with MupleA[A] with Fuple {
    def this(m0: Muple7[A,B,C,D,E,F,G]) = this(m0.a, m0.b, m0.c, m0.d, m0.e, m0.f, m0.g)
    def this(t0: (A,B,C,D,E,F,G)) = this(t0._1, t0._2, t0._3, t0._4, t0._5, t0._6, t0._7)
    def go {}
  }
  object Mu {
    def apply[@specialized A](a0: A) = new Muple1[A] ( a0 )
    def apply[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](a0: A, b0: B) = new Muple2[A,B] ( a0, b0 )
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](a0: A, b0: B, c0: C) = new Muple3[A,B,C] ( a0, b0, c0 )
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](a0: A, b0: B, c0: C, d0: D) = new Muple4[A,B,C,D] ( a0, b0, c0, d0 )
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](a0: A, b0: B, c0: C, d0: D, e0: E) = new Muple5[A,B,C,D,E] ( a0, b0, c0, d0, e0 )
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F](a0: A, b0: B, c0: C, d0: D, e0: E, f0: F) = new Muple6[A,B,C,D,E,F] ( a0, b0, c0, d0, e0, f0 )
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F, G](a0: A, b0: B, c0: C, d0: D, e0: E, f0: F, g0: G) = new Muple7[A,B,C,D,E,F,G] ( a0, b0, c0, d0, e0, f0, g0 )
  }

  // Multi-var optional traits and classes with companion object
  trait Oople { def ok: Boolean; def ok_=(o0: Boolean): Unit; def O(o0: Boolean): this.type = { ok_=(o0); this; }; def isDefined = ok }
  trait Pfuple extends Fuple { self: Oople => final def opgo { if (ok) go }; final def pf = { if (ok) go; this } }
  trait OopleA[@specialized A] extends MupleA[A] with Oople {
    def opA = if (ok) Some(a) else None
    def mapA(fn: A => A) = { if (ok) a = fn(a); this }
  }
  trait OopleB[@specialized B] extends MupleB[B] with Oople {
    def opB = if (ok) Some(b) else None
    def mapB(fn: B => B) = { if (ok) b = fn(b); this }
  }
  trait OopleC[@specialized C] extends MupleC[C] with Oople {
    def opC = if (ok) Some(c) else None
    def mapC(fn: C => C) = { if (ok) c = fn(c); this }
  }
  trait OopleD[@specialized D] extends MupleD[D] with Oople {
    def opD = if (ok) Some(d) else None
    def mapD(fn: D => D) = { if (ok) d = fn(d); this }
  }
  trait OopleE[@specialized E] extends MupleE[E] with Oople {
    def opE = if (ok) Some(e) else None
    def mapE(fn: E => E) = { if (ok) e = fn(e); this }
  }
  trait OopleF[@specialized F] extends MupleF[F] with Oople {
    def opF = if (ok) Some(f) else None
    def mapF(fn: F => F) = { if (ok) f = fn(f); this }
  }
  trait OopleG[@specialized G] extends MupleG[G] with Oople {
    def opG = if (ok) Some(g) else None
    def mapG(fn: G => G) = { if (ok) g = fn(g); this }
  }
  trait Oopled1[A] extends Mupled1[A] with OopleA[A] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople1")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_>"
    override def equals(that: Any) = that match { case oo: Oople1[_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled2[A,B] extends Mupled2[A,B] with OopleA[A] with OopleB[B] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople2")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_>"
    override def equals(that: Any) = that match { case oo: Oople2[_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled3[A,B,C] extends Mupled3[A,B,C] with OopleA[A] with OopleB[B] with OopleC[C] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople3")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_,_>"
    override def equals(that: Any) = that match { case oo: Oople3[_,_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled4[A,B,C,D] extends Mupled4[A,B,C,D] with OopleA[A] with OopleB[B] with OopleC[C] with OopleD[D] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople4")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_,_,_>"
    override def equals(that: Any) = that match { case oo: Oople4[_,_,_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled5[A,B,C,D,E] extends Mupled5[A,B,C,D,E] with OopleA[A] with OopleB[B] with OopleC[C] with OopleD[D] with OopleE[E] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople5")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_,_,_,_>"
    override def equals(that: Any) = that match { case oo: Oople5[_,_,_,_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled6[A,B,C,D,E,F] extends Mupled6[A,B,C,D,E,F] with OopleA[A] with OopleB[B] with OopleC[C] with OopleD[D] with OopleE[E] with OopleF[F] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople6")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_,_,_,_,_>"
    override def equals(that: Any) = that match { case oo: Oople6[_,_,_,_,_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  trait Oopled7[A,B,C,D,E,F,G] extends Mupled7[A,B,C,D,E,F,G] with OopleA[A] with OopleB[B] with OopleC[C] with OopleD[D] with OopleE[E] with OopleF[F] with OopleG[G] {
    def oop: this.type = this
    override def tup = if (ok) super.tup else throw new java.util.NoSuchElementException("Empty Oople7")
    def get = if (ok) Some(super.tup) else None
    override def hashCode = if (ok) super.hashCode else 0x9be97700
    override def toString = if (ok) super.toString else "<_,_,_,_,_,_,_>"
    override def equals(that: Any) = that match { case oo: Oople7[_,_,_,_,_,_,_] if (!ok || !oo.ok) => ok==oo.ok; case _ => super.equals(that) }
  }
  class Oople1[@specialized A](var a: A, var ok: Boolean = true) extends Oopled1[A] with OopleA[A] with Pfuple {
    def this(o0: Oople1[A]) = this(o0.a,o0.ok)
    def this(o1: Option[A]) = this(if (o1.isDefined) o1.get else null.asInstanceOf[A], o1.isDefined)
    def this() = this(null.asInstanceOf[A], false)
    def go {}
  }
  class Oople2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](var a: A, var b: B, var ok: Boolean = true) extends Oopled2[A,B] with OopleA[A] with OopleB[B] with Pfuple {
    def this(o0: Oople2[A,B]) = this(o0.a,o0.b,o0.ok)
    def this(o1: Option[(A,B)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], false)
    def go {}
  }
  class Oople3[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](var a: A, var b: B, var c: C, var ok: Boolean = true) extends Oopled3[A,B,C] with OopleA[A] with OopleB[B] with Pfuple {
    def this(o0: Oople3[A,B,C]) = this(o0.a,o0.b,o0.c,o0.ok)
    def this(o1: Option[(A,B,C)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], if (o1.isDefined) o1.get._3 else null.asInstanceOf[C], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], null.asInstanceOf[C], false)
    def go {}
  }
  class Oople4[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](var a: A, var b: B, var c: C, var d: D, var ok: Boolean = true) extends Oopled4[A,B,C,D] with OopleA[A] with OopleB[B] with Pfuple {
    def this(o0: Oople4[A,B,C,D]) = this(o0.a,o0.b,o0.c,o0.d,o0.ok)
    def this(o1: Option[(A,B,C,D)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], if (o1.isDefined) o1.get._3 else null.asInstanceOf[C], if (o1.isDefined) o1.get._4 else null.asInstanceOf[D], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], null.asInstanceOf[C], null.asInstanceOf[D], false)
    def go {}
  }
  class Oople5[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](var a: A, var b: B, var c: C, var d: D, var e: E, var ok: Boolean = true) extends Oopled5[A,B,C,D,E] with OopleA[A] with OopleB[B] with Pfuple {
    def this(o0: Oople5[A,B,C,D,E]) = this(o0.a,o0.b,o0.c,o0.d,o0.e,o0.ok)
    def this(o1: Option[(A,B,C,D,E)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], if (o1.isDefined) o1.get._3 else null.asInstanceOf[C], if (o1.isDefined) o1.get._4 else null.asInstanceOf[D], if (o1.isDefined) o1.get._5 else null.asInstanceOf[E], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], null.asInstanceOf[C], null.asInstanceOf[D], null.asInstanceOf[E], false)
    def go {}
  }
  class Oople6[@specialized(Int,Long,Double) A, B, C, D, E, F](var a: A, var b: B, var c: C, var d: D, var e: E, var f: F, var ok: Boolean = true) extends Oopled6[A,B,C,D,E,F] with OopleA[A] with Pfuple {
    def this(o0: Oople6[A,B,C,D,E,F]) = this(o0.a,o0.b,o0.c,o0.d,o0.e,o0.f,o0.ok)
    def this(o1: Option[(A,B,C,D,E,F)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], if (o1.isDefined) o1.get._3 else null.asInstanceOf[C], if (o1.isDefined) o1.get._4 else null.asInstanceOf[D], if (o1.isDefined) o1.get._5 else null.asInstanceOf[E], if (o1.isDefined) o1.get._6 else null.asInstanceOf[F], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], null.asInstanceOf[C], null.asInstanceOf[D], null.asInstanceOf[E], null.asInstanceOf[F], false)
    def go {}
  }
  class Oople7[@specialized(Int,Long,Double) A, B, C, D, E, F, G](var a: A, var b: B, var c: C, var d: D, var e: E, var f: F, var g: G, var ok: Boolean = true) extends Oopled7[A,B,C,D,E,F,G] with OopleA[A] with Pfuple {
    def this(o0: Oople7[A,B,C,D,E,F,G]) = this(o0.a,o0.b,o0.c,o0.d,o0.e,o0.f,o0.g,o0.ok)
    def this(o1: Option[(A,B,C,D,E,F,G)]) = this(if (o1.isDefined) o1.get._1 else null.asInstanceOf[A], if (o1.isDefined) o1.get._2 else null.asInstanceOf[B], if (o1.isDefined) o1.get._3 else null.asInstanceOf[C], if (o1.isDefined) o1.get._4 else null.asInstanceOf[D], if (o1.isDefined) o1.get._5 else null.asInstanceOf[E], if (o1.isDefined) o1.get._6 else null.asInstanceOf[F], if (o1.isDefined) o1.get._7 else null.asInstanceOf[G], o1.isDefined)
    def this() = this(null.asInstanceOf[A], null.asInstanceOf[B], null.asInstanceOf[C], null.asInstanceOf[D], null.asInstanceOf[E], null.asInstanceOf[F], null.asInstanceOf[G], false)
    def go {}
  }
  object Oo {
    def empty[@specialized A]: Oople1[A] = new Oople1[A]()
    def apply[@specialized A](a0: A) = new Oople1(a0, true)
    def apply[@specialized A](op: Option[A]) = new Oople1[A](op)
    def empty[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B]: Oople2[A,B] = new Oople2[A,B]()
    def apply[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](a0: A, b0: B) = new Oople2(a0, b0, true)
    def apply[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](op: Option[(A,B)]) = new Oople2[A,B](op)
    def empty[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C]: Oople3[A,B,C] = new Oople3[A,B,C]()
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](a0: A, b0: B, c0: C) = new Oople3(a0, b0, c0, true)
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](op: Option[(A,B,C)]) = new Oople3[A,B,C](op)
    def empty[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D]: Oople4[A,B,C,D] = new Oople4[A,B,C,D]()
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](a0: A, b0: B, c0: C, d0: D) = new Oople4(a0, b0, c0, d0, true)
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](op: Option[(A,B,C,D)]) = new Oople4[A,B,C,D](op)
    def empty[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E]: Oople5[A,B,C,D,E] = new Oople5[A,B,C,D,E]()
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](a0: A, b0: B, c0: C, d0: D, e0: E) = new Oople5(a0, b0, c0, d0, e0, true)
    def apply[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](op: Option[(A,B,C,D,E)]) = new Oople5[A,B,C,D,E](op)
    def empty[@specialized(Int,Long,Double) A, B, C, D, E, F]: Oople6[A,B,C,D,E,F] = new Oople6[A,B,C,D,E,F]()
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F](a0: A, b0: B, c0: C, d0: D, e0: E, f0: F) = new Oople6(a0, b0, c0, d0, e0, f0, true)
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F](op: Option[(A,B,C,D,E,F)]) = new Oople6[A,B,C,D,E,F](op)
    def empty[@specialized(Int,Long,Double) A, B, C, D, E, F, G]: Oople7[A,B,C,D,E,F,G] = new Oople7[A,B,C,D,E,F,G]()
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F, G](a0: A, b0: B, c0: C, d0: D, e0: E, f0: F, g0: G) = new Oople7(a0, b0, c0, d0, e0, f0, g0, true)
    def apply[@specialized(Int,Long,Double) A, B, C, D, E, F, G](op: Option[(A,B,C,D,E,F,G)]) = new Oople7[A,B,C,D,E,F,G](op)
  }

  // Implicit conversions to/from tuples
  implicit def muple2_to_tuple2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](m: Muple2[A,B]) = m.tup
  implicit def muple3_to_tuple3[A,B,C](m: Muple3[A,B,C]) = m.tup
  implicit def muple4_to_tuple4[A,B,C,D](m: Muple4[A,B,C,D]) = m.tup
  implicit def muple5_to_tuple5[A,B,C,D,E](m: Muple5[A,B,C,D,E]) = m.tup
  implicit def muple6_to_tuple6[A,B,C,D,E,F](m: Muple6[A,B,C,D,E,F]) = m.tup
  implicit def muple7_to_tuple7[A,B,C,D,E,F,G](m: Muple7[A,B,C,D,E,F,G]) = m.tup
  implicit def tuple2_to_muple2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](t: Tuple2[A,B]) = Mu[A,B](t._1,t._2)
  implicit def tuple3_to_muple3[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](t: Tuple3[A,B,C]) = Mu[A,B,C](t._1,t._2,t._3)
  implicit def tuple4_to_muple4[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](t: Tuple4[A,B,C,D]) = Mu[A,B,C,D](t._1,t._2,t._3,t._4)
  implicit def tuple5_to_muple5[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](t: Tuple5[A,B,C,D,E]) = Mu[A,B,C,D,E](t._1,t._2,t._3,t._4,t._5)
  implicit def tuple6_to_muple6[@specialized(Int,Long,Double) A, B, C, D, E, F](t: Tuple6[A,B,C,D,E,F]) = Mu[A,B,C,D,E,F](t._1,t._2,t._3,t._4,t._5,t._6)
  implicit def tuple7_to_muple7[@specialized(Int,Long,Double) A, B, C, D, E, F, G](t: Tuple7[A,B,C,D,E,F,G]) = Mu[A,B,C,D,E,F,G](t._1,t._2,t._3,t._4,t._5,t._6,t._7)
  implicit def oople2_to_opttuple2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](m: Oople2[A,B]) = m.get
  implicit def oople3_to_opttuple3[A,B,C](m: Oople3[A,B,C]) = m.get
  implicit def oople4_to_opttuple4[A,B,C,D](m: Oople4[A,B,C,D]) = m.get
  implicit def oople5_to_opttuple5[A,B,C,D,E](m: Oople5[A,B,C,D,E]) = m.get
  implicit def oople6_to_opttuple6[A,B,C,D,E,F](m: Oople6[A,B,C,D,E,F]) = m.get
  implicit def oople7_to_opttuple7[A,B,C,D,E,F,G](m: Oople7[A,B,C,D,E,F,G]) = m.get
  implicit def oople1_to_opt[@specialized A](m: Oople1[A]) = m.a
  implicit def opttuple2_to_oople2[@specialized(Boolean,Byte,Short,Char,Int,Long,Float,Double) A, @specialized(Int,Long,Float,Double) B](ot: Option[Tuple2[A,B]]) = Oo(ot)
  implicit def opttuple3_to_oople3[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C](ot: Option[Tuple3[A,B,C]]) = Oo(ot)
  implicit def opttuple4_to_oople4[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D](ot: Option[Tuple4[A,B,C,D]]) = Oo(ot)
  implicit def opttuple5_to_oople5[@specialized(Int,Long,Double) A, @specialized(Int,Long,Double) B, C, D, E](ot: Option[Tuple5[A,B,C,D,E]]) = Oo(ot)
  implicit def opttuple6_to_oople6[@specialized(Int,Long,Double) A, B, C, D, E, F](ot: Option[Tuple6[A,B,C,D,E,F]]) = Oo(ot)
  implicit def opttuple7_to_oople7[@specialized(Int,Long,Double) A, B, C, D, E, F, G](ot: Option[Tuple7[A,B,C,D,E,F,G]]) = Oo(ot)
  implicit def opt_to_oople1[@specialized A](o: Option[A]) = Oo(o)
}
