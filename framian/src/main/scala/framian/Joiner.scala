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

import scala.reflect.ClassTag
import scala.collection.mutable.{ ArrayBuilder, Builder }

import spire.syntax.cfor._

sealed trait Join {
  val leftOuter: Boolean
  val rightOuter: Boolean
}

sealed trait LeftBiasedJoin extends Join {
  override val rightOuter: Boolean = false
}

sealed trait RightBiasedJoin extends Join {
  override val leftOuter: Boolean = false
}

object Join {
  case object Inner extends LeftBiasedJoin with RightBiasedJoin
  case object Left extends LeftBiasedJoin { override val leftOuter = true }
  case object Right extends RightBiasedJoin { override val rightOuter = true }
  case object Outer extends Join { override val leftOuter = true; override val rightOuter = true }
}

/**
 * This implements a [[Index.Cogrouper]] that is suitable for generating the indices
 * necessary for joins on [[Series]] and [[Frame]].
 */
final case class Joiner[K: ClassTag](join: Join) extends Index.GenericJoin[K] {
  import Index.GenericJoin.Skip

  def cogroup(state: State)(
      lKeys: Array[K], lIdx: Array[Int], lStart: Int, lEnd: Int,
      rKeys: Array[K], rIdx: Array[Int], rStart: Int, rEnd: Int): State = {

    if (lEnd > lStart && rEnd > rStart) {
      val key = lKeys(lStart)
      cfor(lStart)(_ < lEnd, _ + 1) { i =>
        val li = lIdx(i)
        cfor(rStart)(_ < rEnd, _ + 1) { j =>
          state.add(key, li, rIdx(j))
        }
      }
    } else if (lEnd > lStart && join.leftOuter) {
      val key = lKeys(lStart)
      cfor(lStart)(_ < lEnd, _ + 1) { i =>
        state.add(key, lIdx(i), Skip)
      }
    } else if (rEnd > rStart && join.rightOuter) {
      val key = rKeys(rStart)
      cfor(rStart)(_ < rEnd, _ + 1) { i =>
        state.add(key, Skip, rIdx(i))
      }
    }

    state
  }
}
