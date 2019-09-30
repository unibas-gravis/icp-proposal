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

import java.io.File

import api.other.ModelSampling
import api.sampling._
import api.sampling.evaluators.SymmetricEvaluation
import apps.femur.Paths.generalPath
import apps.util.FileUtils
import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.geometry._
import scalismo.io.{MeshIO, StatismoIO}
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.utils.Random.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


object RunMHRandomInitComparison {

  def InitialiseShapeParameters(rank: Int, index: Int, variance: Double = 0.1): ShapeParameters = {
    val perturbationDistribution = new MultivariateNormalDistribution(DenseVector.zeros(rank), DenseMatrix.eye[Double](rank) * variance)
    if (index == 0) {
      ShapeParameters(DenseVector.zeros[Double](rank))
    }
    else {
      ShapeParameters(perturbationDistribution.sample())
    }
  }

  def main(args: Array[String]) {
    scalismo.initialize()
    println(s"Starting Metropolis Hastings registrations with random initialization of shape parameters!")

    val logPath = new File(generalPath, "log")

    val modelFile = new File(generalPath, "femur_gp_model_50-components.h5")
    val model = StatismoIO.readStatismoMeshModel(modelFile).get
    println(s"Model file to be used: $modelFile")

    // Note that the test femurs are already aligned to the model reference after running "AlignShapes"
    val subPath = "aligned"
    val targetMeshes = new File(generalPath, s"$subPath/meshes/").listFiles()
    // Choose which target index to run fitting on
    val targetIndex = 0
    val targetMeshFile = targetMeshes(targetIndex)
    val targetMesh = MeshIO.readMesh(targetMeshFile).get

    val numOfEvaluatorPoints = model.referenceMesh.pointSet.numberOfPoints // Used for the likelihood evaluator
    val numOfICPPointSamples = numOfEvaluatorPoints // Used for the ICP proposal
    val numOfICPSamples = 10000 // Length of Markov Chain
    val numOfRNDSamples = numOfICPSamples * 5 // Length of Markov Chain

    val proposalIcp = MixedProposalDistributions.mixedProposalICP(model, targetMesh, numOfICPPointSamples, projectionDirection = ModelSampling)
    val proposalRnd = MixedProposalDistributions.mixedProposalRandom(model)
    val evaluator = ProductEvaluators.proximityAndIndependent(model, targetMesh, SymmetricEvaluation, uncertainty = 1.0, numberOfEvaluationPoints = numOfEvaluatorPoints)

    val modelName = FileUtils.basename(modelFile)
    val targetName = FileUtils.basename(targetMeshFile)

    (0 until 5).par.foreach { case i =>
      println(s"Starting fitting with random initialization of shape parameters - index: $i")

      val rotatCenter: EuclideanVector[_3D] = model.referenceMesh.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / model.referenceMesh.pointSet.numberOfPoints.toDouble
      val initPoseParameters = PoseParameters(EuclideanVector3D(0, 0, 0), (0, 0, 0), rotationCenter = rotatCenter.toPoint)
      val initShapeParameters = InitialiseShapeParameters(model.rank, i)

      val initialParametersRandom = ModelFittingParameters(initPoseParameters, initShapeParameters)

      val jsonNameICP = new File(logPath, s"RNDvsICP_ICP_random_init_$modelName-$targetName-samples-$numOfICPSamples-$i-index.json")
      val jsonNameRND = new File(logPath, s"RNDvsICP_RND_random_init_$modelName-$targetName-samples-$numOfRNDSamples-$i-index.json")
      Await.result(
        Future.sequence(Seq(
          Future {
            IcpProposalRegistration.fitting(model, targetMesh, evaluator, proposalIcp, numOfICPSamples, None, jsonNameICP, Option(initialParametersRandom))
          },
          Future {
            IcpProposalRegistration.fitting(model, targetMesh, evaluator, proposalRnd, numOfRNDSamples, None, jsonNameRND, Option(initialParametersRandom))
          }
        )), Duration.Inf)
    }
  }
}
