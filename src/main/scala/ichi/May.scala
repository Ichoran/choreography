// May is a cross between Option and right-biased Either
// Copyright Rex Kerr 2012
// This code is available under the BSD 3-Clause License ("BSD New")
// Details at http://www.opensource.org/licenses/BSD-3-Clause

package ichi.core

import language.implicitConversions
import scala.util.{Try, Success, Failure}
import scala.reflect.ClassTag
import ichi.flow._

/** The goals of May are to
 *   - Deliver the capabilities of both Option and right-biased Either
 *   - Provide full for-comprehension compatibility
 *   - Allow arbitrary state for the error condtion
 *   - Not require any state for the error condition
 *   - Avoid overly messy inferred types where possible
 *   - Work nicely with collections, Option, Either, and Try
 * 
 * May has three subclasses:
 *   - [[Yea]] contains the desired value (first type argument)
 *   - [[Alt]] contains an alternate value (second type argument)
 *   - [[Nay]] represents the empty case where neither desired or alternate values exist
 * 
 * Example usage:
 * {{{
 *   def main(args: Array[String]) {
 *     val Digits = """(\d+)""".r
 *     val arg = May.from( args.find(_.startsWith("n=")) ).map(_.drop(2)).flatMap{
 *       case Digits(s) => May.catchAll( s.toInt ).altMap(_ => s)
 *       case x => Alt(x)
 *     }
 *     println( arg.fold("Nay argument")("Improperly formatted number: "+_)("Yay "*_) )
 *   }
 * }}}
 * 
 * Example output:
 * {{{
 * scala> main(Array("5"))
 * Nay argument
 * 
 * scala> main(Array("n=5"))
 * Yay Yay Yay Yay Yay 
 * 
 * scala> main(Array("n=5f"))
 * Improperly formatted number: 5f
 * }}}
 */
sealed trait May[+A,+B] { self =>
  // Low-level handling
  def isYea: Boolean
  def isAlt: Boolean
  def isNay: Boolean
  /** The desired value, if available. */
  def yea: A
  /** The alternate value, if available. */
  def alt: B
  
  // Fundamental operation
  /** Map to an output: a default (first), a mapping of the alternate value (second), or a mapping of the desired value (third) */
  def fold[Z](zero: => Z)(f: B=>Z)(g: A=>Z): Z = this match {
    case Yea(a) => g(a)
    case Alt(b) => f(b)
    case Nay => zero
  }
  
  // Core methods used by for-comprehensions
  def foreach[Y](f: A=>Y) { if (isYea) f(yea) }
  def map[Y](f: A=>Y) = if (isYea) Yea(f(yea)) else this.asInstanceOf[May[Y,B]]
  def flatMap[Y,Z>:B](f: A=>May[Y,Z]) = if (isYea) f(yea) else this.asInstanceOf[May[Y,Z]]
  def filter(p: A=>Boolean) = if (isYea && !p(yea)) Nay else this
  def withFilter(p: A=>Boolean): WithFilter = new WithFilter(p)
  class WithFilter(p: A=>Boolean) {
    def map[Y](f: A=>Y) = self match {
      case Yea(a) => if (p(a)) Yea(f(a)) else Nay
      case _ => self.asInstanceOf[May[Y,B]]
    }
    def flatMap[Y,Z>:B](f: A=>May[Y,Z]) = self match {
      case Yea(a) => if (p(a)) f(a) else Nay
      case _ => self.asInstanceOf[May[Y,Z]]
    }
    def foreach[Y](f: A=>Y) { self match { case Yea(a) => if (p(a)) f(a); case _ => } }
    def withFilter(q: A=>Boolean) = new WithFilter(x => p(x) && q(x))
  }
  
  // Additional yea-biased methods
  def collect[Y](pf: PartialFunction[A,Y]) = this match {
    case Yea(a) => if (pf.isDefinedAt(a)) Yea(pf(a)) else Nay
    case _ => this.asInstanceOf[May[Y,B]]
  }
  def exists(p: A=>Boolean) = this match { case Yea(a) => p(a); case _ => false }
  def filterNayt(p: A=>Boolean) = this match { case Yea(a) if p(a) => Nay; case _ => this }
  def forall(p: A=>Boolean) = !isYea || p(yea)
  def flatten[Y>:A,Z](implicit ev: <:<[A, May[Y,Z]]) = if (isYea) ev(yea) else this.asInstanceOf[May[Y,Z]]

  /** Retrieve the desired (yea) value, convert the alternate, or use a default */
  def getOr[Z>:A](default: => Z)(f: B => Z) = fold(default)(f)(identity)

  /** Retrieve the desired (yea) value, or return a default for both alternate and empty cases */
  def yeaOr[Z>:A](default: => Z) = if (isYea) yea else default
  
  // Alt-biased methods
  def altForeach[Z](f: B => Z) { if (isAlt) f(alt) }
  def altMap[Z](f: B=>Z): May[A,Z] = { if (isAlt) Alt(f(alt)) else this.asInstanceOf[May[A,Z]] }
  def altFlatMap[Y>:A,Z](f: B=>May[Y,Z]) = if (isAlt) f(alt) else this.asInstanceOf[May[Y,Z]]
  def altFilter(p: B=>Boolean) = { if (isAlt && !p(alt)) Nay else this }
  def altCollect[Z](pf: PartialFunction[B,Z]) = this match {
    case Alt(b) => if (pf.isDefinedAt(b)) Alt(pf(b)) else Nay
    case _ => this.asInstanceOf[May[A,Z]]
  }
  
  // Yea-alt-nay conversion methods
  /** Swaps which value is desired and which is alternate */
  def swap = this match {
    case Yea(a) => Alt(a)
    case Alt(b) => Yea(b)
    case Nay => Nay
  }
  /** Discards some desired values to alternate (where partial function is defined) */
  def reject[Z>:B](pf: PartialFunction[A,Z]) = this match {
    case Yea(b) if (pf.isDefinedAt(b)) => Alt(pf(b))
    case _ => this.asInstanceOf[May[A,Z]]
  }
  /** Discards some desired values to alternate (when option exists) */
  def rejectOpt[Z>:B](fop: A=>Option[Z]): May[A,Z]  = {
    if (isYea) {
      val o = fop(yea);
      if (o.isDefined) return Alt(o.get)
    }
    this.asInstanceOf[May[A,Z]]
  }
  /** Discards all desired values, mapping them as alternates */
  def rejectAll[Z>:B](f: A => Z): May[Nothing,Z] = this match {
    case Yea(a) => Alt(f(a))
    case _ => this.asInstanceOf[May[Nothing,Z]]
  }
  /** Promotes some alternate values to desired (where partial function is defined) */
  def accept[Y>:A](pf: PartialFunction[B,Y]) = this match {
    case Alt(b) if (pf.isDefinedAt(b)) => Yea(pf(b))
    case _ => this.asInstanceOf[May[Y,B]]
  }
  /** Promotes some alternate values to desored (when option exists) */
  def acceptOpt[Y>:A](fop: B=>Option[Y]): May[Y,B] = {
    if (isAlt) {
      val o = fop(alt)
      if (o.isDefined) return Yea(o.get)
    }
    this.asInstanceOf[May[Y,B]]
  }
  /** Promotes all alternate values, mapping them as desired */
  def acceptAll[Y>:A](f: B=>Y): May[Y,Nothing] = this match { 
    case Alt(b) => Yea(f(b))
    case _ => this.asInstanceOf[May[Y,Nothing]]
  }
  /** Discards all desired values, leaving only [[Nay]] in their place */
  def dropYea = if (isYea) Nay else this
  /** Discards all alternate values, leaving only [[Nay]] in their place */
  def dropAlt = if (isAlt) Nay else this
  /** Fill in a default alternative value in place of empty */
  def orAlt[Z>:B](reason: => Z) = this match {
    case Nay => Alt(reason)
    case _ => this.asInstanceOf[May[A,Z]]
  }
  /** Fill in a default desired value in place of empty */
  def orYea[Y>:A](value: => Y) = this match {
    case Nay => Yea(value)
    case _ => this.asInstanceOf[May[Y,B]]
  }
  
  // Option, Either, and Try interoperability
  def toOption = this match { case Yea(b) => Some(b); case _ => None }
  /** Map desired values through an `Option`, discarding those mapped to `None` */
  def mapOpt[Y](f: A=>Option[Y]) = {
    if (isYea) f(yea) match { case Some(y) => Yea(y); case _ => Nay }
    else this.asInstanceOf[May[Y,B]]
  }
  def toEither[Z>:B](zero: => Z) = this match { case Yea(a) => Right(a); case Alt(b) => Left(b); case Nay => Left(zero) }
  /** Map desired values through an either, with `Left` results becoming alternates and `Right` results becoming desired */
  def mapEither[Y,Z>:B](f: A=>Either[Z,Y]) = {
    if (isYea) { f(yea) match {
      case Right(y) => Yea(y)
      case Left(z) => Alt(z)
    }}
    else this.asInstanceOf[May[Y,Z]]
  }
  def toTry = { try { Success(yea) } catch { case nse: NoSuchElementException => Failure(nse) } }
  /** Map desired values through a `Try`, discarding `Failure`; use [[flatMap]] (e.g. with `from`) to collect failures as alternatives. */
  def mapTry[Y](f: A=>Try[Y]) = {
    if (isYea) f(yea) match { case Success(y) => Yea(y); case _ => Nay }
    else this.asInstanceOf[May[Y,B]]
  }
  /** Convert empty to `None`, alternate values to `Some(Left(_))`, and desired values to `Some(Right(_))` */
  def toOptEither: Option[Either[B,A]] = this match { case Yea(a) => Some(Right(a)); case Alt(b) => Some(Left(b)); case Nay => None }
  /** Convert desired values to `Right(_)`, alternate values to `Left(Some(_))`, and empty to `Left(None)` */
  def toEitherOpt: Either[Option[B],A] = this match { case Yea(a) => Right(a); case Alt(b) => Left(Some(b)); case Nay => Left(None) }
  /** Convert to Ok */
  def toOk[Z>:B](default: => Z) = this match { case Yea(a) => Yes(a); case Alt(b) => No(b); case Nay => No(default) }
  
  // Collections interoperability
  /** Iterate over the desired value, if any */
  def iterator = if (isYea) Iterator(yea) else Iterator()
  /** Convert the desired value to a list of length 1 */
  def toList = if (isYea) List(yea) else Nil
}

object May {
  /** Wrap a value as a desired value; convert `null` to [[Nay]] */
  def apply[A](a: A): May[A,Nothing] = if (a == null) Nay else Yea(a)
  /** Wrap a value as an alternate value; convert `null` to [[Nay]] */
  def alt[B](b: B): May[Nothing,B] = if (b == null) Nay else Alt(b)
  /** Convert an `Option` to [[May]] (desired or empty only) */
  def from[A](o: Option[A]): May[A,Nothing] = if (o.isDefined) Yea(o.get) else Nay
  /** Convert a `Try` to [[May]] (Exceptions in `Failure`s are stored as alternate values) */
  def from[A](t: Try[A]): May[A,Throwable] = t match { case Success(s) => Yea(s); case Failure(f) => Alt(f) }
  /** Convert an `Either` to [[May]] (Right is desired, Left is alternate, empty is not used) */
  def from[A,B](e: Either[A,B]): May[B,A] = e match { case Right(a) => Yea(a); case Left(b) => Alt(b) }
  /** Convert an `Ok` to [[May]] (Yes is desired, No is alternate, empty is not used) */
  def from[N,Y](k: Ok[N,Y]): May[Y,N] = k match { case Yes(y) => Yea(y); case No(n) => Alt(n) }
  /** [[Nay]] with type widened if desired */
  def empty[A,B]: May[A,B] = Nay
  /** Inverse of [[May.toOptEither]] */
  def unpack[A,B](o: Option[Either[B,A]]) = if (!o.isDefined) Nay else from(o.get)
  /** Inverse of [[May.toEitherOpt]] */
  def unpack[A,B](e: Either[Option[B],A]) = e match { case Right(a) => Yea(a); case Left(Some(b)) => Alt(b); case _ => Nay }

  /** Catch exceptions using `Try`, then return a [[Yea]] on success, or place the exception in an [[Alt]] */
  def catchAll[A](a: => A): May[A,Throwable] = from(Try(a))
  /** Catch only the exception listed in the type argument (success is [[Yea]]], exceptions go into [[Alt]]); let the others fall through. */
  def catching[B <: Throwable](implicit tg: ClassTag[B]) = new Catcher(tg)
  /** Define one or more blocks to package exceptions into [[Alt]] */
  def catchWith[B](pf: PartialFunction[Throwable,B]) = new PartialCatcher(pf)
  /** Catches specific exceptions for packaging into [[May]]. */
  class Catcher[B](tg: ClassTag[B]) {
    /** Execute a code block, putting the designated exception into an [[Alt]] */
    def may[A](a: => A) = try { May(a) } catch { case e if tg.runtimeClass.isAssignableFrom(e.getClass) => Alt(e.asInstanceOf[A]) }
  }
  /** Catches and remaps subsets of exceptions, placing them into an [[Alt]] */
  class PartialCatcher[B](pf: PartialFunction[Throwable,B]) {
    /** Execute a code block, putting any caught exceptions into an [[Alt]] */
    def may[A](a: => A): May[A,B] = try { May(a) } catch (pf andThen (b => Alt(b)))
    /** Catch and map more exceptions (original mapping acts first; this is a fallback) */
    def or[Z>:B](pf2: PartialFunction[Throwable,Z]) = new PartialCatcher[Z](pf orElse pf2)
  }
}

/** Holds a desired value; one of three possibilities for [[May]]
 */
final case class Yea[+A](/** The desired value */yea: A) extends May[A,Nothing] {
  def isYea = true
  def isAlt = false
  def isNay = false
  /** throws NoSuchElementException */
  def alt = throw new NoSuchElementException("Yea.alt")
  /** Widens type on alternate value (`Nothing` by default) while downcasting to [[May]] */
  def may[B] = this.asInstanceOf[May[A,B]]
}

/** Holds an alternate value, perhaps for handling exceptional or error conditions; one of three possibilities for [[May]]
 */
final case class Alt[+B](/** The alternate value */alt: B) extends May[Nothing,B] {
  def isYea = false
  def isAlt = true
  def isNay = false
  /** throws NoSuchElementException */
  def yea = throw new NoSuchElementException("Alt.yea")
  /** Widens type on desired value (`Nothing` by default) while downcasting to [[May]] */
  def may[A] = this.asInstanceOf[May[A,B]]
}

/** Holds no value (empty); one of three possibilities for [[May]]
 */
case object Nay extends May[Nothing,Nothing] {
  def isYea = false
  def isAlt = false
  def isNay = true
  /** throws NoSuchElementException */
  def yea = throw new NoSuchElementException("Nay.yea")
  /** throws NoSuchElementException */
  def alt = throw new NoSuchElementException("Nay.alt")
  /** Widens type on desired and alternate values (`Nothing` by default) while downcasting to [[May]] */
  def may[A,B] = this.asInstanceOf[May[A,B]]
}

/** Implicit conversions for [[May]]
 */
trait MayConverters {
  implicit class ConvertOptionToMay[B](o: Option[B]) { def toMay = May.from(o) }
  implicit class ConvertEitherToMay[A,B](e: Either[A,B]) { def toMay = May.from(e) }
  implicit class ConvertOkToMay[A,B](k: Ok[A,B]) { def toMay = May.from(k) }
  
  implicit def may_to_option[A,B](m: May[A,B]) = m.toOption
  implicit def option_to_may[A](o: Option[A]) = May.from(o)
  implicit def may_to_collection[A,B](m: May[A,B]) = m.toList
}


/*
object HasExample extends App {
  import HasConverters._
// def f(s: String) = for (d <- Has.catchAll(s.toDouble) if !d.isNaN) yield -d
  val numbers = List("5.0","chicken","-1","NaN","1.4e-1")
  def exname(e: Exception) = e.getClass.getName.split('.').last
  val hs = numbers.map{ s =>
    val namedErrors = Has.catchAll(s.toDouble).pleaMap(exname)
    namedErrors.filter(! _.isNaN).reject{ case x if x<0 => "Negative" }
  }
  val answer = hs.flatten
  println("Given:     "+numbers.mkString(", "))
  println("I found:   "+hs.mkString(", "))
  println("producing: "+answer.mkString(", "))
  def validate(input: Int) = {
    val checked = if (input < 1000) Yea(input) else Um("Too big")
    val map = Map(500 -> "fish")
    val lookup = for (i <- checked; x <- map.get(i)) yield i
    lookup.pleaFilter{ s => println("Error: "+s); false }.toOption
  }
  List(500, 2000, 0).map{ n =>
    println
    println("Validating "+n+" gives "+validate(n))
  }
}
*/

/* Output of scala krl.utility.HasExample
 * Given:     5.0, chicken, -1, NaN, 1.4e-1
 * I found:   Yea(5.0), Um(NumberFormatException), Um(Negative), Nay, Yea(0.14)
 * producing: 5.0, 0.14
 * 
 * Validating 500 gives Some(500)
 * 
 * Error: Too big
 * Validating 2000 gives None
 * 
 * Validating 0 gives None
 */
