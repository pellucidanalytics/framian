package framian.column

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

class MaskSpec extends Specification with ScalaCheck {
  val genMask: Gen[Mask] = for {
    rows0 <- arbitrary[List[Int]]
    rows = rows0.map(_ & 0xFFFF)
  } yield Mask(rows: _*)

  implicit val arbMask: Arbitrary[Mask] = Arbitrary(genMask)

  case class Filter(f: Int => Boolean) extends (Int => Boolean) {
    def apply(n: Int): Boolean = f(n)
  }

  val genFilter: Gen[Filter] = for {
    mods0 <- arbitrary[List[Int]]
    mods = mods0.map(_ & 0xFF).map(_ + 1).toSet
  } yield Filter(n => mods.exists(n % _ == 0))

  implicit val arbFilter: Arbitrary[Filter] = Arbitrary(genFilter)

  "+" should {
    "add 0 to empty mask" in {
      (Mask.empty + 0) must_== Mask(0)
    }

    "set bit of value added" in check { (mask: Mask, bit: Int) =>
      (mask + bit)(bit) must beTrue
    }
  }

  "-" should {
    "unset bit of value removed" in check { (mask: Mask, bit: Int) =>
      (mask - bit)(bit) must beFalse
    }

    "shrink underlying array when top words zero'd out" in {
      val a = Mask(1, 100)
      (a - 100).max must_== Some(1)
    }
  }

  "filter" should {
    "remove bits that are filtered out" in check { (mask: Mask, filter: Filter) =>
      mask.filter(filter).toSet must_== mask.toSet.filter(filter)
    }
  }

  "min" should {
    "return minimum set bit" in check { (mask: Mask) =>
      mask.toSet.reduceOption(_ min _) must_== mask.min
    }

    "return None for empty Mask" in {
      Mask.empty.min must beNone
    }
  }

  "max" should {
    "return maximum set bit" in check { (mask: Mask) =>
      mask.toSet.reduceOption(_ max _) must_== mask.max
    }

    "return None for empty Mask" in {
      Mask.empty.max must beNone
    }
  }

  "isEmpty" should {
    "return empty iff the mask is empty" in check { (mask: Mask) =>
      mask.toSet.isEmpty must_== mask.isEmpty
    }
  }

  "foreach" should {
    "iterate over bits in increasing order" in check { (mask: Mask) =>
      val bldr = List.newBuilder[Int]
      mask.foreach(bldr += _)
      bldr.result() must beSorted
    }

    "iterate only over values in the mask" in check { (mask: Mask) =>
      val bldr = List.newBuilder[Int]
      mask.foreach(bldr += _)
      bldr.result().forall(mask) must beTrue
    }

    "iterate over all values in mask" in check { (bits0: List[Int]) =>
      val bits1 = bits0.map(_ & 0xFFFF) // Sane sizes
      val mask = Mask(bits1: _*)
      var bits = bits1.toSet
      mask.foreach(i => bits -= i)
      bits must beEmpty
    }
  }

  "toSet" should {
    "round-trip Mask->Set->Mask" in check { (mask: Mask) =>
      mask must_== Mask(mask.toSet.toList: _*)
    }

    "round-trip Set->Mask->Set" in check { (bits0: List[Int]) =>
      val bits = bits0.map(_ & 0xFFFF).toSet
      Mask(bits.toList: _*).toSet must_== bits
    }
  }

  "|" should {
    "only contain bits from either arguments" in check { (a: Mask, b: Mask) =>
      val mask = (a | b)
      mask.toSet.forall(i => a(i) || b(i)) must beTrue
    }

    "contain all bits from both arguments" in check { (a: Mask, b: Mask) =>
      val mask = a | b
      val bits = a.toSet ++ b.toSet
      bits.forall(mask) must beTrue
    }
  }

  "&" should {
    "only contain bits contained in both arguments" in check { (a: Mask, b: Mask) =>
      val mask = (a & b)
      mask.toSet.forall(i => a(i) && b(i)) must beTrue
    }

    "contain all bits that are in both arguments" in check { (a: Mask, b: Mask) =>
      val mask = a & b
      val bits = a.toSet & b.toSet
      bits.forall(mask) must beTrue
    }

    "shrink array if top zero'd out" in {
      val a = Mask(1, 100)
      val b = Mask(1, 101)
      (a & b).max must_== Some(1)
    }
  }

  "--" should {
    "not contain bits in right-hand side" in check { (a: Mask, b: Mask) =>
      val mask = a -- b
      b.toSet.forall(i => !mask(i)) must beTrue
    }

    "contain bits in the lhs but not the rhs" in check { (a: Mask, b: Mask) =>
      val mask = a -- b
      val setBits = a.toSet -- b.toSet
      setBits.forall(mask) must beTrue
    }
  }

  "equals" should {
    "always be equal for equivalent masks" in check { (a: Mask) =>
      val b = Mask(a.toSet.toSeq: _*)
      a must_== b
    }

    "should not throw IOOE when size are equal but lengths are not" in {
      val a = Mask(1, 2, 3)
      val b = Mask(1000, 1001, 2000)
      (a == b) must not(throwA[Exception])
      (b == a) must not(throwA[Exception])
    }
  }
}
