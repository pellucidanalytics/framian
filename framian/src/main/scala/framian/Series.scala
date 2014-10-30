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

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.{ ArrayBuilder, Builder }
import scala.collection.{ IterableLike, Iterable }
import scala.reflect.ClassTag

import spire.algebra._
import spire.math._
import spire.std.double._
import spire.std.int._
import spire.syntax.additiveMonoid._
import spire.syntax.monoid._
import spire.syntax.order._
import spire.syntax.cfor._

import spire.compat._

import framian.columns.MappedColumn
import framian.reduce.Reducer
import framian.util.TrivialMetricSpace

final class Series[K,V](val index: Index[K], val column: Column[V]) {

  private implicit def classTag = index.classTag
  private implicit def order = index.order

  @inline
  def size: Int = index.size

  /** Returns this series as a collection of key/value pairs. */
  def to[CC[_]](implicit cbf: CanBuildFrom[Nothing, (K, Cell[V]), CC[(K, Cell[V])]]): CC[(K, Cell[V])] = {
    val bldr = cbf()
    bldr.sizeHint(size)
    iterator.foreach { bldr += _ }
    bldr.result()
  }

  /** Returns an iterator over the key-cell pairs of the series.
    * @return an iterator over the key-cell pairs of the series.
    * @see [[denseIterator]]
    * @note If the series is known to be dense, or the non values
    * can ignored, then one should use [[denseIterator]] instead.
    */
  def iterator: Iterator[(K, Cell[V])] = index.iterator map { case (k, row) =>
    k -> column(row)
  }

  /** Returns an iterator over the key-value pairs of the series.
    *
    * This iterator assumes that the series is dense, so it will
    * skip over any non value cells if, in fact, the series is sparse.
    *
    * @return an iterator over the key-value pairs of the series.
    * @see [[iterator]]
    * @note The `Iterator` returned by this method is related to
    * the `Iterator` returned by [[iterator]]
    * {{{
    * series.iterator.collect { case (k, Value(v)) => k -> v} == series.denseIterator
    * }}}
    * however, this method uses a more efficient access pattern
    * to the underlying data.
    */
  def denseIterator: Iterator[(K, V)] =
    index.iterator collect { case (k, ix) if column.isValueAt(ix) =>
      k -> column.valueAt(ix)
    }


  /** Applies a function `f` to all key-cell pairs of the series.
    *
    * The series is traversed in index order.
    *
    * @param  f  the function that is applied for its side-effect
    *            to every key-cell pair. The result of the
    *            function `f` is discarded
    * @see [[foreachCells]]
    * @see [[foreachDense]]
    * @see [[foreachKeys]]
    * @see [[foreachValues]]
    */
  def foreach[U](f: (K, Cell[V]) => U): Unit = {
    cfor(0)(_ < index.size, _ + 1) { i =>
      f(index.keyAt(i), column(index.indexAt(i)))
    }
  }


  /** Applies a function `f` to all key-value pairs of the series.
    *
    * The series is traversed in index order.
    *
    * This method assumes that the series is dense, so it will skip
    * over any non value cells if, in fact, the series is sparse.
    *
    * @param  f  the function that is applied for its side-effect
    *            to every key-value pair. The result of the
    *            function `f` is discarded
    * @see [[foreach]]
    * @see [[foreachCells]]
    * @see [[foreachKeys]]
    * @see [[foreachValues]]
    */
  def foreachDense[U](f: (K, V) => U): Unit = {
    cfor(0)(_ < index.size, _ + 1) { i =>
      val ix = index.indexAt(i)
      if (column.isValueAt(ix)) {
        f(index.keyAt(i), column.valueAt(ix))
      }
    }
  }


  /** Applies a function `f` to all keys of the series.
    *
    * The series is traversed in index order.
    *
    * @param  f  the function that is applied for its side-effect
    *            to every key. The result of the
    *            function `f` is discarded
    * @see [[foreach]]
    * @see [[foreachCells]]
    * @see [[foreachDense]]
    * @see [[foreachValues]]
    */
  def foreachKeys[U](f: K => U): Unit = {
    cfor(0)(_ < index.size, _ + 1) { i =>
      f(index.keyAt(i))
    }
  }


  /** Applies a function `f` to all cells of the series.
    *
    * The series is traversed in index order.
    *
    * @param  f  the function that is applied for its side-effect
    *            to every cell. The result of the
    *            function `f` is discarded
    * @see [[foreach]]
    * @see [[foreachDense]]
    * @see [[foreachKeys]]
    * @see [[foreachValues]]
    */
  def foreachCells[U](f: Cell[V] => U): Unit = {
    cfor(0)(_ < index.size, _ + 1) { i =>
      f(column(index.indexAt(i)))
    }
  }


  /** Applies a function `f` to all values of the series.
    *
    * The series is traversed in index order.
    *
    * This method assumes that the series is dense, so it will skip
    * over any non value cells if, in fact, the series is sparse.
    *
    * @param  f  the function that is applied for its side-effect
    *            to every value. The result of the
    *            function `f` is discarded
    * @see [[foreach]]
    * @see [[foreachCells]]
    * @see [[foreachDense]]
    * @see [[foreachKeys]]
    */
  def foreachValues[U](f: V => U): Unit = {
    cfor(0)(_ < index.size, _ + 1) { i =>
      val ix = index.indexAt(i)
      if (column.isValueAt(ix)) {
        f(column.valueAt(ix))
      }
    }
  }

  /** Returns the keys of this series as a vector in index order.
    *
    * @return a vector of the keys of the series.
    * @see [[cells]]
    * @see [[values]]
    */
  def keys: Vector[K] = {
    val builder = Vector.newBuilder[K]
    cfor(0)(_ < index.size, _ + 1) { i =>
      builder += index.keyAt(i)
    }
    builder.result()
  }


  /** Return the cells of this series as a vector in index order.
    *
    * The series may be sparse, so the vector contains [[Cell]]s
    * rather than just plain values.
    *
    * @return a sparse vector of the values of the series.
    * @see [[keys]]
    * @see [[values]]
    */
  def cells: Vector[Cell[V]] = {
    val builder = Vector.newBuilder[Cell[V]]
    cfor(0)(_ < index.size, _ + 1) { i =>
      builder += column(index.indexAt(i))
    }
    builder.result()
  }


  /** Returns the values of this series as a vector in index order.
    *
    * If the series is dense, this returns the values directly,
    * rather than wrapped in [[Cell]]. If the series is infact sparse,
    * the [[NonValue]]s are ignored.
    *
    * @return a dense vector of the values of the series.
    * @see [[keys]]
    * @see [[values]]
    * @note The `Vector` returned by this method is related to
    * the `Vector` returned by [[values]]
    * {{{
    * series.cells.collect { case Value(v) => v } == series.values
    * }}}
    * however, this method uses a more efficient access pattern
    * to the underlying data.
    */
  def values: Vector[V] = {
    val builder = Vector.newBuilder[V]
    cfor(0)(_ < index.size, _ + 1) { i =>
      val ix = index.indexAt(i)
      if (column.isValueAt(ix)) {
        builder += column.valueAt(ix)
      }
    }
    builder.result()
  }


  @inline
  def keyAt(i: Int): K = index.keyAt(i)

  @inline
  def cellAt(i: Int): Cell[V] = column(index.indexAt(i))

  @inline
  def valueAt(i: Int): V = column.valueAt(index.indexAt(i))

  @inline
  def apply(key: K): Cell[V] = index.get(key) map (column(_)) getOrElse NA

  /**
   * Returns the values associated with keys `ks`. This will only return a
   * dense collection. If there is an `NM` in the values, then `NM` is
   * returned, otherwise if a value is `NA`, then `NA` is returned.
   */
  def select[CC[A] <: Iterable[A]](ks: CC[K])(implicit cbf: CanBuildFrom[CC[K], V, CC[V]]): Cell[CC[V]] =
    Cell.traverse(ks) { k => apply(k) }

  /**
   * Returns `true` if at least 1 value exists in this series. A series with
   * only `NA`s and/or `NM`s will return `false`.
   */
  def hasValues: Boolean = {
    var i = 0
    var seenValue = false
    while (i < index.size && !seenValue) {
      val row = index.indexAt(i)
      seenValue = seenValue | column.isValueAt(row)
      i += 1
    }
    seenValue
  }

  /**
   * Merges 2 series together using a semigroup to append values.
   */
  def merge[VV >: V: Semigroup: ClassTag](that: Series[K, VV]): Series[K, VV] = {
    val merger = Merger[K](Merge.Outer)
    val (keys, lIndices, rIndices) = Index.cogroup(this.index, that.index)(merger).result()
    val lCol = this.column
    val rCol = that.column

    // TODO: Remove duplication between this and zipMap.
    val bldr = Column.builder[VV]
    cfor(0)(_ < lIndices.length, _ + 1) { i =>
      val l = lIndices(i)
      val r = rIndices(i)
      val lExists = lCol.isValueAt(l)
      val rExists = rCol.isValueAt(r)
      if (lExists && rExists) {
        bldr.addValue((lCol.valueAt(l): VV) |+| rCol.valueAt(r))
      } else if (lExists) {
        if (rCol.nonValueAt(r) == NM) bldr.addNM()
        else bldr.addValue(lCol.valueAt(l))
      } else if (rExists) {
        if (lCol.nonValueAt(l) == NM) bldr.addNM()
        else bldr.addValue(rCol.valueAt(r))
      } else {
        bldr.addNonValue(if (lCol.nonValueAt(l) == NM) NM else rCol.nonValueAt(r))
      }
    }

    Series(Index.ordered(keys), bldr.result())
  }

  /**
   * Concatenates `that` onto the end of `this` [[Series]].
   */
  def ++[VV >: V: ClassTag](that: Series[K, VV]): Series[K, VV] = {
    val bldr = Series.newUnorderedBuilder[K, VV]
    this.foreach(bldr.append)
    that.foreach(bldr.append)
    bldr.result()
  }

  /**
   * Merges 2 series together, taking the first non-NA or NM value.
   */
  def orElse[VV >: V: ClassTag](that: Series[K, VV]): Series[K, VV] = {
    val merger = Merger[K](Merge.Outer)
    val (keys, lIndices, rIndices) = Index.cogroup(this.index, that.index)(merger).result()
    val lCol = this.column
    val rCol = that.column

    // TODO: Remove duplication between this and zipMap.
    val bldr = Column.builder[VV]
    cfor(0)(_ < lIndices.length, _ + 1) { i =>
      val l = lIndices(i)
      val r = rIndices(i)
      val lExists = lCol.isValueAt(l)
      val rExists = rCol.isValueAt(r)
      if (lExists && rExists) {
        bldr.addValue(lCol.valueAt(l): VV)
      } else if (lExists) {
        bldr.addValue(lCol.valueAt(l))
      } else if (rExists) {
        bldr.addValue(rCol.valueAt(r))
      } else {
        bldr.addNonValue(if (lCol.nonValueAt(l) == NM) NM else rCol.nonValueAt(r))
      }
    }

    Series(Index.ordered(keys), bldr.result())
  }

  /**
   * Perform an inner join with `that` and group the values in tuples.
   *
   * Equivalent to calling `lhs.zipMap(rhs)((_, _))`.
   */
  def zip[W](that: Series[K, W]): Series[K, (V, W)] =
    zipMap(that)((_, _))

  /**
   * Performs an inner join on this `Series` with `that`. Each pair of values
   * for a matching key is passed to `f`.
   */
  def zipMap[W, X: ClassTag](that: Series[K, W])(f: (V, W) => X): Series[K, X] = {
    val joiner = Joiner[K](Join.Inner)
    val (keys, lIndices, rIndices) = Index.cogroup(this.index, that.index)(joiner).result()
    val bldr = Column.builder[X]

    val lCol = this.column
    val rCol = that.column
    cfor(0)(_ < lIndices.length, _ + 1) { i =>
      val l = lIndices(i)
      val r = rIndices(i)
      val lExists = lCol.isValueAt(l)
      val rExists = rCol.isValueAt(r)
      if (lExists && rExists) {
        bldr.addValue(f(lCol.valueAt(l), rCol.valueAt(r)))
      } else if (lExists) {
        bldr.addNonValue(rCol.nonValueAt(r))
      } else if (rExists) {
        bldr.addNonValue(lCol.nonValueAt(l))
      } else {
        bldr.addNonValue(if (lCol.nonValueAt(l) == NM) NM else rCol.nonValueAt(r))
      }
    }
    Series(Index.ordered(keys), bldr.result())
  }

  /**
   * Sort this series by index keys and return it. The sort should be stable,
   * so the relative order within a key will remain the same.
   */
  def sorted: Series[K, V] = Series(index.sorted, column)

  /**
   * Convert this Series to a single column [[Frame]].
   */
  def toFrame[C: Order: ClassTag](col: C)(implicit tt: ClassTag[V]): Frame[K, C] =
    ColOrientedFrame(index, Series(col -> TypedColumn(column)))

  def toFrame(implicit tt: ClassTag[V]): Frame[K, Int] = {
    import spire.std.int._
    toFrame(0)
  }

  def closestKeyTo(k: K, tolerance: Double)(implicit K0: MetricSpace[K, Double], K1: Order[K]): Option[K] =
    apply(k) match {
      case Value(v) => Some(k)
      case _ =>
        keys.collectFirst {
          case key if MetricSpace.closeTo[K, Double](k, key, tolerance) => key
        }
    }

  /**
   * Map the keys of this series. This will maintain the same iteration order
   * as the old series.
   */
  def mapKeys[L: Order: ClassTag](f: K => L): Series[L, V] =
    Series(index.map { case (k, i) => f(k) -> i }, column)

  /**
   * Map the values of this series only. Note that the function `f` will be
   * called every time a value is accessed. To prevent this, you must `compact`
   * the Series.
   */
  def mapValues[W](f: V => W): Series[K, W] =
    Series(index, new MappedColumn(f, column)) // TODO: Use a macro here?

  /**
   * Map the values of this series, using both the *key* and *value* of each
   * cell.
   */
  def mapValuesWithKeys[W: ClassTag](f: (K, V) => W): Series[K, W] = {
    val bldr = Column.builder[W]
    index.foreach { (k, row) =>
      bldr += column(row).map(v => f(k, v))
    }
    Series(index.resetIndices, bldr.result())
  }

  /**
   * Transforms the cells in this series using `f`.
   */
  def cellMap[W: ClassTag](f: Cell[V] => Cell[W]): Series[K, W] = {
    val bldr = Column.builder[W]
    index.foreach { (k, row) =>
      bldr += f(column(row))
    }
    Series(index.resetIndices, bldr.result())
  }

  /**
   * Transforms the cells, indexed by their key, in this series using `f`.
   */
  def cellMapWithKeys[W: ClassTag](f: (K, Cell[V]) => Cell[W]): Series[K, W] = {
    val bldr = Column.builder[W]
    index.foreach { (k, row) =>
      bldr += f(k, column(row))
    }
    Series(index.resetIndices, bldr.result())
  }

  /** Select all key-cell pairs of this series where the pairs
    * satisfy a predicate.
    *
    * This method preserves the orderedness of the underlying index.
    *
    * @param  p  the predicate used to test key-cell pairs.
    * @return a new series consisting of all key-cell pairs of this
    *   series where the pairs satisfy the given predicate `p`.
    * @see [[filterByKeys]]
    * @see [[filterByCells]]
    * @see [[filterByValues]]
    */
  def filterEntries(p: (K, Cell[V]) => Boolean)(implicit ev: ClassTag[V]): Series[K, V] = {
    val b = Series.newBuilder[K, V](index.isOrdered)
    b.sizeHint(index.size)
    for ((k, ix) <- index) {
      val cell = column(ix)
      if (p(k, cell)) {
        b.append(k, cell)
      }
    }
    b.result()
  }


  /** Select all key-cell pairs of this series where the keys
    * satisfy a predicate.
    *
    * This method preserves the orderedness of the underlying index.
    *
    * @param  p  the predicate used to test keys.
    * @return a new series consisting of all key-call pairs of this
    *   series where the keys satisfy the given predicate `p`.
    * @see [[filterEntries]]
    * @see [[filterByCells]]
    * @see [[filterByValues]]
    * @note This method is a specialized and optimized version of
    * [[filterEntries]], where
    * {{{
    *   s.filterEntries { (k, _) => p(k) } == s.filterByKeys(p)
    * }}}
    */
  def filterByKeys(p: K => Boolean)(implicit ev: ClassTag[V]): Series[K, V] = {
    val b = Series.newBuilder[K, V](index.isOrdered)
    b.sizeHint(this.size)
    cfor(0)(_ < index.size, _ + 1) { i =>
      val k = index.keyAt(i)
      if (p(k)) {
        b.append(k, column(index.indexAt(i)))
      }
    }
    b.result()
  }


  /** Select all key-cell pairs of this series where the cells
    * satisfy a predicate.
    *
    * This method preserves the orderedness of the underlying index.
    *
    * @param  p  the predicate used to test cells.
    * @return a new series consisting of all key-call pairs of this
    *   series where the cells satisfy the given predicate `p`.
    * @see [[filterEntries]]
    * @see [[filterByKeys]]
    * @see [[filterByValues]]
    * @note This method is a specialized and optimized version of
    * [[filterEntries]], where
    * {{{
    *   s.filterEntries { (_, c) => p(c) } == s.filterByCells(p)
    * }}}
    */
  def filterByCells(p: Cell[V] => Boolean)(implicit ev: ClassTag[V]): Series[K, V] = {
    val b = Series.newBuilder[K, V](index.isOrdered)
    b.sizeHint(this.size)
    cfor(0)(_ < index.size, _ + 1) { i =>
      val cell = column(index.indexAt(i))
      if (p(cell)) {
        b.append(index.keyAt(i), cell)
      }
    }
    b.result()
  }


  /** Selects all key-value pairs of this series where the values
    * satisfy a predicate.
    *
    * This method preserves the orderedness of the underlying index.
    * It also assumes this series is dense, so any non values will
    * also be filtered out. The column that backs the new series
    * will be dense.
    *
    * @param  p  the predicate used to test values.
    * @return a new series consisting of all key-value pairs of this
    *   series where the values satisfy the given predicate `p`.
    * @see [[filterEntries]]
    * @see [[filterByKeys]]
    * @see [[filterByCells]]
    * @note This method is a specialized and optimized version of
    * [[filterEntries]], where
    * {{{
    *   s.filterEntries {
    *     case (_, Value(v)) => p(v)
    *     case _ => false
    *   } == s.filterByValues(p)
    * }}}
    */
  def filterByValues(p: V => Boolean)(implicit ev: ClassTag[V]): Series[K, V] = {
    val b = Series.newBuilder[K, V](index.isOrdered)
    b.sizeHint(this.size)
    cfor(0)(_ < index.size, _ + 1) { i =>
      val ix = index.indexAt(i)
      if (column.isValueAt(ix)) {
        val v = column.valueAt(ix)
        if (p(v)) {
          b.appendValue(index.keyAt(i), v)
        }
      }
    }
    b.result()
  }


  /** Returns the first defined result of `f` when scanning the series in acending order.
    *
    * The parameter `f` is predicate on the key-values pairs of the
    * series; however, it also returns a value when satisfied, hence
    * the return type of `Option[B]`.

    * To ensure efficient access to the values of the series, the
    * predicate is supplied with the column and the index into the
    * column, rather than the cell. (Contrast `(K, Cell[V])` to
    * `(K, Column[V], Int)`).
    *
    * @example [[findFirstValue]] is defined as, {{{
    * findAsc((key, col, row) =>
    *   if (col.isValueAt(row))
    *     Some(key -> column.valueAt(row))
    *   else
    *     None
    * )
    * }}}
    *
    * @tparam  B  the return type of the predicate
    * @param  f  a predicate on key–value pairs in the series that returns a value when satisfied
    * @return the first defined result of `f` when scanning the series in acending order.
    * @see [[findDesc]]
    */
  def findAsc[B](f: (K, Column[V], Int) => Option[B]): Option[B] = {
    var i = 0
    while (i < index.size) {
      val row = index.indexAt(i)
      val res = f(index.keyAt(i), column, row)
      if (res.isDefined)
        return res
      i += 1
    }
    None
  }


  /** Returns the first key-value in the series.
    *
    * The returned key-value pair is the first in the series where
    * the value is both available and meaningful.
    *
    * @return the first key-value in the series.
    * @see [[findLastValue]]
    * @see [[findAsc]]
    */
  def findFirstValue: Option[(K, V)] =
    findAsc((key, col, row) =>
      if (col.isValueAt(row))
        Some(key -> col.valueAt(row))
      else
        None
    )


  /** Returns the first defined result of `f` when scanning the series in descending order.
    *
    * The parameter `f` is predicate on the key-values pairs of the
    * series; however, it also returns a value when satisfied, hence
    * the return type of `Option[B]`.

    * To ensure efficient access to the values of the series, the
    * predicate is supplied with the column and the index into the
    * column, rather than the cell. (Contrast `(K, Cell[V])` to
    * `(K, Column[V], Int)`).
    *
    * @example [[findLastValue]] is defined as, {{{
    * findDesc((key, col, row) =>
    *   if (col.isValueAt(row))
    *     Some(key -> col.valueAt(row))
    *   else
    *     None
    * )
    * }}}
    *
    * @tparam  B  the return type of the predicate
    * @param  f  a predicate on key–value pairs in the series that returns a value when satisfied
    * @return the first defined result of `f` when scanning the series in descending order.
    * @see [[findAsc]]
    */
  def findDesc[B](f: (K, Column[V], Int) => Option[B]): Option[B] = {
    var i = index.size - 1
    while (i >= 0) {
      val row = index.indexAt(i)
      val res = f(index.keyAt(i), column, row)
      if (res.isDefined)
        return res
      i -= 1
    }
    None
  }


  /** Returns the last key-value in the series.
    *
    * The returned key-value pair is the last in the series where
    * the value is both available and meaningful.
    *
    * @return the last key-value in the series.
    * @see [[findFirstValue]]
    * @see [[findDesc]]
    */
  def findLastValue: Option[(K, V)] =
    findDesc((key, col, row) =>
      if (col.isValueAt(row))
        Some(key -> col.valueAt(row))
      else
        None
    )


  /**
   * Returns a compacted version of this `Series`. The new series will be equal
   * to the old one, but the backing column will be dropped and replaced with a
   * version that only contains the values needed for this series. It will also
   * remove any indirection in the underlying column, such as that caused by
   * reindexing, shifting, mapping values, etc.
   */
  def compacted[V: ClassTag]: Series[K, V] = ???

  /**
   * Reduce all the values in this `Series` using the given reducer.
   */
  def reduce[W](reducer: Reducer[V, W]): Cell[W] = {
    val indices = new Array[Int](index.size)
    cfor(0)(_ < indices.length, _ + 1) { i =>
      indices(i) = index.indexAt(i)
    }
    reducer.reduce(column, indices, 0, index.size)
  }

  /** Returns the [[reduce.Count]] reduction of this series.
    * @return the [[reduce.Count]] reduction of this series.
    * @see [[reduce.Count]]
    */
  def count: Cell[Int] =
    this.reduce(framian.reduce.Count)

  /** Returns the [[reduce.First]] reduction of this series.
    * @return the [[reduce.First]] reduction of this series.
    * @see [[reduce.First]]
    * @see [[firstN]]
    * @see [[last]]
    */
  def first: Cell[V] =
    this.reduce(framian.reduce.First[V])

  /** Returns the [[reduce.FirstN]] reduction of this series.
    * @return the [[reduce.FirstN]] reduction of this series.
    * @see [[reduce.FirstN]]
    * @see [[first]]
    * @see [[lastN]]
    */
  def firstN(n: Int): Cell[List[V]] =
    this.reduce(framian.reduce.FirstN[V](n))

  /** Returns the [[reduce.Last]] reduction of this series.
    * @return the [[reduce.Last]] reduction of this series.
    * @see [[reduce.Last]]
    * @see [[lastN]]
    * @see [[first]]
    */
  def last: Cell[V] =
    this.reduce(framian.reduce.Last[V])

  /** Returns the [[reduce.LastN]] reduction of this series.
    * @return the [[reduce.LastN]] reduction of this series.
    * @see [[reduce.LastN]]
    * @see [[last]]
    * @see [[firstN]]
    */
  def lastN(n: Int): Cell[List[V]] =
    this.reduce(framian.reduce.LastN[V](n))

  /** Returns the [[reduce.Max]] reduction of this series.
    * @return the [[reduce.Max]] reduction of this series.
    * @see [[reduce.Max]]
    * @see [[min]]
    */
  def max(implicit ev0: Order[V]): Cell[V] =
    this.reduce(framian.reduce.Max[V])

  /** Returns the [[reduce.Min]] reduction of this series.
    * @return the [[reduce.Min]] reduction of this series.
    * @see [[reduce.Min]]
    * @see [[max]]
    */
  def min(implicit ev0: Order[V]): Cell[V] =
    this.reduce(framian.reduce.Min[V])

  /** Returns the `AdditiveMonoid` reduction of this series.
    * @return the `AdditiveMonoid` reduction of this series.
    * @see [[reduce.MonoidReducer]]
    * @see [[sumNonEmpty]]
    * @see [[product]]
    */
  def sum(implicit ev0: AdditiveMonoid[V]): Cell[V] =
    this.reduce(framian.reduce.MonoidReducer[V](ev0.additive))

  /** Returns the `AdditiveSemigroup` reduction of this series.
    * @return the `AdditiveSemigroup` reduction of this series.
    * @see [[reduce.SemigroupReducer]]
    * @see [[sum]]
    * @see [[productNonEmpty]]
    */
  def sumNonEmpty(implicit ev0: AdditiveSemigroup[V]): Cell[V] =
    this.reduce(framian.reduce.SemigroupReducer[V](ev0.additive))

  /** Returns the `MultiplicativeMonoid` reduction of this series.
    * @return the `MultiplicativeMonoid` reduction of this series.
    * @see [[reduce.MonoidReducer]]
    * @see [[productNonEmpty]]
    * @see [[sum]]
    */
  def product(implicit ev0: MultiplicativeMonoid[V]): Cell[V] =
    this.reduce(framian.reduce.MonoidReducer[V](ev0.multiplicative))

  /** Returns the `MultiplicativeSemigroup` reduction of this series.
    * @return the `MultiplicativeSemigroup` reduction of this series.
    * @see [[reduce.SemigroupReducer]]
    * @see [[product]]
    * @see [[sumNonEmpty]]
    */
  def productNonEmpty(implicit ev0: MultiplicativeSemigroup[V]): Cell[V] =
    this.reduce(framian.reduce.SemigroupReducer[V](ev0.multiplicative))

  /** Returns the [[reduce.Mean]] reduction of this series.
    * @return the [[reduce.Mean]] reduction of this series.
    * @see [[reduce.Mean]]
    * @see [[median]]
    */
  def mean(implicit ev0: Field[V]): Cell[V] =
    this.reduce(framian.reduce.Mean[V])

  /** Returns the [[reduce.Median]] reduction of this series.
    * @return the [[reduce.Median]] reduction of this series.
    * @see [[reduce.Median]]
    * @see [[mean]]
    */
  def median(implicit ev0: ClassTag[V], ev1: Field[V], ev2: Order[V]): Cell[V] =
    this.reduce(framian.reduce.Median[V])

  /** Returns the [[reduce.Unique]] reduction of this series.
    * @return the [[reduce.Unique]] reduction of this series.
    * @see [[reduce.Unique]]
    */
  def unique: Cell[Set[V]] =
    this.reduce(framian.reduce.Unique[V])

  /** Returns the [[reduce.Exists]] reduction of this series.
    * @return the [[reduce.Exists]] reduction of this series.
    * @see [[reduce.Exists]]
    * @see [[forall]]
    */
  def exists(p: V => Boolean): Boolean = {
    val cell = this.reduce(framian.reduce.Exists(p))
    assume(cell.isValue, "assumed that the Exists reducer always returns a value")
    cell.get
  }

  /** Returns the [[reduce.ForAll]] reduction of this series.
    * @return the [[reduce.ForAll]] reduction of this series.
    * @see [[reduce.ForAll]]
    * @see [[exists]]
    */
  def forall(p: V => Boolean): Boolean = {
    val cell = this.reduce(framian.reduce.ForAll(p))
    assume(cell.isValue, "assumed that the ForAll reducer always returns a value")
    cell.get
  }

  /**
   * For each unique key in this series, this reduces all the values for that
   * key and returns a series with only the unique keys and reduced values. The
   * new series will be in key order.
   */
  def reduceByKey[W](reducer: Reducer[V, W]): Series[K, W] = {
    val reduction = new Reduction[K, V, W](column, reducer)
    val (keys, values) = Index.group(index)(reduction).result()
    Series(Index.ordered(keys), Column.fromCells(values))
  }

  /** Returns the [[reduce.Count]] reduction of this series by key.
    * @return the [[reduce.Count]] reduction of this series by key.
    * @see [[reduce.Count]]
    */
  def countByKey: Series[K, Int] =
    this.reduceByKey(framian.reduce.Count)

  /** Returns the [[reduce.First]] reduction of this series by key.
    * @return the [[reduce.First]] reduction of this series by key.
    * @see [[reduce.First]]
    * @see [[firstN]]
    * @see [[last]]
    */
  def firstByKey: Series[K, V] =
    this.reduceByKey(framian.reduce.First[V])

  /** Returns the [[reduce.FirstN]] reduction of this series by key.
    * @return the [[reduce.FirstN]] reduction of this series by key.
    * @see [[reduce.FirstN]]
    * @see [[first]]
    * @see [[lastN]]
    */
  def firstNByKey(n: Int): Series[K, List[V]] =
    this.reduceByKey(framian.reduce.FirstN[V](n))

  /** Returns the [[reduce.Last]] reduction of this series by key.
    * @return the [[reduce.Last]] reduction of this series by key.
    * @see [[reduce.Last]]
    * @see [[lastN]]
    * @see [[first]]
    */
  def lastByKey: Series[K, V] =
    this.reduceByKey(framian.reduce.Last[V])

  /** Returns the [[reduce.LastN]] reduction of this series by key.
    * @return the [[reduce.LastN]] reduction of this series by key.
    * @see [[reduce.LastN]]
    * @see [[last]]
    * @see [[firstN]]
    */
  def lastNByKey(n: Int): Series[K, List[V]] =
    this.reduceByKey(framian.reduce.LastN[V](n))

  /** Returns the [[reduce.Max]] reduction of this series by key.
    * @return the [[reduce.Max]] reduction of this series by key.
    * @see [[reduce.Max]]
    * @see [[min]]
    */
  def maxByKey(implicit ev0: Order[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.Max[V])

  /** Returns the [[reduce.Min]] reduction of this series by key.
    * @return the [[reduce.Min]] reduction of this series by key.
    * @see [[reduce.Min]]
    * @see [[max]]
    */
  def minByKey(implicit ev0: Order[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.Min[V])

  /** Returns the `AdditiveMonoid` reduction of this series by key.
    * @return the `AdditiveMonoid` reduction of this series by key.
    * @see [[reduce.MonoidReducer]]
    * @see [[sumNonEmpty]]
    * @see [[product]]
    */
  def sumByKey(implicit ev0: AdditiveMonoid[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.MonoidReducer[V](ev0.additive))

  /** Returns the `AdditiveSemigroup` reduction of this series by key.
    * @return the `AdditiveSemigroup` reduction of this series by key.
    * @see [[reduce.SemigroupReducer]]
    * @see [[sum]]
    * @see [[productNonEmpty]]
    */
  def sumNonEmptyByKey(implicit ev0: AdditiveSemigroup[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.SemigroupReducer[V](ev0.additive))

  /** Returns the `MultiplicativeMonoid` reduction of this series by key.
    * @return the `MultiplicativeMonoid` reduction of this series by key.
    * @see [[reduce.MonoidReducer]]
    * @see [[productNonEmpty]]
    * @see [[sum]]
    */
  def productByKey(implicit ev0: MultiplicativeMonoid[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.MonoidReducer[V](ev0.multiplicative))

  /** Returns the `MultiplicativeSemigroup` reduction of this series by key.
    * @return the `MultiplicativeSemigroup` reduction of this series by key.
    * @see [[reduce.SemigroupReducer]]
    * @see [[product]]
    * @see [[sumNonEmpty]]
    */
  def productNonEmptyByKey(implicit ev0: MultiplicativeSemigroup[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.SemigroupReducer[V](ev0.multiplicative))

  /** Returns the [[reduce.Mean]] reduction of this series by key.
    * @return the [[reduce.Mean]] reduction of this series by key.
    * @see [[reduce.Mean]]
    * @see [[median]]
    */
  def meanByKey(implicit ev0: Field[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.Mean[V])

  /** Returns the [[reduce.Median]] reduction of this series by key.
    * @return the [[reduce.Median]] reduction of this series by key.
    * @see [[reduce.Median]]
    * @see [[mean]]
    */
  def medianByKey(implicit ev0: ClassTag[V], ev1: Field[V], ev2: Order[V]): Series[K, V] =
    this.reduceByKey(framian.reduce.Median[V])

  /** Returns the [[reduce.Unique]] reduction of this series by key.
    * @return the [[reduce.Unique]] reduction of this series by key.
    * @see [[reduce.Unique]]
    */
  def uniqueByKey: Series[K, Set[V]] =
    this.reduceByKey(framian.reduce.Unique[V])

  /** Returns the [[reduce.Exists]] reduction of this series by key.
    * @return the [[reduce.Exists]] reduction of this series by key.
    * @see [[reduce.Exists]]
    * @see [[forall]]
    */
  def existsByKey(p: V => Boolean): Series[K, Boolean] =
    this.reduceByKey(framian.reduce.Exists(p))

  /** Returns the [[reduce.ForAll]] reduction of this series by key.
    * @return the [[reduce.ForAll]] reduction of this series by key.
    * @see [[reduce.ForAll]]
    * @see [[exists]]
    */
  def forallByKey(p: V => Boolean): Series[K, Boolean] =
    this.reduceByKey(framian.reduce.ForAll(p))


  /**
   * Rolls values and `NM`s forward, over `NA`s. This is similar to
   * [[rollForwardUpTo]], but has no bounds checks. In fact, this is exactly
   * equivalent to
   * `series.rollForwardUpTo(1)(TrivialMetricSpace[K], Order[Int])`.
   */
  def rollForward: Series[K, V] =
    rollForwardUpTo[Int](1)(TrivialMetricSpace[K], Order[Int])

  /**
   * Roll-forward values and `NM`s over `NA`s. It will rolls values in sequence
   * order (not sorted order). It will only roll over `NA`s whose key is within
   * `delta` of the last valid value or `NM`. This bounds check is inclusive.
   *
   * An example of this behaviour is as follows:
   * {{{
   * Series(1 -> "a", 2 -> NA, 3 -> NA, 4 -> NM, 5 -> NA, 6 -> NA).rollForwardUpTo(1D) ===
   *     Series(1 -> "a", 2 -> "a", 3 -> NA, 4 -> NM, 5 -> NM, 6 -> NA)
   * }}}
   */
  def rollForwardUpTo[R](delta: R)(implicit K: MetricSpace[K, R], R: Order[R]): Series[K, V] = {
    val indices: Array[Int] = new Array[Int](index.size)

    @tailrec
    def loop(i: Int, lastValue: Int): Unit = if (i < index.size) {
      val row = index.indexAt(i)
      if (!column.isValueAt(row) && column.nonValueAt(row) == NA) {
        if (K.distance(index.keyAt(i), index.keyAt(lastValue)) <= delta) {
          indices(i) = index.indexAt(lastValue)
        } else {
          indices(i) = row
        }
        loop(i + 1, lastValue)
      } else {
        indices(i) = row
        loop(i + 1, i)
      }
    }

    loop(0, 0)

    Series(index, column.reindex(indices))
  }

  override def toString: String =
    (keys zip values).map { case (key, value) =>
      s"$key -> $value"
    }.mkString("Series(", ", ", ")")

  override def equals(that0: Any): Boolean = that0 match {
    case (that: Series[_, _]) if this.index.size == that.index.size =>
      (this.index.iterator zip that.index.iterator) forall {
        case ((key0, idx0), (key1, idx1)) if key0 == key1 =>
          this.column(idx0) == that.column(idx1)
        case _ => false
      }
    case _ => false
  }

  override def hashCode: Int =
    index.map { case (k, i) => (k, column(i)) }.hashCode
}

object Series {
  import spire.std.int._
  def empty[K: Order: ClassTag, V] = Series(Index.empty[K], Column.empty[V])

  def apply[K, V](index: Index[K], column: Column[V]): Series[K, V] =
    new Series(index, column)

  def apply[K: Order: ClassTag, V: ClassTag](kvs: (K, V)*): Series[K, V] = {
    val (keys, values) = kvs.unzip
    Series(Index(keys.toArray), Column.fromArray(values.toArray))
  }

  def apply[V: ClassTag](values: V*): Series[Int, V] = {
    val keys = Array(0 to (values.length - 1): _*)
    Series(Index(keys), Column.fromArray(values.toArray))
  }

  def fromCells[K: Order: ClassTag, V: ClassTag](col: TraversableOnce[(K, Cell[V])]): Series[K, V] = {
    val bldr = Series.newUnorderedBuilder[K ,V]
    bldr ++= col
    bldr.result()
  }

  def fromCells[K: Order: ClassTag, V: ClassTag](kvs: (K, Cell[V])*): Series[K, V] =
    fromCells(kvs)

  def fromMap[K: Order: ClassTag, V: ClassTag](kvMap: Map[K, V]): Series[K, V] =
    Series(Index(kvMap.keys.toArray), Column.fromArray(kvMap.values.toArray))

  implicit def cbf[K: Order: ClassTag, V : ClassTag]: CanBuildFrom[Series[_, _], (K, Cell[V]), Series[K, V]] =
    new CanBuildFrom[Series[_, _], (K, Cell[V]), Series[K, V]] {
      def apply(): Builder[(K, Cell[V]), Series[K, V]] = Series.newUnorderedBuilder[K ,V]
      def apply(from: Series[_, _]): Builder[(K, Cell[V]), Series[K, V]] = apply()
    }

  private def newBuilder[K : ClassTag : Order, V : ClassTag](isOrdered: Boolean): AbstractSeriesBuilder[K, V] =
    if (isOrdered) newOrderedBuilder else newUnorderedBuilder

  private def newUnorderedBuilder[K : ClassTag : Order, V : ClassTag]: AbstractSeriesBuilder[K, V] =
    new AbstractSeriesBuilder[K, V] {
      def result(): Series[K, V] =
        Series(
          Index(this.keyBldr.result()),
          this.colBldr.result())
    }

  private def newOrderedBuilder[K : ClassTag : Order, V : ClassTag]: AbstractSeriesBuilder[K, V] =
    new AbstractSeriesBuilder[K, V] {
      def result(): Series[K, V] =
        Series(
          Index.ordered(this.keyBldr.result()),
          this.colBldr.result())
    }
}


private abstract class AbstractSeriesBuilder[K : ClassTag : Order, V : ClassTag] extends Builder[(K, Cell[V]), Series[K, V]] {
  protected val keyBldr = Array.newBuilder[K]
  protected val colBldr = new ColumnBuilder[V]

  def +=(elem: (K, Cell[V])): this.type = {
    keyBldr += elem._1
    colBldr += elem._2
    this
  }

  def append(k: K, c: Cell[V]): this.type = {
    keyBldr += k
    colBldr += c
    this
  }

  def appendValue(k: K, v: V): this.type = {
    keyBldr += k
    colBldr.addValue(v)
    this
  }

  def appendNonValue(k: K, nonValue: NonValue): this.type = {
    keyBldr += k
    colBldr.addNonValue(nonValue)
    this
  }

  def clear(): Unit = {
    keyBldr.clear()
    colBldr.clear()
  }

  override def sizeHint(size: Int): Unit = {
    keyBldr.sizeHint(size)
    colBldr.sizeHint(size)
  }
}
