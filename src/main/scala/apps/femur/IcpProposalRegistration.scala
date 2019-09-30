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

import api.other.{ModelAndTargetSampling, RegistrationComparison}
import api.sampling._
import api.sampling.evaluators.SymmetricEvaluation
import apps.femur.Paths.dataFemurPath
import scalismo.geometry._3D
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.ui.api.{ScalismoUI, StatisticalMeshModelViewControls}

object IcpProposalRegistration {

  def fitting(model: StatisticalMeshModel, targetMesh: TriangleMesh3D, evaluator: Map[String, DistributionEvaluator[ModelFittingParameters]], proposal: ProposalGeneratorWithTransition[ModelFittingParameters], numOfIterations: Int, showModel: Option[StatisticalMeshModelViewControls], log: File, initialParameters: Option[ModelFittingParameters] = None): TriangleMesh[_3D] = {

    val samplingRegistration = new SamplingRegistration(model, targetMesh, showModel, modelUiUpdateInterval = 10, acceptInfoPrintInterval = 100)
    val t0 = System.currentTimeMillis()

    val best = samplingRegistration.runfitting(evaluator, proposal, numOfIterations, initialModelParameters = initialParameters, jsonName = log)

    val t1 = System.currentTimeMillis()
    println(s"ICP-Timing: ${(t1 - t0) / 1000.0} sec")
    ModelFittingParameters.transformedMesh(model, best)
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    println(s"Starting Metropolis Hastings registrations with ICP-proposal!")

    val logPath = new File(dataFemurPath, "log")

    val (model, modelLms, targetMesh, targetLms) = LoadTestData.modelAndTarget()

    val numOfEvaluatorPoints = model.referenceMesh.pointSet.numberOfPoints // Used for the likelihood evaluator
    val numOfICPPointSamples = numOfEvaluatorPoints // Used for the ICP proposal
    val numOfSamples = 1000 // Length of Markov Chain

    val proposalIcp = MixedProposalDistributions.mixedProposalICP(model, targetMesh, numOfICPPointSamples, projectionDirection = ModelAndTargetSampling)

    // Euclidean likelihood evaluator using a Gaussian distribution
    val evaluator = ProductEvaluators.proximityAndIndependent(model, targetMesh, SymmetricEvaluation, uncertainty = 1.0, numberOfEvaluationPoints = numOfEvaluatorPoints)
    // Hausdorff likelihood evaluator using an Exponential distribution
    //    val evaluator = ProductEvaluators.proximityAndHausdorff(model, targetMesh, uncertainty = 100.0)

    val ui = ScalismoUI(s"MH-ICP-proposal-registration")
    val modelGroup = ui.createGroup("modelGroup")
    val targetGroup = ui.createGroup("targetGroup")
    val finalGroup = ui.createGroup("finalGroup")

    val showModel = ui.show(modelGroup, model, "model")
    ui.show(modelGroup, modelLms, "landmarks")
    val showTarget = ui.show(targetGroup, targetMesh, "target")
    ui.show(targetGroup, targetLms, "landmarks")
    showTarget.color = Color.YELLOW


    val bestRegistration = fitting(model, targetMesh, evaluator, proposalIcp, numOfSamples, Option(showModel), new File(logPath, s"icpProposalRegistration.json"))
    ui.show(finalGroup, bestRegistration, "best-fit")
    RegistrationComparison.evaluateReconstruction2GroundTruth("SAMPLE", bestRegistration, targetMesh)
  }
}
