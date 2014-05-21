package pellucid
package pframe

import spire.algebra.{ Eq, Order, Semigroup, Monoid }
import spire.syntax.order._
import spire.syntax.semigroup._

/** A [[Cell]] represents a single piece of data that may not be
  * available or meangingful in a given context.
  *
  * Essentially, a [[Cell]] is similar to `Option`, except instead of
  * `None` we have 2 representations of [[NonValue]], the absence of
  * data: [[NA]] (Not Available) and [[NM]] (Not Meaningful).
  *
  * @tparam A the value type contain in the cell
  * @see [[Value]] [[[NonValue]]] [[NA]] [[NM]]
  */
sealed trait Cell[+A] {
  def isValue: Boolean

  def value: Option[A]
  def valueString: String

  def fold[B](na: => B, nm: => B)(f: A => B): B = this match {
    case NA => na
    case NM => nm
    case Value(a) => f(a)
  }

  def getOrElse[B >: A](default: => B): B = this match {
    case Value(a) => a
    case _ => default
  }

  def map[B](f: A => B): Cell[B] = this match {
    case Value(a) => Value(f(a))
    case NA => NA
    case NM => NM
  }

  def flatMap[B](f: A => Cell[B]): Cell[B] = this match {
    case Value(a) => f(a)
    case NA => NA
    case NM => NM
  }

  def filter(f: A => Boolean): Cell[A] = this match {
    case Value(a) if !f(a) => NA
    case other => other
  }

  def foreach[U](f: A => U): Unit = this match {
    case Value(a) => f(a)
    case _ =>
  }
}

// TODO: there are currently issues where we get comparison between Value(NA) and NA and this should be true
// the current tweaks to equality are just holdovers until we figure out some more details on the implementation
// of non values.
object Cell extends CellInstances {
  def value[A](x: A): Cell[A] = Value(x)
  def notAvailable[A]: Cell[A] = NA
  def notMeaningful[A]: Cell[A] = NM

  def fromOption[A](opt: Option[A], nonValue: NonValue = NA): Cell[A] = opt match {
    case Some(a) => Value(a)
    case None => nonValue
  }

  implicit def cell2Iterable[A](cell: Cell[A]): Iterable[A] = cell.value.toList
}

/** The supertype of non values, [[NA]] (''Not Available'') and
  * [[NM]] (''Not Meaningful'')
  *
  * @see [[Cell]] [[NA]] [[NM]]
  */
sealed trait NonValue extends Cell[Nothing] {
  def isValue = false
  def value = None

  override def equals(that: Any): Boolean = that match {
    case Value(thatValue) => this == thatValue
    case _ => super.equals(that)
  }
}


/** A value is ''Not Available (NA)''
  *
  * This represents the absence of any data.
  *
  * @see [[Cell]] [[NonValue]] [[NM]] [[Value]]
  */
case object NA extends NonValue { val valueString = "NA" }


/** The value is ''Not Meaningful (NM)''.
  *
  * This indicates that data exists, but that it is not meaningful.
  * For instance, if we divide by 0, then the result is not
  * meaningful, but we wouldn't necessarily say that data is
  * unavailable.
  *
  * @see [[Cell]] [[NonValue]] [[NA]] [[Value]]
  */
case object NM extends NonValue { val valueString = "NM" }


/** A value that is meaningful.
  *
  * @tparam A the type of the value contained
  * @see [[Cell]] [[NonValue]] [[NA]] [[NM]]
  */
final case class Value[+A](get: A) extends Cell[A] {
  def value = Some(get)
  def valueString = get.toString

  val isValue = if (get == NA || get == NM) false else true

  override def equals(that: Any): Boolean = that match {
    case Value(Value(NA)) => get == NA
    case Value(Value(NM)) => get == NM
    case Value(thatValue) => thatValue == get
    case v @ NA => get == NA
    case v @ NM => get == NM
    case _ => false
  }
}

trait CellInstances0 {
  implicit def cellEq[A: Eq]: Eq[Cell[A]] = new CellEq[A]
}

trait CellInstances extends CellInstances0 {
  implicit def cellOrder[A: Order]: Order[Cell[A]] = new CellOrder[A]
  implicit def cellMonoid[A: Semigroup]: Monoid[Cell[A]] = new CellMonoid[A]
}

@SerialVersionUID(0L)
private final class CellEq[A: Eq] extends Eq[Cell[A]] {
  import spire.syntax.eq._

  def eqv(x: Cell[A], y: Cell[A]): Boolean = (x, y) match {
    case (Value(x0), Value(y0)) => x0 === y0
    case (NA, NA) | (NM, NM) => true
    case _ => false
  }

  /*def eqv[X >: A: Eq, Y >: A: Eq](x: Cell[X], y: Cell[Y]): Boolean = (x, y) match {
    case (Value(NA), NA) | (Value(NM), NM) | (NA, Value(NA)) | (NM, Value(NM)) => true
    case _ => false
  }*/
}

@SerialVersionUID(0L)
private final class CellOrder[A: Order] extends Order[Cell[A]] {
  def compare(x: Cell[A], y: Cell[A]): Int = (x, y) match {
    case (Value(x0), Value(y0)) => x0 compare y0
    case (NA, NA) | (NM, NM) => 0
    case (NA, _) => -1
    case (_, NA) => 1
    case (NM, _) => -1
    case (_, NM) => 1
  }
}

@SerialVersionUID(0L)
private final class CellMonoid[A: Semigroup] extends Monoid[Cell[A]] {
  def id: Cell[A] = NA
  def op(x: Cell[A], y: Cell[A]): Cell[A] = (x, y) match {
    case (NM, _) => NM
    case (_, NM) => NM
    case (Value(a), Value(b)) => Value(a |+| b)
    case (Value(_), _) => x
    case (_, Value(_)) => y
    case (NA, NA) => NA
  }
}
