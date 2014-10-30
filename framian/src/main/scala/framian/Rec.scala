/*  _____                    _
 * |  ___| __ __ _ _ __ ___ (_) __ _ _ __
 * | |_ | '__/ _` | '_ ` _ \| |/ _` | '_ \
 * |  _|| | | (_| | | | | | | | (_| | | | |
 * |_|  |_|  \__,_|_| |_| |_|_|\__,_|_| |_|
 *
 * Copyright 2014 Pellucid Analytics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package framian

import scala.language.implicitConversions

import scala.reflect.ClassTag

import spire.algebra.Order

final class TypeWitness[A](val value: A)(implicit val classTag: ClassTag[A])
object TypeWitness {
  implicit def lift[A: ClassTag](a: A) = new TypeWitness[A](a)
}

/**
 * A `Rec` is an untyped sequence of values - usually corresponding to a row or
 * column in a Frame.
 */
final class Rec[K](cols: Series[K, UntypedColumn], row: Int) {
  def get[A: ColumnTyper](col: K): Cell[A] =
    cols(col) flatMap (_.cast[A].apply(row))

  def values: Iterable[(K, Cell[Any])] = cols.to[Vector] map { case (k, colCell) =>
    val value = for {
      col <- colCell
      a <- col.cast[Any].apply(row)
    } yield a

    k -> value
  }

  override def toString: String = values.map { case (k, value) =>
    s"""$k -> ${value.fold("na", "nm")(_.toString)}"""
  }.mkString("Rec(", ", ", ")")

  override def equals(that: Any): Boolean = that match {
    case (that: Rec[_]) => this.values == that.values
    case _ => false
  }

  override def hashCode: Int = this.values.hashCode * 23
}

object Rec {
  def apply[K: Order: ClassTag](kvs: (K, TypeWitness[_])*): Rec[K] = {
    val cols: Series[K, UntypedColumn] = Series(kvs.map { case (k, w: TypeWitness[a]) =>
      k -> TypedColumn[a](Column.const(w.value))(w.classTag)
    }: _*)
    new Rec(cols, 0)
  }

  def fromRow[K](frame: Frame[_, K])(row: Int): Rec[K] =
    new Rec(frame.columnsAsSeries, row)

  def fromCol[K](frame: Frame[K, _])(col: Int): Rec[K] =
    new Rec(frame.rowsAsSeries, col)

  implicit def RecRowExtractor[K]: RowExtractor[Rec[K], K, Variable] = new RowExtractor[Rec[K], K, Variable] {
    type P = Series[K, UntypedColumn]

    def prepare(cols: Series[K, UntypedColumn], keys: List[K]): Option[P] = {
      import cols.index.{ order, classTag }
      Some(Series.fromCells(keys map { k => k -> cols(k) }: _*))
    }

    def extract(row: Int, cols: P): Cell[Rec[K]] =
      Value(new Rec(cols, row))
  }
}
