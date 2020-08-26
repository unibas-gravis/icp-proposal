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

import java.awt.Color

import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random


object ReplayFittingFromLog {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val (model, targetName, gt, targetMesh, targetLogFile) = LoadTestData.modelAndTarget(targetIndex = 1)

    val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](targetLogFile)
    val logInit: IndexedSeq[jsonLogFormat] = logObj.loadLog()

    val ui = ScalismoUI(targetName)
    val targetGroup = ui.createGroup("target")
    val modelGroup = ui.createGroup("model")

    ui.show(targetGroup, targetMesh, "target").color = Color.YELLOW
    ui.show(targetGroup, gt, "gt").color = Color.ORANGE

    val bestPars: ModelFittingParameters = logObj.getBestFittingParsFromJSON
    val best: TriangleMesh[_3D] = ModelFittingParameters.transformedMesh(model, bestPars)

    val modelShow = ui.show(modelGroup, model, "model")

    def getLogIndex(i: Int): Int = {
      if (logInit(i).status) i
      else getLogIndex(i - 1)
    }

    val firstIndexNotReject = math.max(0, logInit.filter(f => f.status).head.index)

    val takeEveryN = 30

    ui.show(ui.createGroup("best"), best, "best")

    println(s"takeEvery: $takeEveryN total log : " + logInit.length)
    Thread.sleep(3000)
    val sampleGroup = ui.createGroup("samples")
    for (cnt <- firstIndexNotReject until 700 by takeEveryN) yield {
      val index = getLogIndex(cnt)
      println(s"Index from Markov-Chain: $cnt, Index closest accepted sample: $index")
      val js = logInit(index)
      val pars = logObj.sampleToModelParameters(js)
      val rigidTrans = ModelFittingParameters.poseTransform(pars)
      modelShow.shapeModelTransformationView.poseTransformationView.transformation = rigidTrans
      modelShow.shapeModelTransformationView.shapeTransformationView.coefficients = pars.shapeParameters.parameters
      val sample = model.instance(pars.shapeParameters.parameters).transform(rigidTrans)
      ui.show(sampleGroup, sample, cnt.toString)
      Thread.sleep(100)
    }
  }
}
