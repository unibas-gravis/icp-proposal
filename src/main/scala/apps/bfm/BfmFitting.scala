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
import java.io.File

import api.other.{ModelAndTargetSampling, ModelSampling, RegistrationComparison}
import api.sampling.{MixedProposalDistributions, ModelFittingParameters, ProductEvaluators, SamplingRegistration}
import api.sampling.evaluators.{ModelToTargetEvaluation, SymmetricEvaluation}
import scalismo.io.StatismoIO
import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless}
import Paths.generalPath

import scala.collection.parallel.ForkJoinTaskSupport

object BfmFitting {

  def main(args: Array[String]) {
    scalismo.initialize()

    val alignedPath = new File(generalPath, "aligned")
    val alignedMeshesPath = new File(alignedPath, "meshes")
    val fileList = alignedMeshesPath.listFiles().filter(f => f.getName.endsWith(".stl")).sorted.toIndexedSeq
    fileList.foreach(println(_))

    fileList.zipWithIndex.foreach{case (_, faceIndex) =>

      Thread.sleep(1000*faceIndex)
      println(s"FACE INDEX: ${faceIndex}")

      // load the data
      val (modelInit, _, targetGroundTruth, targetMeshPartialInit, targetLogFile) = LoadTestData.modelAndTarget(faceIndex)

//      val targetMeshPartial = targetMeshPartialInit
      val targetMeshPartial = targetMeshPartialInit.operations.decimate(1000) //      For speed up - decimate the target mesh

      val model = modelInit
//      val model = modelInit.decimate(1000) //      For speed up - decimate the model

      println(s"Number of vertices in model: ${model.mean.pointSet.numberOfPoints}")

      // visualization setup
      val ui = ScalismoUI(s"BFM-icp-fitting ${faceIndex}")
//      val ui = ScalismoUIHeadless()

      val modelGroup = ui.createGroup("modelGroup")
      val targetGroup = ui.createGroup("targetGroup")
      val finalGroup = ui.createGroup("finalGroup")
      val showModel = ui.show(modelGroup, model, "model")
      val showGt = ui.show(targetGroup, targetGroundTruth, "Ground-truth")
      showGt.opacity = 0.0
      val showTarget = ui.show(targetGroup, targetMeshPartial, "target")
      showTarget.color = Color.YELLOW

      // proposal
      val numOfICPointSamples = model.rank*2
      val proposalIcpInit = MixedProposalDistributions.mixedProposalICP(
        model,
        targetMeshPartial,
        numOfICPointSamples,
        projectionDirection = ModelAndTargetSampling,
        tangentialNoise = 10.0,
        noiseAlongNormal = 2.0,
        stepLength = 0.1
      )

      val proposalIcpNext = MixedProposalDistributions.mixedProposalICP(
        model,
        targetMeshPartial,
        numOfICPointSamples,
        projectionDirection = ModelAndTargetSampling,
        tangentialNoise = 4.0,
        noiseAlongNormal = 2.0,
        stepLength = 0.5
      )
      // evaluator
      val numberOfEvaluationPoints = numOfICPointSamples*2
      val avgUncertainty = 0.2
      val maxUncertainty = 10.0
      val evaluator = ProductEvaluators.proximityAndCollectiveHausdorffBoundaryAware(
        model,
        targetMeshPartial,
        uncertaintyAvg = avgUncertainty,
        uncertaintyMax = maxUncertainty,
        numberOfEvaluationPoints = numberOfEvaluationPoints,
        evaluationMode = ModelToTargetEvaluation
      )

      // run the registration
      val numOfSamples = 10000
      val samplingRegistration = new SamplingRegistration(
        model,
        targetMeshPartial,
        Option(showModel),
        modelUiUpdateInterval = 10,
        acceptInfoPrintInterval = 100
      )
      val bestRegistrationParsInit = samplingRegistration.runfitting(
        evaluator,
        proposalIcpInit,
        100,
        jsonName = targetLogFile
      )

      // visualize result
      val bestRegistration = ModelFittingParameters.transformedMesh(model, bestRegistrationParsInit)
      ui.show(finalGroup, bestRegistration, "best-init-fit")

      val bestRegistrationParsNext = samplingRegistration.runfitting(
        evaluator,
        proposalIcpNext,
        numOfSamples,
        jsonName = targetLogFile,
        initialModelParameters = Option(bestRegistrationParsInit)
      )

      // visualize result
      val bestRegistrationFinal = ModelFittingParameters.transformedMesh(model, bestRegistrationParsNext)
      ui.show(finalGroup, bestRegistrationFinal, "best-init-fit")

      // evaluation
      RegistrationComparison.evaluateReconstruction2GroundTruthBoundaryAware("SAMPLE", bestRegistration, targetMeshPartial)
    }
  }
}
