package framian

import org.specs2.mutable._
import org.specs2.{ScalaCheck}

import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

import spire.algebra._
import spire.math.Rational
import spire.laws._
import spire.syntax.eq._
import spire.implicits.IntAlgebra

import org.typelevel.discipline.specs2.mutable.Discipline

/* extends Properties("ColumnAlgebras")*/
class ColumnAlgebrasSpec extends Specification with ScalaCheck with Discipline  {
  import ColumnGenerators.arbColumn

  // We use a pretty sketchy notion of equality here. Basically, pretending that
  // only real values matter. Also, actually only checking rows 0-1000 is bad too.
  implicit def ColumnEq[A: Eq] = new Eq[Column[A]] {
    def eqv(lhs: Column[A], rhs: Column[A]): Boolean = (0 to 1000) forall { row =>
      (lhs(row), rhs(row)) match {
        case (Value(x), Value(y)) => x === y
        case _ => true
      }
    }
  }

  def genRational: Gen[Rational] = for {
    n <- arbitrary[Long] map {
      case Long.MinValue => Long.MinValue + 1
      case n => n
    }
    d <- arbitrary[Long] map {
      case 0L => 1L
      case Long.MinValue => Long.MinValue + 1
      case n => n
    }
  } yield Rational(n, d)

  implicit def arbRational = Arbitrary(genRational)

  "ColumnAlgebras" should {
    checkAll("Int column ring", RingLaws[Column[Int]].ring)
    checkAll("Rational column field", RingLaws[Column[Rational]].field)
  }
}
