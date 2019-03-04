// Tri is a ternary sum type for exception/error handling.
// Tri is bulletproof and has a monad for the Good case.
// Copyright Rex Kerr 2012
// This code is available under the BSD 3-Clause License ("BSD New")
// Details at http://www.opensource.org/licenses/BSD-3-Clause

package ichi.core

import scala.util.{Try, Failure, Success}
import scala.util.control.NonFatal
import ichi.flow._

/**
 * `Tri` is a ternary sum type with zero with the exception-bulletproof property.
 * As a practical matter, this means that it can simultaneously fulfill all
 * the roles of `Option`, `Either`, and `Try`.  If you do not need to catch exceptions,
 * use [[May]] instead.  If you do not need a zero, use [[Ok]] instead.
 * 
 * `Tri` has three subclasses:
 *   - [[Good]] holds a desired value
 *   - [[Bad]] holds an alternate value, for example, for error handling
 *   - [[Ugly]] holds `Throwable`s (exceptions)
 * 
 * `Tri` also has a ''zero'', [[Tri$.Zilch]], which is a specific instance
 * of [[Ugly]] holding a pre-generated exception.  This is used in error
 * conditions where there is no information (the `None` case of `Option`, for
 * instance, or undefined values of a partial function in [[collect]]).
 * 
 * `Tri` is ''bulletproof'', meaning that every method that returns a `Tri` is
 * guaranteed not to throw an exception, but instead will package it into an
 * [[Ugly]].  Unrecoverable errors and control-flow exceptions are permitted
 * through (but only those that extend `scala.util.control.ControlThrowable`).
 * Methods that do not return a `Tri` are not protected.  (For instance, [[fold]]
 * will not catch an exception encountered during processing of the `Good` and
 * `Bad` branches.)
 * 
 * Note that for speed, `Tri` uses an indicator function instead of pattern matching:
 * {{{
 *   try { f() } catch { case t if NonFatal(t) => DoStuff }
 * }}}
 * The match form usually only saves five characters but requires an extra object creation.
 */
sealed trait Tri[+V,+P] {
  import Tri.Zilch

  def isGood: Boolean
  def isBad: Boolean
  def isUgly: Boolean
  /** Throws an exception unless this is [[Good]]. */
  def assert: this.type
  /** A desired value; throws a `NoSuchElementException` if not available */
  def value: V
  /** An alternate or problematic value; throws a `NoSuchElementException` if not available */
  def problem: P
  /** Produce a value by running mappings on [[Good]], [[Bad]], or [[Ugly]] cases, as appropriate. */
  def fold[A](f: V => A, g: P => A, h: Throwable => A): A = this match {
    case Good(v) => f(v)
    case Bad(p) => g(p)
    case Ugly(t) => h(t)
  }
  
  def foreach[A](f: V =>A) { this match {
    case Good(v) => f(v)
    case _ =>
  }}
  def map[U](f: V => U): Tri[U,P] = this match {
    case Good(v) => Tri[U,P](f(v))
    case x => x.asInstanceOf[Tri[U,P]]
  }
  /** Map [[Good]] values; if an exception is thrown, produce an [[Ugly]]; if the function is not defined, return [[Tri$.Zilch]] */
  def collect[U](pf: PartialFunction[V,U]): Tri[U,P] = this match {
    case Good(v) =>
      try { if (pf.isDefinedAt(v)) Good(pf(v)) else Zilch }
      catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[U,P]]
  }
  def flatMap[U,Q >: P](f: V => Tri[U,Q]): Tri[U,Q] = this match {
    case Good(v) => try { f(v) } catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[U,Q]]
  }
  def filter(p: V => Boolean): Tri[V,P] = this match {
    case Good(v) => try { if (p(v)) this else Zilch } catch { case t if NonFatal(t) => Ugly(t) }
    case _ => this
  }
  /** Map some good values as bad ones (those caught by the partial function) */
  def reject[Q >: P](pf: PartialFunction[V,Q]): Tri[V,Q] = this match {
    case Good(v) =>
      try { if (pf.isDefinedAt(v)) Bad(pf(v)) else this }
      catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[V,Q]]
  }
  /** Map all good values as bad ones. */
  def rejectAll[Q >: P](f: V => Q): Tri[Nothing,Q] = this match {
    case Good(v) => try { Bad(f(v)) } catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[Nothing,Q]]
  }
  /** Map some bad values as good ones (those where the partial function is defined) */
  def accept[U >: V](pf: PartialFunction[P,U]): Tri[U,P] = this match {
    case Bad(p) =>
      try { if (pf.isDefinedAt(p)) Good(pf(p)) else this }
      catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[U,P]]
  }
  /** Map all bad values to good ones. */
  def acceptAll[U >: V](f: P => U): Tri[U,Nothing] = this match {
    case Bad(p) => try { Good(f(p)) } catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[U,Nothing]]
  }
  /** Convert some exceptions into bad values (where partial function is defined) */
  def explain[Q >: P](pf: PartialFunction[Throwable,Q]): Tri[V,Q] = this match {
    case Ugly(u) =>
      try { if (pf.isDefinedAt(u)) Bad(pf(u)) else this }
      catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[V,Q]]
  }
  /** Convert all exceptions into bad values */
  def explainAll[Q >: P](f: Throwable => Q): Tri[V,Q] = this match {
    case Ugly(u) => try { Bad(f(u)) } catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[V,Q]]
  }
  /** Map some exceptions into good values (where partial function is defined) */
  def recover[U >: V](pf: PartialFunction[Throwable,U]): Tri[U,P] = this match {
    case Ugly(u) =>
      try { if (pf.isDefinedAt(u)) Good(pf(u)) else this }
      catch { case t if NonFatal(t) => Ugly(t) }
    case x => x.asInstanceOf[Tri[U,P]]
  }
  /** Map all exceptions into good values */
  def recoverAll[U >: V](f: Throwable => U): Tri[U,P] = this match {
    case Ugly(u) => Tri[U,P](f(u))
    case x => x.asInstanceOf[Tri[U,P]]
  }
  /** Collapse everything that isn't [[Good]] into [[Tri$.Zilch]] */
  def simplify: Tri[V,Nothing] = if (isGood) this.asInstanceOf[Tri[V,Nothing]] else Zilch
  /** Return a good value or recover with a default value; use [[fold]] to compute a good value from problematic values or exceptions */
  def getOr[U >: V](f: => U) = this match {
    case Good(v) => v
    case _ => f
  }

  /** Convert [[Good]] values into `Some`; discard everything else as `None` */
  def toOption = this match {
    case Good(v) => Some(v)
    case _ => None
  }
  /** Convert [[Good]] values to `Right` and [[Bad]] ones to `Left`.  An [[Ugly]] will throw its exception--consider using [[explainAll]] first. */
  def toEither = this match {
    case Good(v) => Right(v)
    case _ => Left(problem)  // Let Ugly throw its own exception if we're Ugly
  }
  /** Convert [[Good]] values to `Success`, [[Ugly]] ones to `Failure`, and package [[Bad]] values inside [[ExceptionWithPayload]] and place it in a `Failure` */
  def toTry = this match {
    case Good(v) => Success(v)
    case Bad(p) => Failure(new ExceptionWithPayload(p))
    case Ugly(t) => Failure(t)
  }
  /** Convert [[Good]] values to `Yes` and [[Bad]] ones to `No`.  [[Ugly]] will throw. */
  def toOk = this match {
    case Good(v) => Yes(v)
    case _ => No(problem)
  }
  /** Convert [[Good]] values to `Yes` and [[Bad]] ones to `No`; convert [[Ugly]] to `No` with the specified function. */
  def toOk[Q>:P](f: Throwable => Q) = this match {
    case Good(v) => Yes(v)
    case Bad(p) => No(p)
    case Ugly(t) => No(f(t))
  }
  /** Drop all exceptions to [[Nay]], and rewrap [[Good]] as [[Yea]] and [[Bad]] as [[Alt]]. */
  def toMay = this match {
    case Good(v) => Yea(v)
    case Bad(p) => Alt(p)
    case Ugly(t) => Nay
  }
  
  /** Iterate over one [[Good]] value if there is any; otherwise, the iterator is empty */
  def iterator = this match {
    case Good(v) => Iterator(v)
    case _ => Iterator.empty
  }
  /** A one-element [[Good]] value, or `Nil` if the value is not `Good` */
  def toList = this match {
    case Good(v) => List(v) :: Nil
    case _ => Nil
  }
}

object Tri {
  /** Produce a value wrapped in a [[Good]], or an [[Ugly]] containing an exception if something went wrong; `null` values are mapped to [[Zilch]], not interpreted as [[Good]]. */
  def apply[V,P](v: => V): Tri[V,P] = try { val v0 = v; if (v0 == null) Zilch else Good(v0) } catch { case t if NonFatal(t) => Ugly(t) }
  /** Just [[Zilch]], downcast as requested. */
  def empty[V,P]: Tri[V,P] = Zilch
  /** Repack `Some` into [[Good]] and `None` into [[Zilch]] */
  def from[A](o: Option[A]): Tri[A,Nothing] = o match { case Some(a) => Good(a); case _ => Zilch }
  /** Repack `Right` into [[Good]] and `Left` into [[Bad]] */
  def from[L,R](e: Either[L,R]): Tri[R,L] = e match { case Left(l) => Bad(l); case Right(r) => Good(r) }
  /** Repack `Success` into [[Good]] and `Failure` into [[Ugly]] */
  def from[T](t: scala.util.Try[T]): Tri[T,Nothing] = t match { case Success(s) => Good(s); case Failure(f) => Ugly(f) }
  /** Repack `Yes` into [[Good]], `No` into [[Bad]] */
  def from[N,Y](k: Ok[N,Y]): Tri[Y,N] = k match { case Yes(y) => Good(y); case No(n) => Bad(n) }
  /** Repack [[Yea]] into [[Good]], [[Alt]] into [[Bad]], and [[Nay]] into [[Zilch]] */
  def from[A,B](m: May[A,B]): Tri[A,B] = m match { case Yea(a) => Good(a); case Alt(b) => Bad(b); case _ => Zilch }
  /** A pre-formed, stack-trace-free, non-control sentinel exception to use for the [[Ugly]]-zero. */
  object ConditionNotMet extends scala.util.control.NoStackTrace
  /** The zero of [[Tri]] (a specific instance of [[Ugly]]; it contains a sentinel exception). */
  val Zilch = Ugly(ConditionNotMet)
}

/** A class to wrap good values. */
final case class Good[+V](/** A desired value (which actually exists) */value: V) extends Tri[V,Nothing] {
  def isGood = true
  def isBad = false
  def isUgly = false
  /** Always succeeds; class returns itself */
  def assert = this
  /** Throws NoSuchElementException */
  def problem = throw new NoSuchElementException("Request for problem but value is Good")
}

/** A class to wrap problematic or error values. */
final case class Bad[P](/** An alternative or error value (which is what we have this time) */problem: P) extends Tri[Nothing,P] {
  def isGood = false
  def isBad = true
  def isUgly = false
  /** Always fails with a NoSuchElementException */
  def assert = throw new NoSuchElementException("Assert in Bad")
  /** Throws NoSuchElementException */
  def value = throw new NoSuchElementException("Request for value but there is a problem")
}

/** A class to wrap exceptions that have been caught but not processed to something more useful. */
final case class Ugly(/** The exception (must pattern match to obtain; not part of the [[Tri]] trait) */thrown: Throwable) extends Tri[Nothing,Nothing] {
  def isGood = false
  def isBad = false
  def isUgly = true
  /** Always fails with a NoSuchElementException */
  def assert = if (thrown != Tri.Zilch) throw thrown else throw new NoSuchElementException("Attempt to throw Zilch")
  /** Throws NoSuchElementException */
  def value = if (thrown != Tri.Zilch) throw thrown else throw new NoSuchElementException("Attempt to throw Zilch")
  /** Throws NoSuchElementException */
  def problem = if (thrown != Tri.Zilch) throw thrown else throw new NoSuchElementException("Attempt to throw Zilch")
}

/** A wrapper class that makes it possible to deliver [[Bad]] values through `Try` (albeit with loss of type information) */
class ExceptionWithPayload[P](val problem: P) extends Exception {
  override def fillInStackTrace = this
}

/** Implicit conversions for [[Tri]] */
trait TriConverters {
  implicit class ConvertOptionToTri[V](o: Option[V]) { 
    def toTri = Tri.from(o)
    def goodOr[P](p: P) = o match { case Some(x) => Good(x); case None => Bad(p) }
  }
  implicit class ConvertEitherToTri[V,P](e: Either[P,V]) { def toTri = Tri.from(e) }
  implicit class ConvertTryToTri[T](t: Try[T]) { def toTri = Tri.from(t) }
  implicit class ConvertOkToTri[N,Y](k: Ok[N,Y]) { def toTri = Tri.from(k) }
  implicit class ConvertMayToTri[A,B](m: May[A,B]) { def toTri = Tri.from(m) }
}

