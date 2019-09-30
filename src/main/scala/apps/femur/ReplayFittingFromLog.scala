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

package apps.femur

import java.awt.Color
import java.io.File
import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random
import apps.femur.Paths.dataFemurPath


object ReplayFittingFromLog {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val logPath = new File(dataFemurPath, "log")

    val (model, _, targetMesh, _) = LoadTestData.modelAndTarget()

    val jsonFileName = "icpProposalRegistration.json"

    println(new File(logPath, jsonFileName).toString)

    val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](new File(logPath, jsonFileName))
    val logInit: IndexedSeq[jsonLogFormat] = logObj.loadLog()

    val ui = ScalismoUI(jsonFileName)
    val targetGroup = ui.createGroup("target")
    val modelGroup = ui.createGroup("model")

    ui.show(targetGroup, targetMesh, "target").color = Color.YELLOW

    val modelShow = ui.show(modelGroup, model, "model")

    def getLogIndex(i: Int): Int = {
      if (logInit(i).status) i
      else getLogIndex(i - 1)
    }

    val firstIndexNotReject = logInit.filter(f => f.status).head.index

    val takeEveryN = 10

    println(s"takeEvery: $takeEveryN total log : " + logInit.length)
    Thread.sleep(3000)
    for (cnt <- firstIndexNotReject until logInit.length by takeEveryN) yield {
      val index = getLogIndex(cnt)
      println(s"Index from Markov-Chain: $cnt, Index closest accepted sample: $index")
      val js = logInit(index)
      val pars = logObj.sampleToModelParameters(js)
      val rigidTrans = ModelFittingParameters.poseTransform(pars)
      modelShow.shapeModelTransformationView.poseTransformationView.transformation = rigidTrans
      modelShow.shapeModelTransformationView.shapeTransformationView.coefficients = pars.shapeParameters.parameters
      Thread.sleep(100)
    }
  }
}
