package mwt.converter

import org.openworm.trackercommons._

import java.nio.file.{ FileVisitResult => Fvr,  StandardOpenOption => Soo, StandardCopyOption => Sco, _ }
import java.nio.file.attribute.{ BasicFileAttributes => Bfa }
import java.io._

import scala.collection.JavaConverters._

import kse.flow._
import kse.eio._
import kse.jsonal._
import kse.jsonal.JsonConverters._

object WconToOld {
  val Name = """(\d\d\d\d\d\d\d\d_\d\d\d\d\d\d)_(.+)""".r

  def sf3(d: Double): String = if (d.toLong == d) d.toLong.toString else "%.3f".format(d)

  private def bisect[A](a: Array[A])(f: A => Double, target: Double): A = {
    var i = 0
    var j = a.length -1
    while (i + 1 < j) {
      val c = (i+j)/2
      val v = f(a(c))
      if (v < target) i = c else j = c
    }
    if (i == j) a(i)
    else { if (math.abs(f(a(i)) - target) > math.abs(f(a(j)) - target)) a(j) else a(i) }
  }

  case class PerTime(var hello: List[String], var goodbye: List[String], var count: Int, var evt: Long = 0L, var frame: Int = -1) {
    def ++(): this.type = { count += 1; this }
    def hi(s: String): this.type = { hello = s :: hello; this }
    def bye(s: String): this.type = { goodbye = s :: goodbye; this }
    def evtstr =
      if (evt == 0) ""
      else {
        val sb = new StringBuilder
        sb ++= " %"
        for (i <- 0 until 64) {
          val bit = (1L << i.toLong)
          if ((evt & bit) != 0) { sb ++= " 0x"; sb ++= bit.toHexString }
        }
        sb.toString
      }
    def hibyestr =
      if (hello.isEmpty && goodbye.isEmpty) ""
      else {
        val sb = new StringBuilder
        sb ++= " %%"
        hello.reverse.foreach{ h => sb ++= " 0 "; sb ++= h }
        goodbye.reverse.foreach{ g => sb += ' '; sb ++= g; sb ++= " 0" }
        sb.toString
      }
  }
  object PerTime {
    def empty = new PerTime(Nil, Nil, 0)
  }

  def convert(f: java.io.File) {
    if (!f.getName.endsWith(".wcon.zip")) throw new Exception("Can only convert .wcon.zip files (sorry!), NOT "+f.getName)
    val (dir, inner) = f.getName.dropRight(9) match {
      case Name(d, i) => (d, i)
      case _          => throw new Exception("Can only convert files of style YYYYMMdd_hhmmss_MetaData, NOT "+f.getName)
    }
    val wcon = ReadWrite.readChunkedZip(f) match {
      case Left(e) => throw new Exception(f"Couldn't read ${f.getPath}" + "\n" + e)
      case Right(w) => w.groupByIDs()
    }

    val mm_per_pixel = wcon.custom("@XJ")("mm_per_pixel").double match {
      case x if java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x) => 0.026
      case x                                            => x
    }
    val set = wcon.meta.software.headOption.flatMap(sw => sw.settings).flatMap(_.string)

    val datas = wcon.data //.zipWithIndex.map{ case (d,i) => d.copy(id = (i+1).toString)(d.rxs, d.rys) }
    val times = new collection.mutable.HashMap[Double, PerTime]
    datas.map{ data =>
      if (data.ts.length > 0) {
        times.getOrElseUpdate(data.ts.head, PerTime.empty).hi(data.id)
        times.getOrElseUpdate(data.ts.last, PerTime.empty).bye(data.id)
        data.ts.foreach(t => times.getOrElseUpdate(t, PerTime.empty).++)
      }
    }
    val sortedByTime: Array[(Double, PerTime)] = times.toArray.sortBy(_._1)
    def markEvents(name: String, flag: Long) {
      wcon.custom("@XJ")(name) match {
        case ja: Json.Arr =>
          var i = 0
          while (i < ja.size) {
            val t = ja(i).double
            if (!(java.lang.Double.isNaN(t) || java.lang.Double.isInfinite(t))) {
              val pt = times.get(t).getOrElse(bisect(sortedByTime)(_._1, t)._2)
              pt.evt |= flag
            }
            i += 1
          }
        case _ =>
      }
    }
    markEvents("tap", 0x1L)
    markEvents("puff", 0x2L)
    markEvents("custom 1", 0x4L)
    markEvents("custom 2", 0x8L)
    val summary = sortedByTime.zipWithIndex.map{ case ((t, pt), i) =>
      pt.frame = i + 1
      f"${pt.frame} ${sf3(t)}  ${pt.count} ${pt.count} 0  0 0  0 0  0 0  0 0  0 0${pt.evtstr}${pt.hibyestr}"
    }

    val blobs: Array[(String, Array[String])] = datas.grouped(1000).toArray.zipWithIndex.map{ case (group, gi) =>
      val lines = Array.newBuilder[String]
      group.foreach{ data =>
        lines += "% " + data.id
        var i = 0
        while (i < data.ts.length) {
          val ti = data.ts(i)
          val (t, pt) = times.get(ti).map(ti -> _).getOrElse(bisect(sortedByTime)(_._1, ti))
          val cx = (if (data.cxs.length > 0) data.cxs(i) else data.x(i, data.spineN(i)/2))/mm_per_pixel
          val cy = (if (data.cys.length > 0) data.cys(i) else data.y(i, data.spineN(i)/2))/mm_per_pixel
          val a = data.custom("@XJ")("area")(i).double match {
            case x if java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x) => 0
            case x                                           => math.rint(x/(mm_per_pixel*mm_per_pixel)).toInt
          }
          val bl = data.custom("@XJ")("box-length")(i).double match {
            case x if java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x) => 0.0
            case x                                            => x/mm_per_pixel
          }
          val bw = data.custom("@XJ")("box-width")(i).double match {
            case x if java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x) => 0.0
            case x                                            => x/mm_per_pixel
          }
          val contour = 
            if (data.walks.isEmpty) ""
            else {
              val wi = data.walks.get.apply(i)
              if (wi.path.length == 0) ""
              else {
                val sb = new java.lang.StringBuilder
                sb append " %% "
                sb append math.rint(wi.x0/mm_per_pixel).toInt
                sb append ' '
                sb append math.rint(wi.y0/mm_per_pixel).toInt
                sb append ' '
                sb append wi.n
                sb append ' '
                var bits = wi.path(0) & 0xFF
                var nb = 8
                var bi = 1
                var n = 0
                while (n+2 < wi.n) {
                  if (nb < 6) {
                    bits = bits | ((wi.path(bi) & 0xFF) << nb)
                    bi += 1
                    nb += 8
                  }
                  val old = '0' + (((bits&0x3) << 4) | (bits & 0xC) | ((bits >>> 4) & 0x3))
                  sb append old.toChar
                  nb -= 6
                  bits = bits >>> 6
                  n += 3
                }
                if ((wi.n - n)*2 > nb && bi < wi.path.length) bits = bits | ((wi.path(bi) & 0xFF) << nb)
                if (wi.n - n == 2) sb append ('0' + (((bits&0x3) << 4) | (bits & 0xC))).toChar
                else if (wi.n - n == 1) sb append ('0' + ((bits&0x3) << 4)).toChar
                sb.toString
              }
            }
          lines += f"${pt.frame} ${sf3(t)}  ${sf3(cx)} ${sf3(cy)}  $a  0 1  0  ${sf3(bl)} ${sf3(bw)}$contour"
          i += 1
        }
      }
      f"${inner}_$gi%05dk.blobs" -> lines.result
    }

    // Normally we'll replace the existing file, but for testing we'll just create new one(s)
    var target = new File(f.getParentFile, dir + ".zip")
    var n = 1
    while (target.exists) { target = new File(f.getParentFile, f"$dir ($n).zip"); n += 1 }

    val env = new java.util.HashMap[String, String]
    env.put("create", "true")
    val zfs = FileSystems.newFileSystem(java.net.URI.create("jar:" + target.toURI), env, null)
    try {
      val base = zfs.getPath(dir)
      Files.createDirectory(base)
      val sump = base.resolve(inner + ".summary")
      Files.write(sump, java.util.Arrays.asList(summary: _*), Soo.CREATE)
      blobs.foreach{ case (fn, lines) =>
        val blop = base.resolve(fn)
        Files.write(blop, java.util.Arrays.asList(lines: _*), Soo.CREATE)
      }
      set.foreach{ s =>
        Files.write(base.resolve(inner+".set"), s.getBytes, Soo.CREATE)
      }
      val yfs = FileSystems.newFileSystem(f.toPath, null)
      var png: Option[Path] = None
      var depth = 0
      Files.walkFileTree(yfs.getPath("/"), new FileVisitor[Path] {
        def preVisitDirectory(dir: Path, attrs: Bfa) = {
          if (depth < 2) { depth += 1; Fvr.CONTINUE }
          else Fvr.SKIP_SUBTREE
        }
        def postVisitDirectory(dir: Path, ioe: java.io.IOException) = {
          if (depth > 0) depth -= 1
          Fvr.CONTINUE
        }
        def visitFile(file: Path, attrs: Bfa) = {
          val name = file.getFileName.toString
          if (name == inner + ".png") png = Some(file)
          Fvr.CONTINUE
        }
        def visitFileFailed(file: Path, ioe: java.io.IOException) = Fvr.CONTINUE
      })
      png.foreach(p => Files.copy(p, base.resolve(inner+".png"), Sco.COPY_ATTRIBUTES))
    }
    finally { zfs.close }
  }

  def convert(argi: String) { convert(new File(argi)) }

  def main(args: Array[String]) {
    if (args.isEmpty) throw new Exception("No file names to convert.")
    args.foreach(argi => convert(argi))
  }
}
