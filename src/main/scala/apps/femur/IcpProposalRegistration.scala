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
import api.sampling.evaluators.ModelToTargetEvaluation
import apps.femur.Paths.dataFemurPath
import scalismo.geometry._3D
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.ui.api.{ScalismoUI, StatisticalMeshModelViewControls}
import scalismo.utils.Random.implicits.randomGenerator


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

    val numOfEvaluatorPoints = model.rank * 4 // Used for the likelihood evaluator
    val numOfICPPointSamples = model.rank * 2 // Used for the ICP proposal
    val numOfSamples = 10000 // Length of Markov Chain

    /** *** ***** ***** ***** ***** *****
      * Closest Point proposal configuration
      * projectionDirection:
      *  - TargetSampling (if registering partial meshes)
      *  - ModelSampling (if registering noisy meshes)
      *  - ModelAndTargetSampling (if registering clean complete meshes)
      *    **** ***** ***** ***** ***** **** */
    val proposalICP = MixedProposalDistributions.mixedProposalICP(model, targetMesh, numOfICPPointSamples, projectionDirection = ModelAndTargetSampling, tangentialNoise = 10.0, noiseAlongNormal = 5.0, stepLength = 0.1)
    val proposalRND = MixedProposalDistributions.mixedRandomShapeProposal(model)
    val proposal = MixtureProposal.fromProposalsWithTransition(Seq((0.90, proposalICP), (0.10, proposalRND)): _ *)

    /* Uncomment below to use the standard "Random walk proposal" proposal */
    //    val proposal = MixedProposalDistributions.mixedProposalRandom(model)

    /***** ***** ***** ***** ***** *****
    * Choosing the likelihood function
    * - euclideanEvaluator (gaussian distribution): gives best L2 distance restults
    * - hausdorffEvaluator (exponential distribution): gives best hausdorff result evaluationMode:
    * - ModelToTargetEvaluation (if registering noisy meshes)
    * - TargetToModelEvaluation (if registering partial meshes)
    * - SymmetricEvaluation (if registering clean complete meshes)
    ***** ***** ***** ***** ***** **** */
    val evaluator = ProductEvaluators.proximityAndIndependent(model, targetMesh, evaluationMode = ModelToTargetEvaluation, uncertainty = 2.0, numberOfEvaluationPoints = numOfEvaluatorPoints)
    /* Uncomment below to use the hausdorff likelihood function */
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

    val bestRegistration = fitting(model, targetMesh, evaluator, proposal, numOfSamples, Option(showModel), new File(logPath, s"icpProposalRegistration.json"))
    ui.show(finalGroup, bestRegistration, "best-fit")
    RegistrationComparison.evaluateReconstruction2GroundTruth("SAMPLE", bestRegistration, targetMesh)
  }
}
