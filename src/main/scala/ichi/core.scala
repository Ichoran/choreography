package ichi

package object core extends MayConverters with TriConverters {
  type Tag[A] = scala.reflect.ClassTag[A]
  type tco = scala.annotation.tailrec
  import flow._

  // Generic control/looping--note that Function1 is only specialized on these four, so no point doing others
  def cfor[@specialized(Int, Long, Float, Double) T](zero: T, okay: T=>Boolean, succ: T=>T)(act: T => Unit) {
    var i = zero
    while (okay(i)) {
      act(i)
      i = succ(i)
    }
  }
  
  def optIn[A](b: Boolean)(f: => A) = if (b) Some(f) else None
  
  def lots[F](n: Int)(f: => F): F = if (n>1) { f; lots(n-1)(f) } else f
  
  def cleanly[A,B](resource: => A)(cleanup: A => Unit)(code: A => B): Ok[Throwable,B] = safe {
    val r = resource
    try { code(r) } finally { cleanup(r) }
  }
  
  // Extra-simple counter which works like i++ in C
  class Incer(i0: Int = 0) {
    private[this] var i = i0
    def apply() = i
    def ++ = { val j = i; i += 1; j }
  }
  
  
  // Output utilities
  object Print {
    def me[A](a: A) = {
      a match {
        case aa: Array[_] => println(aa.deep)
        case _ => println(a)
      }
      a
    }
    
    def meWith[A,B](b: B)(a: A) = { me(b); me(a) }
    
    // Quick way to dump text to a file
    def toFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
      val p = new java.io.PrintWriter(f)
      try { op(p) } finally { p.close() }
    }
    def toFile(s: String)(op: java.io.PrintWriter => Unit) { toFile(new java.io.File(s))(op) }
  }
  
  
  // Specialized option-like mutable container
  class Vx[@specialized A] {
    var value: A = _
    var ok: Boolean = false
    def no = { ok = false; this }
    def yes = { ok = true; this }
    def blank = { ok = false; value = null.asInstanceOf[A]; this }
    def set(a: A) = { ok = true; value = a; this }
    def grab(implicit oops: Oops) = if (ok) value else oops
    def getOr(a: => A) = if (ok) value else a
    def getOrSet(a: => A) = { if (!ok) { set(a) }; value }
    def apply() = if (ok) value else throw new java.util.NoSuchElementException("Vx")
    def :=(a: A) { ok = true; value = a }
    def ap(f: A => A) = { if (ok) { value = f(value) }; this }
    def op(f: A => Unit) = { if (ok) { f(value) }; this }
    def |>[@specialized B](g: A => B) = { val v = new Vx[B]; if (ok) { v := g(value) }; v }
    def -->[@specialized B](b: => B, g: A => B) = if (ok) g(value) else b
    def filt(f: A => Boolean) = { if (ok) { ok = f(value) }; this }
    def check(f: A => Boolean) = ok && f(value)
    def opt = if (ok) Some(value) else None
    def vx = this
    def copy = { val v = new Vx[A]; if (ok) { v := value }; v }
    override def equals(a: Any) = (a == value)
    override def toString = if (ok) "<"+value.toString+">" else "<_>"
    override def hashCode = value.##
  }
  object Vx {
    def apply[@specialized A](oa: Option[A]) = { val v = new Vx[A]; if (oa.isDefined) { v := oa.get }; v }
    def apply[@specialized A](a: A) = { val v = new Vx[A]; v := a; v }
    def empty[@specialized A] = new Vx[A]
  }
  class VxFromOption[@specialized A](oa: Option[A]) {
    def toVx = Vx(oa)
  }
  implicit def varex_from_option[@specialized A](oa: Option[A]) = new VxFromOption(oa)
  implicit def varex_to_option[@specialized A](v: Vx[A]) = v.opt
  
  // These can be placed in case classes without changing equality, sort of like a comment
  class Caseless[A](private[core] val a: A) {
    override def hashCode = 0
    override def equals(o: Any) = o.isInstanceOf[Caseless[_]]
    override def toString = "/*"+a.asInstanceOf[AnyRef].getClass.getName+"*/"
  }
  implicit def anything_is_caseless[A](a: A) = new Caseless(a)
  implicit def caseless_is_anything[A](c: Caseless[A]) = c.a
  
  // Both mutable and invisible to case classes
  class Volatile[A](private[core] var a: A) {
    def apply() = a
    def update(aa: A) { a = aa }
    def advance(f: A=>A) { a = f(a) }
    override def hashCode = 0
    override def equals(o: Any) = o.isInstanceOf[Volatile[_]]
    override def toString = "/~"+a.asInstanceOf[AnyRef].getClass.getName+"~/"
  }
  object Volatile {
    def apply[A](a: A) = new Volatile(a)
  }
  implicit def volatile_is_anything[A](v: Volatile[A]) = v.a
  
  // Secretly (and mutably) holds a second object but looks just like the first one
  class Bundled[A,B](val value: A) {
    var bundle: Option[B] = None
    def empty[C] = new Bundled[A,C](value)
    override def hashCode = value.hashCode
    override def equals(o: Any) = o equals value
    override def toString = value.toString
  }
  object Bundled {
    def apply[A](a: A) = new Bundled[A,Nothing](a)
    def apply[A,B](a: A, b: B) = { val bun = new Bundled[A,B](a); bun.bundle = Some(b); bun }
  }
  implicit def unbundle_anything[A,B](b: Bundled[A,B]) = b.value
  implicit def bundle_anything[A,B](a: A) = new Bundled[A,B](a)
  
  // Use this to create class-specific implicits that won't conflict with each other (A=implicit, B=class)
  class Implicated[A,B](private[core] val a: A, b: Class[B]) { }
  implicit def implicated_is_anything[A,B](i: Implicated[A,B]) = i.a
  
  // Use this as a general way to defer a computation but cache the result.
  class Lazy[A](gen: => A) {
    lazy val value = gen
    def map[B](f: A => B) = Lazy(f(value))
    def flatMap[B](f: A => Lazy[B]) = f(value)
    def foreach[B](f: A => B) { f(value) }
  }
  object Lazy {
    def apply[A](gen: => A) = new Lazy(gen)
  }
  
  // Use this to cache expensive computations that are cleared when memory gets especially tight (via SoftReference); not thread-safe
  class Soft[T,U](t: T)(gen: T => U) {
    private[this] var cache = new java.lang.ref.SoftReference(gen(t))
    def apply(): U = {
      var u = cache.get()
      if (u==null) {
        u = gen(t)
        cache = new java.lang.ref.SoftReference(u)
      }
      u
    }
  }
  object Soft {
    def apply[T,U](t: T)(gen: T => U) = new Soft(t)(gen)
  }
  
  
  // Generic methods that should be on every object
  implicit class UtilityWrapper[A](val a: A) extends AnyVal {
    def zap[Z](f: A=>Z) = f(a)
    def |>[Z](f: A=>Z) = f(a)
    def rezap[Z,Y](f: A=>Z)(g: (A,Z)=>Y) = g(a,f(a))
    def ||>[Z,Y](f: A=>Z)(g: (A,Z)=>Y) = g(a,f(a))
    def yap(f: => Any) = { f; a }
    def tap(f: A => Any) = { f(a); a }
    def taps(ff: (A => Any)*) = { ff.foreach(_(a)); a }
    def also[Z](f: A=>Z) = (a, f(a))
    def partial(pf: PartialFunction[A,A]) = if (pf.isDefinedAt(a)) pf(a) else a
    def when(f: A=>Boolean) = if (f(a)) Some(a) else None
    def fixup(p: A=>Boolean)(a0: => A) = if (p(a)) a0 else a
    def fixby(p: A=>Boolean)(f: A => A) = if (p(a)) f(a) else a
    def tidily[Z](g: A=>Any)(f: A=>Z) = try { f(a) } finally { g(a) }
    def must(f: A=>Boolean)(implicit oops: Oops) = if (f(a)) a else oops
    //def probably[B](f: A=>B): Option[B] = try { Some(f(a)) } catch { case Nope => None }
    def generate[B](f: A => (Option[B],A)): (Seq[B],A) = {
      val bs = Vector.newBuilder[B]
      var ai = a
      var more = true
      while (more) {
        val x = f(ai)
        if (x._1.isEmpty) more = false
        else {
          bs += x._1.get
          ai = x._2
        }
      }
      (bs.result,ai)
    }
  }
  
  // Even more methods on strings (beyond what Scala already adds)
  implicit class RicherString(val s: String) extends AnyVal {
    def file = new java.io.File(s)
    def token(c: Char) = {
      val i = s.indexOf(c)
      if (i < 0) (s,"")
      else {
        var j = i+1
        while (j < s.length && s.charAt(j)==c) j += 1
        (s.substring(0,i), if (j<s.length) s.substring(j) else "")
      }
    }
    def process[A](pre: String)(f: String => A): Option[A] = if (s.startsWith(pre)) Some(f(s.substring(pre.length))) else None
    def processable[A](pre: String)(f: String => Option[A]): Option[A] = if (s.startsWith(pre)) f(s.substring(pre.length)) else None
    def processes[A](items: (String, String => A)*): Option[A] = {
      items.iterator.dropWhile{ case (p,f) => !s.startsWith(p) }.map{ case (p,f) => f(s.substring(p.length)) }.take(1).toList.headOption
    }
    def processables[A](items: (String, String => Option[A])*): Option[A] = {
      items.iterator.dropWhile{ case (p,f) => !s.startsWith(p) }.map{ case (p,f) => f(s.substring(p.length)) }.take(1).toList.flatten.headOption
    }
    def procablechain[A](items: (String, String => Option[A])*): Option[A] = {
      items.iterator.dropWhile{ case (p,f) => !s.startsWith(p) }.flatMap{ case (p,f) => f(s.substring(p.length)) }.take(1).toList.headOption
    }
  }
  
  // Typesafer casting
  def getAs[A](x: Any)(implicit tg: Tag[A]): Option[A] = {
    (x match {
      case u: Unit => if (tg.runtimeClass == classOf[Unit]) Some(u) else None
      case z: Boolean => if (tg.runtimeClass == classOf[Boolean]) Some(z) else None
      case b: Byte => if (tg.runtimeClass == classOf[Byte]) Some(b) else None
      case s: Short => if (tg.runtimeClass == classOf[Short]) Some(s) else None
      case c: Char => if (tg.runtimeClass == classOf[Char]) Some(c) else None
      case i: Int => if (tg.runtimeClass == classOf[Int]) Some(i) else None
      case l: Long => if (tg.runtimeClass == classOf[Long]) Some(l) else None
      case f: Float => if (tg.runtimeClass == classOf[Float]) Some(f) else None
      case d: Double => if (tg.runtimeClass == classOf[Double]) Some(d) else None
      case _ =>
        val a = x.asInstanceOf[AnyRef]  // Need to handle null this way or we get a MatchError
        if ((a ne null) && tg.runtimeClass.isAssignableFrom(a.getClass)) Some(a) else None
    }).asInstanceOf[Option[A]]
  }
  
  // Reference equality on Any
  def referentiallyEquals[A](x: A, y: Any) = y match {
    case Unit | Boolean | Byte | Short | Char | Int | Long | Float | Double => y == x
    case _ => x.asInstanceOf[AnyRef] eq y.asInstanceOf[AnyRef]
  }
  
  // Methods for tuples
  implicit class PairWrapper[A,B](val ab: (A,B)) extends AnyVal {
    def f1[Z](f: A=>Z) = (f(ab._1), ab._2)
    def f2[Z](f: B=>Z) = (ab._1, f(ab._2))
    def each[Y,Z](fl: A=>Y, fr: B=>Z) = (fl(ab._1),fr(ab._2))
    def idmap[C, A1 >: A <: C, B1 >: B <: C, Z](f: C=>Z) = (f(ab._1.asInstanceOf[C]),f(ab._2.asInstanceOf[C]))
    def fold[Z](f: (A,B)=>Z) = f(ab._1,ab._2)
    def also[Z](f: (A,B)=>Z) = (ab._1, ab._2, f(ab._1,ab._2))
  }
  
  implicit class SamePairWrapper[A](val aa: (A,A)) extends AnyVal {
    def same[Z](f: A => Z) = (f(aa._1), f(aa._2))
  }
  
  implicit class PairOptionWrapper[A,B](val ab: (Option[A],Option[B])) extends AnyVal {
    def both[Z](f: (A,B)=>Z) = if (ab._1.isDefined && ab._2.isDefined) Some(f(ab._1.get,ab._2.get)) else None
  }
  
  implicit class TrioWrapper[A,B,C](val abc: (A,B,C)) extends AnyVal {
    def f1[Z](f: A=>Z) = (f(abc._1), abc._2, abc._3)
    def f2[Z](f: B=>Z) = (abc._1, f(abc._2), abc._3)
    def f3[Z](f: C=>Z) = (abc._1, abc._2, f(abc._3))
    def each[X,Y,Z](fl: A=>X, fc: B=>Y, fr: C=>Z) = (fl(abc._1),fc(abc._2),fr(abc._3))
    def idmap[D, A1 >: A <: D, B1 >: B <: D, C1 >: C <: D, Z](f: D=>Z) = (f(abc._1.asInstanceOf[D]), f(abc._2.asInstanceOf[D]), f(abc._3.asInstanceOf[D]))
    def fold[Z](f: (A,B,C)=>Z) = f(abc._1,abc._2,abc._3)
    def also[Z](f: (A,B,C)=>Z) = (abc._1, abc._2, abc._3, f(abc._1,abc._2,abc._3))
  }
  
  implicit class SameTrioWrapper[A](val aaa: (A,A,A)) extends AnyVal {
    def same[Z](f: A => Z) = (f(aaa._1), f(aaa._2), f(aaa._3))
  }
    
  implicit class QuadWrapper[A,B,C,D](val abcd: (A,B,C,D)) extends AnyVal {
    def f1[Z](f: A=>Z) = (f(abcd._1), abcd._2, abcd._3, abcd._4)
    def f2[Z](f: B=>Z) = (abcd._1, f(abcd._2), abcd._3, abcd._4)
    def f3[Z](f: C=>Z) = (abcd._1, abcd._2, f(abcd._3), abcd._4)
    def f4[Z](f: D=>Z) = (abcd._1, abcd._2, abcd._3, f(abcd._4))
    def each[W,X,Y,Z](fa: A=>W, fb: B=>X, fc: C=>Y, fd: D=>Z) = (fa(abcd._1), fb(abcd._2), fc(abcd._3), fd(abcd._4))
    def idmap[E, A1 >: A <: E, B1 >: B <: E, C1 >: C <: E, D1 >: D <: E, Z](f: E=>Z) = (f(abcd._1.asInstanceOf[E]), f(abcd._2.asInstanceOf[E]), f(abcd._3.asInstanceOf[E]), f(abcd._4.asInstanceOf[E]))
    def fold[Z](f: (A,B,C,D)=>Z) = f(abcd._1, abcd._2, abcd._3, abcd._4)
    def also[Z](f: (A,B,C,D)=>Z) = (abcd._1, abcd._2, abcd._3, abcd._4, f(abcd._1, abcd._2, abcd._3, abcd._4))
  }
  
  implicit class SameQuadWrapper[A](val aaaa: (A,A,A,A)) extends AnyVal {
    def same[Z](f: A => Z) = (f(aaaa._1), f(aaaa._2), f(aaaa._3), f(aaaa._4))
  }
  
  
  // Alternatives to standard tuples
  sealed trait SomeOf2[+A, +B] {
    def hasOne: Boolean
    def hasTwo: Boolean
    def hasBoth = hasOne && hasTwo
    def one: A
    def two: B
    def both: (A,B)
    def oneOption: Option[A] = if (hasOne) Some(one) else None
    def twoOption: Option[B] = if (hasTwo) Some(two) else None
    def bothOption: Option[(A,B)] = if (hasOne && hasTwo) Some((one, two)) else None
    def oneMap[Y](f: A => Y): SomeOf2[Y, B] = this match {
      case Some2One(x) => Some2One(f(x))
      case _: Some2Two[_,_] => this.asInstanceOf[SomeOf2[Y,B]]
      case Some2Both(x,y) => Some2Both(f(x),y)
    }
    def twoMap[Z](f: B => Z): SomeOf2[A, Z] = this match {
      case _: Some2One[_,_] => this.asInstanceOf[SomeOf2[A,Z]]
      case Some2Two(y) => Some2Two(f(y))
      case Some2Both(x,y) => Some2Both(x,f(y))
    }
    def toList[Z](implicit eva: A <:< Z, evb: B <:< Z) = this match {
      case Some2One(x) => List[Z](x)
      case Some2Two(y) => List[Z](y)
      case Some2Both(x,y) => List[Z](x,y)
    }
    def swap = this match {
      case Some2One(x) => Some2Two(x)
      case Some2Two(y) => Some2One(y)
      case Some2Both(x,y) => Some2Both(y,x)
    }
  }
  sealed case class Some2One[+A, +B](one: A) extends SomeOf2[A,B] {
    def hasOne = true
    def hasTwo = false
    def two = throw new NoSuchElementException("Some2One.two")
    def both = throw new NoSuchElementException("Some2One.both")
  }
  sealed case class Some2Two[+A, +B](two: B) extends SomeOf2[A,B] {
    def hasOne = false
    def hasTwo = true
    def one = throw new NoSuchElementException("Some2Two.one")
    def both = throw new NoSuchElementException("Some2Two.both")
  }
  sealed case class Some2Both[+A, +B](one: A, two: B) extends SomeOf2[A,B] {
    def hasOne = true
    def hasTwo = true
    def both = (one, two)
  }
  object SomeOf2 {
    def apply[A,B](one: A, two: B) = Some2Both(one, two)
    def apply[A,B](both: (A,B)) = Some2Both(both._1, both._2)
  }
  
  // Methods for functions
  class DroppedFunction[-A,+B](f: A => Option[B]) extends PartialFunction[A,B] {
    private[this] var tested = false
    private[this] var arg: A = _
    private[this] var ans: Option[B] = None
    private[this] def cache(a: A) {
      if (!tested || a != arg) {
        tested = true
        arg = a
        ans = f(a)
      }
    }        
    def isDefinedAt(a: A) = {
      cache(a)
      ans.isDefined
    }
    def apply(a: A) = {
      cache(a)
      ans.get
    }
    override def lift = f
  }
  implicit class DroppableFunction[A,B](val f: A => Option[B]) extends AnyVal {
    def drop = new DroppedFunction(f)
  }
  
  
  // Type unions using contravariance
  // Usage: def f[T: Union[Int, String]#Check](t: T) = t match { case i: Int => i; case s: String => s.length }
  trait Contra[-A] {}
  type Union[A,B] = { type Check[Z] = Contra[Contra[Z]] <:< Contra[Contra[A] with Contra[B]] }
  type Union3[A,B,C] = { type Check[Z] = Contra[Contra[Z]] <:< Contra[Contra[A] with Contra[B] with Contra[C]] }
  type Union4[A,B,C,D] = { type Check[Z] = Contra[Contra[Z]] <:< Contra[Contra[A] with Contra[B] with Contra[C] with Contra[D]] }
  type Union5[A,B,C,D,E] = { type Check[Z] = Contra[Contra[Z]] <:< Contra[Contra[A] with Contra[B] with Contra[C] with Contra[D] with Contra[E]] }
  
  // Methods for collections
  private[core] final class TakenToIterator[A](ir: Iterator[A], p: A => Boolean) extends Iterator[A] {
    private[this] var cache: A = _
    private[this] var loaded = false
    private[this] var done = false
    final def hasNext = loaded || (!done && {
      if (!ir.hasNext) { done = true; false }
      else {
        cache = ir.next
        loaded = true
        done = p(cache)
        true
      }
    })
    @tco final def next: A = if (loaded) { loaded = false; cache } else if (hasNext) next else throw new NoSuchElementException("No next element in iterator.")
  }
  implicit class ExtendedIterator[A](val ir: Iterator[A]) extends AnyVal {
    def getNext = if (ir.hasNext) Some(ir.next) else None
    def grabNext(implicit oops: Oops) = if (ir.hasNext) ir.next else oops
    def takeTo(p: A => Boolean): Iterator[A] = new TakenToIterator[A](ir,p)
    def findUnique(p: A => Boolean): Option[A] = {
      while (ir.hasNext) {
        val x = ir.next
        if (p(x)) {
          while (ir.hasNext) { if (p(ir.next)) return None }
          return Some(x)
        }
      }
      None
    }
    def pairfold[B,C](zero: B)(f: (A,A) => C)(g: (B,C) => B): B = {
      if (!ir.hasNext) zero
      else {
        var b = zero
        var prev = ir.next
        while (ir.hasNext) {
          val a = ir.next
          b = g(b, f(prev,a))
          prev = a
        }
        b
      }
    }
    def forallpair(f: (A,A) => Boolean) = {
     if (!ir.hasNext) true
      else {
        var prev = ir.next
        var ans = true
        while (ans && ir.hasNext) {
          val x = ir.next
          ans = f(prev,x)
          prev = x
        }
        ans
      }
    }
    def existspair(f: (A,A) => Boolean) = {
      if (!ir.hasNext) false
      else {
        var prev = ir.next
        var ans = false
        while (!ans && ir.hasNext) {
          val x = ir.next
          ans = f(prev,x)
          prev = x
        }
        ans
      }
    }
  }
  class ExtendedIterable[A](it: Iterable[A]) {
    def headOr(a: A) = it.headOption.getOrElse(a)
    def neck2(implicit oops: Oops) = { val ir = it.iterator; oopsless( (ir.grabNext, ir.grabNext) ) }
    def neck3(implicit oops: Oops) = { val ir = it.iterator; oopsless( (ir.grabNext, ir.grabNext, ir.grabNext) ) }
    def neck4(implicit oops: Oops) = { val ir = it.iterator; oopsless( (ir.grabNext, ir.grabNext, ir.grabNext, ir.grabNext) ) }

    def findUnique(p: A => Boolean): Option[A] = it.iterator.findUnique(p)
    def pairfold[B,C](zero: B)(f: (A,A) => C)(g: (B,C) => B): B = it.iterator.pairfold(zero)(f)(g)
    def forallpair(f: (A,A) => Boolean) = it.iterator.forallpair(f)
    def existspair(f: (A,A) => Boolean) = it.iterator.existspair(f)
  }
  implicit def iterable_has_extension[A](it: Iterable[A]) = new ExtendedIterable(it)
  implicit def array_has_extension[A](a: Array[A]) = new ExtendedIterable(a: collection.mutable.WrappedArray[A])
  implicit def string_has_extension(s: String) = new ExtendedIterable(s: Seq[Char])
  
  implicit class RichIndexedSeq[A](val isa: IndexedSeq[A]) extends AnyVal {
    def look(i: Int) = if (i<0) isa(isa.length+i) else isa(i)
    def ring(i: Int) = if (i<0) isa(isa.length-1+((i+1)%isa.length)) else isa(i%isa.length)
    def safe(i: Int) = if (i<0 || i>=isa.length) None else Some(isa(i))
  }
  
  implicit class RichSpecArray[@specialized A](val arr: Array[A]) {
    def look(i: Int) = if (i<0) arr(arr.length+i) else arr(i)
    def ring(i: Int) = if (i<0) arr(arr.length-1+((i+1)%arr.length)) else arr(i%arr.length)
    def safe(i: Int) = if (i<0 || i>=arr.length) None else Some(arr(i))
  }
  
  import collection.generic.CanBuildFrom
  private[this] val vector_string_builder = new CanBuildFrom[String, String, Vector[String]] {
    def apply() = Vector.newBuilder[String]
    def apply(from: String) = this.apply()
  }
  class PimpedCollections[A, C, D[C]](ca: C)(implicit c2i: C => Iterable[A], cbf: CanBuildFrom[C,C,D[C]], cbfi: CanBuildFrom[C,A,C]) {
    def groupedWhile(p: (A,A) => Boolean): D[C] = {
      val it = c2i(ca).iterator
      val cca = cbf()
      if (!it.hasNext) cca.result
      else {
        val as = cbfi()
        var olda = it.next
        as += olda
        while (it.hasNext) {
          val a = it.next
          if (p(olda,a)) as += a
          else { cca += as.result; as.clear; as += a }
          olda = a
        }
        cca += as.result
      }
      cca.result
    }
    def groupedFold[B](b0: => B)(i: (B,A) => B)(p: (B,A) => Boolean): D[C] = {
      val it = c2i(ca).iterator
      val cca = cbf()
      if (!it.hasNext) cca.result
      else {
        val as = cbfi()
        var first = true
        val a0 = it.next
        as += a0
        var b = i(b0,a0)
        while (it.hasNext) {
          val a = it.next
          if (p(b,a)) { as += a; b = i(b,a) }
          else { cca += as.result; as.clear; as += a; b = i(b0,a) }
        }
        cca += as.result
      }
      cca.result
    }
    def tokenize(tok: A): D[C] = {
      val it = c2i(ca).iterator
      val cca = cbf()
      val as = cbfi()
      while (it.hasNext) {
        as ++= it.takeWhile(_ != tok)
        cca += as.result
        as.clear
      }
      cca.result
    }
    def tokenizeBy(p: A => Boolean, where: Int = 0) = {
      val it = c2i(ca).iterator
      val cca = cbf()
      val as = cbfi()
      var empty = true
      while (it.hasNext) {
        var x = it.next
        var found = false
        while (!{ found = p(x); found } && it.hasNext) {
          as += x
          empty = false
          x = it.next
        }
        if (!found || where < 0) as += x
        cca += as.result
        as.clear
        if (found && where > 0) as += x
        else empty = true
      }
      if (!empty) cca += as.result
      cca.result
    }
    def tokensByContext(separate: (A,A,A) => Boolean): D[C] = {
      val it = c2i(ca).iterator
      val cca = cbf()
      if (!it.hasNext) cca.result
      else {
        val as = cbfi()
        var left = it.next
        as += left
        var na = 1
        if (!it.hasNext) cca += as.result
        else {
          var center = it.next
          if (!it.hasNext) { as += center; na += 1; cca += as.result }
          else {
            while (it.hasNext) {
              val right = it.next
              if (separate(left,center,right)) {
                if (na > 0) {
                  na = 0
                  cca += as.result
                  as.clear
                }
                left = center
                center = right
              }
              else {
                as += center
                na += 1
                left = center
                center = right
              }
            }
            as += center
            cca += as.result
          }
        }
      }
      cca.result
    }
  }
  implicit def collections_have_pimps[A, C[A]](ca: C[A])(implicit c2i: C[A] => Iterable[A], cbf: CanBuildFrom[C[A],C[A],C[C[A]]], cbfi: CanBuildFrom[C[A],A,C[A]]) = 
    new PimpedCollections[A,C[A],C](ca)(c2i,cbf,cbfi)
  implicit def strings_have_pimps(s: String)(implicit c2i: String => Iterable[Char], cbfi: CanBuildFrom[String,Char,String]) =
    new PimpedCollections[Char,String,Vector](s)(c2i, vector_string_builder, cbfi)
  
  class IndexAwareCollections[A,C](ca: C)(implicit c2s: C => IndexedSeq[A], cbf: CanBuildFrom[C,A,C]) {
    def compacted(f: (A,A) => Option[A]): C = {
      val sa = c2s(ca)
      if (sa.length>1) {
        val compact = cbf()
        var j = 0
        var last = sa(0)
        while (j < sa.length-1) {
          f(last,sa(j+1)).fold{ compact += last; last = sa(j+1) }{x => last = x}
          j += 1
        }
        compact += last
        compact.result
      }
      else ca
    }
    def takeNear(i: Int)(p: A => Boolean): C = {
      val taken = cbf()
      val sa = c2s(ca)
      if (p(sa(i))) {
        var j = i-1
        while (j >= 0 && p(sa(j))) j -= 1
        while (j < i) {
          j += 1
          taken += sa(j)
        }
        j += 1
        while (j < sa.length && p(sa(j))) {
          taken += sa(j)
          j += 1
        }
      }
      taken.result
    }
    def forpieces[Z](p: A => Boolean)(f: (IndexedSeq[A], Boolean, Int, Int) => Z) {
      val sa = c2s(ca)
      var i = 0
      while (i < sa.length) {
        val pi = p(sa(i))
        var j = i+1
        while (j < sa.length && p(sa(j))==pi) j += 1
        f(sa, pi, i, j)
        i = j
      }
    }
  }
  implicit def collections_are_index_aware[A, C[A]](ca: C[A])(implicit c2s: C[A] => IndexedSeq[A], cbf: CanBuildFrom[C[A],A,C[A]]) = new IndexAwareCollections[A,C[A]](ca)(c2s,cbf)
  implicit def strings_are_index_aware(s: String)(implicit c2s: String => IndexedSeq[Char], cbf: CanBuildFrom[String,Char,String]) = new IndexAwareCollections[Char,String](s)(c2s,cbf)
  
  class IndexRemappingCollections[A,C](ca: C)(implicit c2s: C => IndexedSeq[A]) {
    def imap[B,D](f: (A,Int) => B)(implicit cba2b: CanBuildFrom[C,B,D]): D = {
      val sa = c2s(ca)
      val N = sa.length
      val mapped = cba2b()
      var j = 0
      while (j < N) { mapped += f(sa(j),j); j += 1 }
      mapped.result
    }
  }
  implicit def collections_can_index_map[A, C[A]](ca: C[A])(implicit c2s: C[A] => IndexedSeq[A]) = new IndexRemappingCollections[A,C[A]](ca)(c2s)
  implicit def strings_can_index_map(s: String)(implicit c2s: String => IndexedSeq[Char]) = new IndexRemappingCollections[Char,String](s)(c2s)
  
   
  class SmartlyRebuiltCollections[A,C](ca: C)(implicit c2s: C => Iterable[A], cbf: CanBuildFrom[C,A,C]) {
    def collectBoth[B,D](pf: PartialFunction[A,B])(implicit cba2b: CanBuildFrom[C,B,D]): (D,C) = {
      val skipped = cbf()
      val mapped = cba2b()
      val i = c2s(ca).iterator
      while (i.hasNext) {
        val j = i.next
        if (pf.isDefinedAt(j)) mapped += pf(j)
        else skipped += j
      }
      (mapped.result, skipped.result)
    }
  }
  implicit def collections_can_rebuild_smartly[A, C[A]](ca: C[A])(implicit c2s: C[A] => Iterable[A], cbf: CanBuildFrom[C[A],A,C[A]]) =
    new SmartlyRebuiltCollections[A,C[A]](ca)(c2s,cbf)
  implicit def strings_can_rebuild_smartly(s: String)(implicit c2s: String => Iterable[Char], cbf: CanBuildFrom[String,Char,String]) =
    new SmartlyRebuiltCollections[Char,String](s)(c2s,cbf)

  class Bisectables[A,C](ca: C)(implicit c2s: C => IndexedSeq[A]) {
    def bisect(a: A, left: Boolean = false)(implicit ord: Ordering[A]) = bisectBy(a)(identity)(ord)
    def bisectR(a: A)(implicit ord: Ordering[A]) = bisect(a, false)(ord)
    def bisectL(a: A)(implicit ord: Ordering[A]) = bisect(a, true)(ord)
    def bisectBy[B](b: B, left: Boolean = false)(f: A => B)(implicit ord: Ordering[B]): Int = {
      import ord._
      val is = c2s(ca)
      var i = 0
      var j = is.length-1
      if (j < 0) return (if (left) -1 else 0)
      while (i+1 < j) {
        val k = (i+j)/2
        if (f(is(k)) < b) i = k else j = k
      }
      val bi = f(is(i))
      if (b < bi && left) i-1
      else if (b <= bi) i
      else {
        val bj = f(is(j))
        if (b > bj && !left) j+1
        else if (b >= bj) j
        else if (left) i
        else j
      }
    }
    def bisectByR[B](b: B)(f: A => B)(implicit ord: Ordering[B]) = bisectBy(b, false)(f)(ord)
    def bisectByL[B](b: B)(f: A => B)(implicit ord: Ordering[B]) = bisectBy(b, true)(f)(ord)
  }
  implicit def collections_can_bisect[A, C[A]](ca: C[A])(implicit c2s: C[A] => IndexedSeq[A]) = new Bisectables[A,C[A]](ca)(c2s)
  implicit def strings_can_bisect(s: String)(implicit c2s: String => IndexedSeq[Char]) = new Bisectables[Char,String](s)(c2s)
  
  
  // Novel collections under development--will probably move to their own file or library at some point
    
  trait Cursor[A] {
    def *(): A
    def current: Option[A]
    def index: Int
    def index_=(i: Int): Unit
    def rightLimit: Int
    def replace(a: A): this.type
    def ins(a: A): this.type
    def del(): this.type
    final def Index(i: Int) = { index = i; this }
    final def bksp() = { if (index > 0) bkw.del else this }
    final def toFirst = { index = 0; this }
    final def toLast = { index = rightLimit-1; this }
    final def fwd() = { if (index+1 < rightLimit) Index(index+1) else this }
    final def bkw() = { if (index >= 0) Index(index-1) else this }
    final def insL(a: A) = bkw.ins(a)
    final def hasL = index>0 && index<rightLimit
    final def hasR = index>=0 && index+1<rightLimit
    final def cursorOk = index>=0 && index<rightLimit
  }
  class Cursory[A] 
    extends collection.mutable.ArrayBuffer[A]
    with collection.generic.GenericTraversableTemplate[A,Cursory]
    with collection.mutable.IndexedSeqOptimized[A,Cursory[A]]
    with collection.mutable.Builder[A, Cursory[A]]
    with Cursor[A] 
  { self =>
    // Cursor
    def * = if (cursorOk) this(index) else null.asInstanceOf[A]
    def current = if (cursorOk) Some(this(index)) else None
    var index = -1
    final def rightLimit = length
    def replace(a: A) = { update(index,a); this }
    def ins(a: A) = { insert(index+1,a); index += 1; this }
    def del() = { if (index>=0) { remove(index) }; this }
    
    // Builder
    override def result() = {
      val ans = new Cursory[A]
      ans ++= this
      ans.index = index
      ans
    }
    // IndexedSeqOptimized
    // GenericTraversableTemplate
    override def companion = new collection.generic.GenericCompanion[Cursory] {
      def newBuilder[B] = new collection.mutable.Builder[B,Cursory[B]] {
        private[this] val fractionalPosition = index.toDouble/rightLimit
        private[this] val targetItem = self.*
        private[this] var partialBuild = new Cursory[B]
        def +=(b: B) = { partialBuild += b; this }
        def clear() { partialBuild = new Cursory[B] }
        def result = {
          val i0 = math.max(0, math.floor(partialBuild.length*fractionalPosition).toInt)
          var i = 0
          if (!referentiallyEquals(partialBuild(i0),targetItem)) {
            i += 1
            var found = 0
            while (found==0 && i0-i >= 0 && i0+i < partialBuild.length) {
              if (referentiallyEquals(partialBuild(i0-i), targetItem)) found = -1
              else if (referentiallyEquals(partialBuild(i0+i), targetItem)) found = 1
              else i += 1
            }
            if (found != 0) partialBuild.index = i0+found*i
          }
          partialBuild
        }
      }
    }
    
    // ResizableArray (via ArrayBuffer)
    override protected def swap(i: Int, j: Int) {
      super.swap(i,j)
      if (i==index) index = j
      if (j==index) index = i
    }
    override protected def copy(m: Int, n: Int, len: Int) {
      super.copy(m,n,len)
      if (index >= m && index < m+len) index += (n-m)
      else if (index >= n && index < n+len) index = math.min(length-1, n+len)
    }
    
    // ArrayBuffer
    override def +=:(a: A) = {
      if (index >= 0) index += 1
      super.+=:(a)
    }
    override def insertAll(n: Int, xs: Traversable[A]) {
      if (index < n) super.insertAll(n,xs)
      else {
        val l0 = length
        super.insertAll(n,xs)
        index += (length - l0)
      }
    }
    override def remove(n: Int, count: Int) {
      super.remove(n,count)
      if (index >= n+count) index -= count
      else if (index >= n) index = math.min(length-1,n)
    }
    override def clone: Cursory[A] = result()
    override def stringPrefix = "Cursory[%d]".format(index)
  }
    
    
  //@deprecated("RingBuffer works, but only partially, and will be replaced as soon as possible.  Use at your own risk.","0.0")
  final class RingBuffer[A: Tag](val maxsize: Int, initial: Option[Array[A]] = None)
//   extends collection.mutable.Buffer[A]
//    with collection.mutable.BufferLike[A, RingBuffer[A]]
//    with collection.mutable.IndexedSeqOptimized[A, RingBuffer[A]]
  { self =>
    private[this] val buffer = new Array[A](maxsize+1)
    private[this] var i0,i1 = 0
    private[this] def i0up = { i0 += 1; if (i0>=buffer.length) i0 -= buffer.length }
    private[this] def i0dn = { i0 -= 1; if (i0<0) i0 += buffer.length }
    private[this] def i1up = { i1 += 1; if (i1>=buffer.length) i1 -= buffer.length }
    private[this] def i1dn = { i1 -= 1; if (i1<0) i1 += buffer.length }   
    private[this] def me = this
    if (initial.isDefined) {
      val aa = initial.get
      if (aa.length <= maxsize) { System.arraycopy(aa,0,buffer,0,aa.length); i1 = aa.length }
      else { System.arraycopy(aa,aa.length-maxsize,buffer,0,maxsize); i1 = maxsize }
    }

    def this(a: Array[A]) = this(a.length, Some(a))
    def newBuilder = RingBuffer.newBuilder(maxsize)
    def length = if (i1<i0) buffer.length+i1-i0 else i1-i0
    def apply(i: Int) = {
      val j = i+i0
      if (j >= buffer.length) buffer(j-buffer.length) else buffer(j)
    }
    def update(i: Int, elem: A) {
      val j = i+i0
      if (j >= buffer.length) buffer(j-buffer.length) = elem else buffer(j) = elem
    }
    def clear { i0 = 0; i1= 0 }
    def fresh = { clear; this }
    def head = buffer(i0)
    def last = if (i1==0) buffer(buffer.length-1) else buffer(i1-1)
    def asIndexedSeq = new collection.IndexedSeq[A] {
      def apply(i: Int) = self(i)
      def length: Int = self.length
    }
    def +=(a: A) = {
      buffer(i1) = a
      i1up; if (i1==i0) i0up
      this
    }
    def remove(n: Int, count: Int) {
      if (n<length) {
        if (n+count >= length) i1 = (i0+n)%buffer.length
        else if (n<=0) i0 = (i0+count)%buffer.length
        else {
          val after = length - (n+count)
          if (after < n) {
            var i = (i0+n)%buffer.length
            var j = (i0+n+count)%buffer.length
            while (j != i1) {
              buffer(i) = buffer(j)
              i += 1
              if (i>=buffer.length) i = 0
              j += 1
              if (j>=buffer.length) j = 0
            }
            i1 = i
          }
          else {
            var i = (i0+n-1)%buffer.length
            var j = (i0+n+count-1)%buffer.length
            buffer(j) = buffer(i)
            while (i != i0) {
              i -= 1
              if (i<0) i = buffer.length - 1
              j -= 1
              if (j<0) j = buffer.length - 1
              buffer(j) = buffer(i)
            }
            i0 = j
          }
        }
      }
    }
    def remove(n: Int): A = { if (n==0) poph else if (n+1==length) popt else { val ans = apply(n); remove(n,1); ans } }
    def insertAll(n: Int, elems: Traversable[A]) {
      if (n >= length) elems.foreach(this += _)
      else {
        val extra = new Array[A](length-n)
        i1 = (i0+n)%buffer.length
        var i = i1
        var j = 0
        while (j < extra.length) {
          extra(j) = buffer(i)
          j += 1
          i += 1
          if (i>=buffer.length) i = 0
        }
        elems.foreach(this += _)
        j = 0
        while (length < maxsize && j < extra.length) {
          this += extra(j)
          j += 1
        }
      }
    }
    def +=:(a: A) = {
      i0dn; if (i0==i1) i1dn
      buffer(i0) = a
      this
    }
    def :=+(a: A) = (this += a)
    def popt = {
      if (i1==i0) throw new java.util.NoSuchElementException
      i1dn; buffer(i1)
    }
    def poph = {
      if (i1==i0) throw new java.util.NoSuchElementException
      val ans = buffer(i0); i0up; ans
    }
  }
  object RingBuffer {
    def apply[A: Tag](aa: A*) = { val rb = new RingBuffer[A](aa.length); aa.foreach(rb += _); rb }
    def empty[A: Tag] = new RingBuffer[A](0)
    def newBuilder[A: Tag] = (collection.mutable.ArrayBuilder.make[A]).mapResult(aa => new RingBuffer(aa))
    def newBuilder[A: Tag](n: Int) = (collection.mutable.ArrayBuilder.make[A]).mapResult(aa => new RingBuffer(n,Some(aa)))
    implicit def canBuildFrom[A: Tag] = new CanBuildFrom[RingBuffer[_],A,RingBuffer[A]] {
      def apply() = newBuilder
      def apply(from: RingBuffer[_]) = newBuilder(from.maxsize)
    }
  }
  
  // Parsing
  private[core] def nfe[T](t: => T) = try {Some(t)} catch {case nfe: NumberFormatException => None}
  implicit class SafeParsePrimitive(val s: String) extends AnyVal {
    import SafeParsePrimitive._
    def booleanOption = s.toLowerCase match {
      case "yes" | "true" => Some(true)
      case "no" | "false" => Some(false)
      case _ => None
    }
    def byteOption = nfe(s.toByte)
    def doubleOption = nfe(s.toDouble)
    def floatOption = nfe(s.toFloat)
    def hexOption = nfe(java.lang.Integer.valueOf(s,16))
    def hexLongOption = nfe(java.lang.Long.valueOf(s,16))
    def intOption = nfe(s.toInt)
    def longOption = nfe(s.toLong)
    def shortOption = nfe(s.toShort)
    def siOption = if (s.length==0) None else siMap.get(s.charAt(s.length-1)).map(s.substring(0,s.length-1)+_).orElse(Some(s)).flatMap(x => nfe(x.toDouble))
  }
  object SafeParsePrimitive {
    val siMap = Map('a'->"e-18", 'f'->"e-15", 'p'->"e-12", 'n'->"e-9", 'u'->"e-6", 'm'->"e-3", 'k'->"e3", 'M'->"e6", 'G'->"e9", 'T'->"e12", 'P'->"e15", 'E'->"e18")
  }
  object ParseBoolean { def unapply(s: String) = s.booleanOption }
  object ParseByte { def unapply(s: String) = s.byteOption }
  object ParseDouble { def unapply(s: String) = s.doubleOption }
  object ParseFloat { def unapply(s: String) = s.floatOption }
  object ParseHex { def unapply(s: String) = s.hexOption }
  object ParseHexLong { def unapply(s: String) = s.hexLongOption }
  object ParseInt { def unapply(s: String) = s.intOption }
  object ParseLong { def unapply(s: String) = s.longOption }
  object ParseShort { def unapply(s: String) = s.shortOption }
  object ParseTokens { def unapplySeq(s: String) = s.split("""\W""").filter(_.length>0).toVector.when(_.length>0) }

  // XML handling with more data-type awareness
  class SmartXMLTag(n: scala.xml.Node) {
    def #@(s: String) = (n \ ("@"+s)).flatMap(_.text.intOption)
    def ##@(s: String) = (n \ ("@"+s)).flatMap(_.text.longOption)
    def ^@(s: String) = (n \ ("@"+s)).flatMap(_.text.doubleOption)
    def ~@(s: String) = (n \ ("@"+s)).map(_.text)
  }
  implicit def xmlnode_is_smarter(n: scala.xml.Node) = new SmartXMLTag(n)  // Compiler gets confused if you switch to implicit class
  class SmartXMLTags(ns: scala.xml.NodeSeq) {
    def #@(s: String) = ns.flatMap(_ #@ s)
    def ##@(s: String) = ns.flatMap(_ ##@ s)
    def ^@(s: String) = ns.flatMap(_ ^@ s)
    def ~@(s: String) = ns.map(_ ~@ s)
  }
  implicit def xmlnodeseq_is_smarter(n: scala.xml.NodeSeq) = new SmartXMLTags(n)  // Compiler gets confused if you switch to implicit class
  
  
  // Directory tools
  val thePwd = new java.io.File(".")
  def getPwd() = thePwd.getCanonicalFile
  trait FileBackedStream extends java.io.InputStream {
    def size: Long
    def path: String
    def length = size
    def name = path.split(java.io.File.separatorChar).last
    def underlying: java.io.InputStream = this
  }
  def numberedFilePattern(s: String) = {
    """(.*?)(\d+)(\D*)""".r.unapplySeq(s).flatMap(l => {
      if (l.length==3) Some((l.head,l.tail.head,l.tail.tail.head)) else None
    }).flatMap(pnp => pnp._2.intOption.map(n => (pnp._1,n,pnp._3)))
  }
  class DirectoryIterator(f: java.io.File, callback: Option[(java.io.File, Boolean) => Boolean] = None) extends Iterator[java.io.File] {
    private[this] val fs = Option(f.listFiles).getOrElse(Array[java.io.File]()).map(f => if (f.isDirectory) (f, Some(f.getCanonicalFile)) else (f,None))
    private[this] var i = -1
    private[this] var recurse: DirectoryIterator = null
    private[this] val check = callback.getOrElse{
      val hs = collection.mutable.HashSet[java.io.File](f.getCanonicalFile)
      (f: java.io.File, b: Boolean) => {
        if (hs contains f) false
        else { if (b) hs += f; true }
      }
    }
    def hasNext = {
      if (recurse != null && recurse.hasNext) true
      else {
        while (i+1 < fs.length && fs(i+1)._2.isDefined && !check(fs(i+1)._2.get, false)) i += 1
        (i+1 < fs.length)
      }
    }
    def next = {
      if (recurse != null && recurse.hasNext) recurse.next 
      else if (i+1 >= fs.length) {
        throw new java.util.NoSuchElementException("next on empty file iterator")
      }
      else {
        i += 1;
        if (fs(i)._2.isDefined) {
          if (check(fs(i)._2.get, true)) {
            recurse = new DirectoryIterator(fs(i)._1, Some(check))
            fs(i)._1
          }
          else next
        }
        else fs(i)._1
      }
    }
  }
  implicit class UtilitizedFile(val file: java.io.File) {
    def canon = file.getCanonicalFile
    def namely(op: String => String) = new java.io.File(file.getParentFile, op(file.getName))
    def pathly(op: String => String) = new java.io.File(op(file.getParent), file.getName)
    def extly(op: String => String) = new java.io.File(file.getParentFile, file.getName.split('.').zap{ ss =>
      if (ss.length > 1) op(ss(ss.length-1)).zap(ext => if (ext.length==0) ss.dropRight(1) else ss.dropRight(1) :+ ext)
      else op("").zap(ext => if (ext.length==0) ss else ss :+ ext)
    }.mkString("."))  
    def getExtension = file.getName.split('.').zap(ss => if (ss.length>1) ss(ss.length-1) else "")
    def hasExtension(s: String) = getExtension == s
    def copyTo(targ: java.io.File) {
      val fis = new java.io.FileInputStream(file)
      try {
        val fos = new java.io.FileOutputStream(targ)
        try { fos.getChannel.transferFrom(fis.getChannel, 0, Long.MaxValue ) }
        finally { fos.close }
      }
      finally { fis.close }
    }
    def zip(compressionLevel: Int, selector: java.io.File => Option[String] = (f) => Some(f.getPath), overwrite: Boolean = false, extra: => Seq[(String, Either[String, Array[Byte]])] = Nil) {
      val bits = explode
      def strip(f: java.io.File) = {
        val e = (new UtilitizedFile(f)).explode
        var i = 0
        while (i+1 < e.length && e(i) == bits(i)) i += 1
        new java.io.File(e.drop(i).mkString(java.io.File.separator))
      }
      val zipfile = new java.io.File(file.getParentFile, file.getName + ".zip")
      if (!overwrite && zipfile.exists) throw new java.io.IOException(zipfile.getPath + " exists.")
      val longtargets = if (file.isDirectory) listRecursive() else Array(file)
      val targets = longtargets.flatMap{ lt => val st = strip(lt); selector(lt).map(x => (lt, st, x)) }
      val fos = new java.io.FileOutputStream(zipfile)
      try {
        val buffer = new Array[Byte](1024*1024)
        val zipper = new java.util.zip.ZipOutputStream(fos)
        zipper.setLevel((0 max (9 min compressionLevel)))
        for ((tf,_,nm) <- targets) {
          val ze = new java.util.zip.ZipEntry(nm)
          ze.setTime(tf.lastModified)
          val fis = new java.io.FileInputStream(tf)
          try {
            zipper.putNextEntry(ze)
            try {
              var n = 0
              while (n != -1) {
                n = fis.read(buffer)
                if (n > 0) zipper.write(buffer, 0, n)
              }
            }
            finally zipper.closeEntry  
          }
          finally fis.close
        }
        for ((nm, sab) <- extra) {
          val ze = new java.util.zip.ZipEntry(file.getName + java.io.File.separator + nm)
          try {
            zipper.putNextEntry(ze)
            val contents = sab match {
              case Left(s) => s.getBytes
              case Right(a) => a
            }
            zipper.write(contents, 0, contents.length)
          }
          finally zipper.closeEntry
        }
        zipper.close
      }
      finally { fos.close }
    }
    def explode = Iterator.iterate(canon)(_.getParentFile).takeWhile(_ ne null).toArray.reverse
    def explodeName = explode.map(_.getName)
    def listSequence(prepost: Option[Vx[(String,String)]] = None) = {
      val absone = file.getAbsoluteFile
      val onegroup = numberedFilePattern(absone.getName)
      onegroup.map(o => {
        prepost.foreach(_ := ((o._1,o._3)))
        val files = absone.getParentFile.listFiles      
        files.flatMap(
          f => numberedFilePattern(f.getName).filter(x => x._1==o._1 && x._3==o._3).map(x=>(x,f))
        ).sortBy(_._1._2).map(_._2)
      }).getOrElse(Array[java.io.File]())
    }
    def listRecursive(p: java.io.File => Boolean = (pf) => true): Array[java.io.File] = {
      if (!file.isDirectory) Array[java.io.File]()
      else {
        val files = file.listFiles
        val dirs = files.filter(_.isDirectory)
        files.filter(p) ++ dirs.flatMap(f => (new UtilitizedFile(f)).listRecursive(p))
      }
    }
    def listZip: Option[Array[String]] = {
      import scala.collection.JavaConverters._
      if (file.isDirectory) None
      safe{ new java.util.zip.ZipFile(file).tidily(_.close){ zf =>
        val e = zf.entries
        val sb = Array.newBuilder[String]
        while (e.hasMoreElements) sb += e.nextElement.getName
        sb.result
      }}.toOption
    }
    def recursiveStreams(p: String => Boolean = (pf) => true): Iterator[FileBackedStream] = {
      import scala.collection.JavaConversions._
      if (file.isDirectory) new Iterator[FileBackedStream] {
        val files = file.listFiles
        val selected = files.filter(f => !f.isDirectory && !f.getName.endsWith(".zip") && p(f.getAbsolutePath))
        val dirzipit = files.filter(f => f.isDirectory || f.getName.endsWith(".zip")).iterator.map(x => (new UtilitizedFile(x)).recursiveStreams(p)).flatten
        var idx = -1
        def hasNext = (idx+1 < selected.length) || dirzipit.hasNext
        def next = {
          if (idx+1 >= selected.length) dirzipit.next
          else {
            idx += 1;
            new java.io.FileInputStream(selected(idx)) with FileBackedStream {
              val path = selected(idx).getAbsolutePath
              val size = selected(idx).length
            }
          }
        }
      }
      else if (file.getName.endsWith(".zip")) {
        try {
          var opencount = 0
          var alldone = false
          val zf = new java.util.zip.ZipFile(file)
          val ent = zf.entries.filter(ze => !ze.isDirectory && p(file.getAbsolutePath + java.io.File.separator + ze.getName))
          new Iterator[FileBackedStream] {
            def hasNext = {
              val ans = ent.hasNext
              if (!ans && opencount==0) zf.close
              else alldone = !ans
              ans
            }
            def next = {
              val e = ent.next
              opencount += 1
              new java.io.FilterInputStream(zf.getInputStream(e)) with FileBackedStream {
                val size = e.getSize
                val path = file.getAbsolutePath + java.io.File.separator + e.getName
                override def underlying = in
                override def close() = {
                  super.close()
                  opencount -= 1
                  if (opencount==0 && alldone) zf.close
                }
              }
            }
          }
        }
        catch {
          case ze: java.util.zip.ZipException => List[FileBackedStream]().iterator
        }
      }
      else List[FileBackedStream]().iterator
    }
    def repath(prefixes: Array[String], newpre: String): Option[java.io.File] = {
      val g = file.getAbsolutePath
      if (g.length<2) None
      else if (prefixes.exists(_ == g)) Some(new java.io.File(newpre))
      else {
        val p = file.getAbsoluteFile.getParentFile
        val n = file.getName
        (new UtilitizedFile(p)).repath(prefixes,newpre).map(s => (new java.io.File(s,n)))
      }
    }
    def ensurePath() {
      val g = file.getAbsoluteFile.getParentFile
      if (!g.exists) {
        if (g.getPath.length > 1) (new UtilitizedFile(g)).ensurePath()
        g.mkdir()
      }
    }
    def retirePath() {
      val g = file.getAbsoluteFile.getParentFile
      if (!g.exists) (new UtilitizedFile(g)).retirePath()
      else if (g.listFiles.length == 0) {
        g.delete()
        (new UtilitizedFile(g)).retirePath()
      }
    }
    def hasDirectory(s: String): Boolean = {
      val g = file.getAbsoluteFile.getParentFile
      if (!g.getPath.contains(s)) false
      else if (g.getPath.length <= 1) false
      else if (g.getName == s) true
      else (new UtilitizedFile(g)).hasDirectory(s)
    }
    def readText = safe{ scala.io.Source.fromFile(file).tidily(_.close){ _.getLines.toVector } }
    def readTextWith[A](f: String => Option[A]) = safe{ scala.io.Source.fromFile(file).tidily(_.close){
      src => val vb = Vector.newBuilder[A];  src.getLines().foreach{ l => f(l) match { case Some(a) => vb += a; case _ => } }; vb.result
    }}
  }
  //implicit class CollapseTextToFile(val texts: Array[String]) {
  //  def file = new java.io.File(texts.mkString(java.io.File.pathSeparator, java.io.File.pathSeparator, ""))
  //}
  

  // Methods that should be in nio.ByteBuffer but mysteriously aren't
  implicit class RichByteBuffer(val b: java.nio.ByteBuffer) extends AnyVal {
    def getBytes(n: Int) = { val a = new Array[Byte](n); b.get(a); a }
    def getShorts(n: Int) = { val a = new Array[Short](n); var i=0; while (i<n) { a(i)=b.getShort(); i+=1 } ; a }
    def getInts(n: Int) = { val a = new Array[Int](n); var i=0; while (i<n) { a(i)=b.getInt(); i+=1 } ; a }
    def getLongs(n: Int) = { val a = new Array[Long](n); var i=0; while (i<n) { a(i)=b.getLong(); i+=1 } ; a }
    def getFloats(n: Int) = { val a = new Array[Float](n); var i=0; while (i<n) { a(i)=b.getFloat(); i+=1 } ; a }
    def getDoubles(n: Int) = { val a = new Array[Double](n); var i=0; while (i<n) { a(i)=b.getDouble(); i+=1 } ; a }
  }
  
  // Implicit classes that have to be here due to compiler limitations
  implicit class OkExactType[N,Y](val ok: Ok[N,Y]) extends AnyVal {
    def yesOr(f: N => Y): Y = ok match {
      case Yes(y) => y
      case No(n) => f(n)
    }
    def noOr(f: Y => N): N = ok match {
      case Yes(y) => f(y)
      case No(n) => n
    }
  }

  import scala.util.{Try, Success, Failure}
  implicit class TryCanBeOk[Y](val t: Try[Y]) extends AnyVal {
    def toOk: Ok[Throwable, Y] = t match {
      case Success(y) => Yes(y)
      case Failure(n) => No(n)
    }
    def toOk[N](f: Throwable => N) = t match {
      case Success(y) => Yes(y)
      case Failure(x) => No(f(x))
    }
  }

  implicit class OptionCanBeOk[Y](val o: Option[Y]) extends AnyVal {
    def toOk: Ok[Unit, Y] = o match {
      case Some(y) => Yes(y)
      case _ => Ok.UnitNo
    }
    def toNotOk: Ok[Y, Unit] = o match {
      case Some(y) => No(y)
      case _ => Ok.UnitYes
    }
    def toOk[N](default: => N) = o match {
      case Some(y) => Yes(y)
      case _ => No(default)
    }
  }

  implicit class EitherCanBeOk[N, Y](val e: Either[N, Y]) extends AnyVal {
    def toOk: Ok[N,Y] = e match {
      case Right(y) => Yes(y)
      case Left(n) => No(n)
    }
  }

  
  // Experimental stuff.
  class HashFlat[T](initialBufferBits: Int = 4) {
    private[this] final var myBits = (1 max initialBufferBits) min 30
    private[this] final var mySize = 1<<myBits
    private[this] final var myMask = (mySize-1)
    private[this] final var myBuffer = new Array[AnyRef](mySize)
    private[this] final var myCount = 0
    private[this] final var myCollide = 0
    private[this] final def index(i: Int) = scala.util.hashing.byteswap32(i)&myMask
    @annotation.tailrec private[this] final def addAt(i: Int, x: AnyRef) {
      val e = myBuffer(i)
      if (e==null) { myCount += 1; myBuffer(i) = x }
      else if (e==x) { myBuffer(i) = x }
      else { myCollide += 1; addAt((i+1)&myMask, x) }
    }
    def add(t: T): this.type = {
      if ((myCount*8L > mySize*3L || (myCollide - myCount)*8L > mySize) && myBits < 30) {
        myBits += 1
        mySize <<= 1
        myMask = mySize-1
        val old = myBuffer
        myBuffer = new Array[AnyRef](mySize)
        myCount = 0
        myCollide = 0
        var i = 0
        while (i < old.length) {
          val e = old(i)
          if (e != null) addAt(index(e.hashCode),e)
          i += 1
        }
      }
      addAt(index(t.hashCode), t.asInstanceOf[AnyRef])
      this
    }
    @annotation.tailrec private[this] final def isAt(i: Int, x: AnyRef): Boolean = {
      val e = myBuffer(i)
      if (e==null) false
      else if (e==x) true
      else isAt((i+1)&myMask, x)
    }
    def contains(t: T): Boolean = isAt(index(t.hashCode), t.asInstanceOf[AnyRef])
    def size = myCount
  }
}
