package framian.column

import java.lang.Integer.highestOneBit
import java.lang.Long.bitCount
import java.util.Arrays

import scala.annotation.tailrec
import scala.collection.immutable.BitSet
import scala.collection.mutable.ArrayBuilder

import spire.implicits._

class MaskBuilder {
  var len = 0
  var size = 0
  var bits = new Array[Long](8)

  private def resize(newLen: Int): Unit = {
    bits = Arrays.copyOf(bits, highestOneBit(newLen) * 2)
    len = newLen
  }

  def +=(n: Int): this.type = {
    val i = n >>> 6
    if (i >= bits.length)
      resize(i + 1)
    if (i >= len)
      len = i + 1
    val word = bits(i)
    val bit = 1L << (n & 0x3F)
    if ((word & bit) == 0) {
      bits(i) = word | bit
      size += 1
    }
    this
  }

  def result(): Mask = {
    val bits0 = Arrays.copyOf(bits, len)
    new Mask(bits0, size)
  }
}

object Mask {
  def newBuilder: MaskBuilder = new MaskBuilder

  final val empty = new Mask(new Array[Long](0), 0)

  def apply(elems: Int*): Mask = {
    val bldr = new MaskBuilder
    elems.foreach(bldr += _)
    bldr.result()
  }

  def range(from: Int, until: Int): Mask =
    Mask(from.until(until): _*)

  final def fromBits(bits: Array[Long]): Mask = {
    var i = 0
    var size = 0
    while (i < bits.length) {
      size += bitCount(bits(i))
      i += 1
    }
    new Mask(bits, size)
  }
}

final class Mask(private val bits: Array[Long], val size: Int) {
  def foreach[U](f: Int => U): Unit = {
    var i = 0
    while (i < bits.length) {
      val word = bits(i)
      val hi = i << 6
      var lo = 0
      while (lo < 64) {
        if ((word & (1L << lo)) != 0)
          f(hi | lo)
        lo += 1
      }
      i += 1
    }
  }

  def |(that: Mask): Mask = {
    val size = math.max(bits.length, that.bits.length)
    val bits0 = Arrays.copyOf(that.bits, size)
    var i = 0
    while (i < bits.length) {
      bits0(i) |= bits(i)
      i += 1
    }
    Mask.fromBits(bits0)
  }

  def &(that: Mask): Mask = {
    val size = math.min(bits.length, that.bits.length)
    val bits0 = Arrays.copyOf(that.bits, size)
    var i = 0
    while (i < bits0.length) {
      bits0(i) &= bits(i)
      i += 1
    }
    Mask.fromBits(bits0)
  }

  final def ++(that: Mask): Mask = this | that

  def --(that: Mask): Mask = {
    val bldr = new MaskBuilder
    foreach { i => if (!that(i)) bldr += i }
    bldr.result()
  }

  def +(n: Int): Mask = {
    val hi = n >>> 6
    val bit = 1L << (n & 0x3F)

    if (hi < bits.length && (bits(hi) & bit) == 0) {
      this
    } else {
      val len = math.max(bits.length, n)
      val bits0 = Arrays.copyOf(bits, len)
      bits0(hi) |= bit
      new Mask(bits0, size + 1)
    }
  }

  def filter(f: Int => Boolean): Mask = {
    val bldr = new MaskBuilder
    foreach { i => if (f(i)) bldr += i }
    bldr.result()
  }

  def apply(n: Int): Boolean = {
    val hi = n >>> 6
    if (hi < bits.length)
      (bits(hi) & (1L << (n & 0x3F))) != 0L
    else
      false
  }

  def toSet: Set[Int] = {
    val bldr = Set.newBuilder[Int]
    foreach(bldr += _)
    bldr.result()
  }

  override def toString: String =
    toSet.mkString("Mask(", ", ", ")")
}
