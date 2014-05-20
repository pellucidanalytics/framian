package pellucid.pframe

import org.joda.time.LocalDate

import scala.annotation.tailrec
import scala.reflect.ClassTag

import spire.algebra._

package object reduce {
  def MonoidReducer[A: Monoid]: Reducer[A, A] = new MonoidReducer[A]

  def SemigroupReducer[A: Semigroup]: Reducer[A, A] = new SemigroupReducer[A]

  def Mean[A: Field]: Reducer[A, A] = new Mean[A]

  def Sum[A: AdditiveMonoid]: Reducer[A, A] = MonoidReducer(spire.algebra.Monoid.additive[A])

  def Median[A: Field: Order: ClassTag]: Reducer[A, A] = new Median

  def Quantile[A: Field: Order: ClassTag](probabilities: Seq[Double] = Seq(0, .25, .5, .75, 1)): Reducer[A, Seq[(Double, A)]] =
    new Quantile(probabilities)

  def Outliers[A: Field: Order: ClassTag](k: Double = 1.5): Reducer[A, (Option[A], Option[A])] =
    new Outliers(k)

  def Max[A: Order]: Reducer[A, A] = new Max[A]

  def Min[A: Order]: Reducer[A, A] = Max(Order[A].reverse)

  def First[A]: Reducer[A, A] = new First[A]

  def FirstN[A](n: Int): Reducer[A, List[A]] = new FirstN[A](n)

  def Last[A]: Reducer[A, A] = new Last[A]

  def LastN[A](n: Int): Reducer[A, List[A]] = new LastN[A](n)

  def Current[A]: Reducer[(LocalDate, A), A] = new Current[A]

  def Unique[A]: Reducer[A, Set[A]] = new Unique[A]
}
