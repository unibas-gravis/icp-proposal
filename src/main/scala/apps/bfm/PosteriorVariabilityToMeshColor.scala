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

package apps.bfm

import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import apps.util.{LogHelper, PosteriorVariability}
import scalismo.ui.api.ScalismoUI

object PosteriorVariabilityToMeshColor {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()


    val (model, targetName, _, _, targetLogFile) = LoadTestData.modelAndTarget(0)

    val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](targetLogFile)
    val logInit: IndexedSeq[jsonLogFormat] = logObj.loadLog()
    val burnInPhase = 200

    val logSamples = LogHelper.samplesFromLog(logInit, takeEveryN = 50, total = 100000, burnInPhase)
    println(s"Number of samples from log: ${logSamples.length}/${logInit.length - burnInPhase}")
    val logShapes = LogHelper.logSamples2shapes(model, logSamples.map(_._1))

    val best = ModelFittingParameters.transformedMesh(model, logObj.getBestFittingParsFromJSON)

    val colorMap_normalVariance = PosteriorVariability.computeDistanceMapFromMeshesNormal(logShapes, best, sumNormals = true)
    val colorMap_posteriorEstimate = PosteriorVariability.computeDistanceMapFromMeshesTotal(logShapes, best)

    val ui = ScalismoUI(s"Posterior visualization - $targetName")
    val modelGroup = ui.createGroup("model")
    val colorGroup = ui.createGroup("color")
    val showModel = ui.show(modelGroup, model, "model")
    showModel.meshView.opacity = 0.0
    ui.show(colorGroup, colorMap_posteriorEstimate, "posterior")
    ui.show(colorGroup, colorMap_normalVariance, "normal")
    ui.show(colorGroup, best, "best-fit")
    val rndGroup = ui.createGroup("random")
    scala.util.Random.shuffle(logShapes).take(10).zipWithIndex.foreach(m => ui.show(rndGroup, m._1, m._2.toString))
  }
}
