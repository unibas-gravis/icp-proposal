/*
 *  Copyright University of Basel, Graphics and Vision Research Group
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package apps.util

import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.StatisticalMeshModel

object LogHelper {

  def samplesFromLog(log: IndexedSeq[jsonLogFormat], takeEveryN: Int = 50, total: Int = 100, burnIn: Int = 0): IndexedSeq[(jsonLogFormat, Int)] = {
    def getLogIndex(i: Int): Int = {
      if (log(i).status) i
      else getLogIndex(i - 1)
    }

    println("Log length: " + log.length)
    val indexes = (burnIn until math.min(log.length, total) by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i), i))
    filtered.take(math.min(total, filtered.length))
  }

  def logSamples2shapes(model: StatisticalMeshModel, log: IndexedSeq[jsonLogFormat]): IndexedSeq[TriangleMesh[_3D]] = {
    log.map { l => ModelFittingParameters.transformedMesh(model, JSONAcceptRejectLogger.sampleToModelParameters(l)) }
  }
}
