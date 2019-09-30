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

import api.other.{ModelSampling, RegistrationComparison}
import api.sampling.{MixedProposalDistributions, ModelFittingParameters, ProductEvaluators, SamplingRegistration}
import api.sampling.evaluators.ModelToTargetEvaluation
import scalismo.ui.api.ScalismoUI

object BfmFitting {

  def main(args: Array[String]) {
    scalismo.initialize()


    // load the data
    val (model, _, targetGroundTruth, targetMeshPartial, targetLogFile) = LoadTestData.modelAndTarget()


    // visualization setup
    val ui = ScalismoUI("BFM-icp-fitting")
    val modelGroup = ui.createGroup("modelGroup")
    val targetGroup = ui.createGroup("targetGroup")
    val finalGroup = ui.createGroup("finalGroup")
    val showModel = ui.show(modelGroup, model, "model")
    val showGt = ui.show(targetGroup, targetGroundTruth, "Ground-truth")
    showGt.opacity = 0.0
    val showTarget = ui.show(targetGroup, targetMeshPartial, "target")
    showTarget.color = Color.YELLOW


    // proposal
    val numOfICPointSamples = model.referenceMesh.pointSet.numberOfPoints / 10
    val proposalIcp = MixedProposalDistributions.mixedProposalICP(
      model,
      targetMeshPartial,
      numOfICPointSamples,
      projectionDirection = ModelSampling
    )


    // evaluator
    val numberOfEvaluationPoints = numOfICPointSamples
    val avgUncertainty = 0.1
    val maxUncertainty = 5.0
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
    val bestRegistrationPars = samplingRegistration.runfitting(
      evaluator,
      proposalIcp,
      numOfSamples,
      jsonName = targetLogFile
    )


    // visualize result
    val bestRegistration = ModelFittingParameters.transformedMesh(model, bestRegistrationPars)
    ui.show(finalGroup, bestRegistration, "best-fit")


    // evaluation
    RegistrationComparison.evaluateReconstruction2GroundTruth("SAMPLE", bestRegistration, targetMeshPartial)

  }
}
