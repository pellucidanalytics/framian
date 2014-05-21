package pellucid.pframe
package reduce

/**
 * A low level trait for implementing reductions.
 *
 * TODO: All reducers should return Cell[B].
 */
trait Reducer[-A, +B] {
  def reduce(column: Column[A], indices: Array[Int], start: Int, end: Int): Cell[B]
}
