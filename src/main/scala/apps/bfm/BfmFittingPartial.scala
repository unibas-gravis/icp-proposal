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
import scalismo.ui.api.ScalismoUI
import Paths.generalPath
import scalismo.sampling.proposals.MixtureProposal
import scalismo.utils.Random.implicits.randomGenerator

object BfmFittingPartial {

  def main(args: Array[String]) {
    scalismo.initialize()

    val alignedPath = new File(generalPath, "aligned")
    val alignedMeshesPath = new File(alignedPath, "meshes")
    val fileList = alignedMeshesPath.listFiles().filter(f => f.getName.endsWith(".stl")).sorted.toIndexedSeq
    fileList.foreach(println(_))

    fileList.zipWithIndex.foreach{case (_, faceIndex) =>
      println(s"FACE INDEX: ${faceIndex}")

      // load the data
      val (modelInit, _, targetGroundTruth, targetMeshPartialInit, targetLogFile) = LoadTestData.modelAndTarget(faceIndex)

      val targetMeshPartial = targetMeshPartialInit.operations.decimate(500) //      For speed up - decimate the target mesh

      val model = modelInit.decimate(500)

      println(s"Number of vertices in model: ${model.mean.pointSet.numberOfPoints}")

      // visualization setup
      val ui = ScalismoUI(s"BFM-icp-fitting ${faceIndex}")
//      val ui = ScalismoUIHeadless()

      val modelGroup = ui.createGroup("modelGroup")
      val targetGroup = ui.createGroup("targetGroup")
      val finalGroup = ui.createGroup("finalGroup")
      val showModel = ui.show(modelGroup, modelInit, "model")
      val showGt = ui.show(targetGroup, targetGroundTruth, "Ground-truth")
      showGt.opacity = 0.0
      val showTarget = ui.show(targetGroup, targetMeshPartialInit, "target")
      showTarget.color = Color.YELLOW

      // proposal
      val numOfICPPointSamples = model.rank * 2
      val proposalICP = MixedProposalDistributions.mixedProposalICP(model, targetMeshPartial, numOfICPPointSamples, projectionDirection = ModelSampling, tangentialNoise = 6.0, noiseAlongNormal = 3.0, stepLength = 0.1)
      val proposalRandomPose = MixedProposalDistributions.mixedRandomPoseProposal()
      val proposalRandomShape = MixedProposalDistributions.mixedRandomShapeProposal(model)

      val proposal = MixtureProposal.fromProposalsWithTransition(Seq((0.4, proposalRandomPose), (0.55, proposalICP), (0.05, proposalRandomShape)): _ *)


      // evaluator
      val numberOfEvaluationPoints = numOfICPPointSamples * 2
      val avgUncertainty = 0.3
      val maxUncertainty = 1.0
      // evaluator
      val numOfEvaluatorPoints = numOfICPPointSamples*2

      val evaluator = ProductEvaluators.proximityAndCollectiveHausdorffBoundaryAware(model, targetMeshPartial, evaluationMode = SymmetricEvaluation, uncertaintyAvg = avgUncertainty, numberOfEvaluationPoints = numOfEvaluatorPoints, uncertaintyMax = maxUncertainty, mean = 0.1)

      // run the registration
      val numOfSamples = 10000
      val samplingRegistration = new SamplingRegistration(
        model,
        targetMeshPartial,
        Option(showModel),
        modelUiUpdateInterval = 10,
        acceptInfoPrintInterval = 100
      )
      val bestRegistrationPars = samplingRegistration.runfitting(
        evaluator,
        proposal,
        numOfSamples,
        jsonName = targetLogFile
      )

      // visualize result
      val bestRegistration = ModelFittingParameters.transformedMesh(model, bestRegistrationPars)
      ui.show(finalGroup, bestRegistration, "best-init-fit")

      // evaluation
      RegistrationComparison.evaluateReconstruction2GroundTruthBoundaryAware("SAMPLE", bestRegistration, targetMeshPartial)
    }
  }
}
