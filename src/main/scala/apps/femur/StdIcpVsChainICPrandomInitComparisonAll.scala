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

import api.other.ModelAndTargetSampling
import api.sampling._
import api.sampling.evaluators.ModelToTargetEvaluation
import api.sampling.loggers.JSONExperimentLogger
import apps.femur.Paths.generalPath
import apps.util.FileUtils
import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.geometry._
import scalismo.io.{MeshIO, StatismoIO}
import scalismo.mesh.{MeshMetrics, TriangleMesh, TriangleMesh3D}
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}
import scalismo.ui.api.ScalismoUIHeadless
import scalismo.utils.Random.implicits._

import scala.collection.parallel.ForkJoinTaskSupport

object StdIcpVsChainICPrandomInitComparisonAll {

  def appendExperiment(model: StatisticalMeshModel, log: JSONExperimentLogger, index: Int, target: TriangleMesh3D, bestSampleEuclidean: TriangleMesh3D, bestSampleHausdorff: TriangleMesh3D, bestIcp: TriangleMesh3D, targetPath: String, samplingEuclideanLoggerPath: String, samplingHausdorffLoggerPath: String, coeffInit: DenseVector[Double], numOfEvaluationPoints: Int, numOfSamplePoints: Int, normalNoise: Double, comment: String): Unit = {
    def distMeasure(best: TriangleMesh3D) = {
      val avgDist = MeshMetrics.avgDistance(best, target)
      val hausDist = MeshMetrics.hausdorffDistance(best, target)
      val diceCoeff = MeshMetrics.diceCoefficient(best, target)
      Map("avg" -> avgDist, "hausdorff" -> hausDist, "dice" -> diceCoeff)
    }

    val samplingEuclidean = distMeasure(bestSampleEuclidean)
    val samplingHausdorff = distMeasure(bestSampleHausdorff)
    val icp = distMeasure(bestIcp)
    val bestSampleCoeffEuclidean = model.coefficients(bestSampleEuclidean).toArray.toSeq
    val bestSampleCoeffHausdorff = model.coefficients(bestSampleHausdorff).toArray.toSeq
    val bestIcpCoeff = model.coefficients(bestIcp).toArray.toSeq
    log.append(index = index, targetPath = targetPath, samplingEuclideanLoggerPath = samplingEuclideanLoggerPath, samplingHausdorffLoggerPath = samplingHausdorffLoggerPath,
      coeffInit = coeffInit.toArray.toSeq, coeffSamplingEuclidean = bestSampleCoeffEuclidean, coeffSamplingHausdorff = bestSampleCoeffHausdorff, coeffIcp = bestIcpCoeff,
      samplingEuclidean = samplingEuclidean, samplingHausdorff = samplingHausdorff, icp = icp, numOfEvaluationPoints = numOfEvaluationPoints, numOfSamplePoints = numOfSamplePoints, normalNoise = normalNoise, comment = comment)
    log.writeLog()
  }

  def randomInitPars(model: StatisticalMeshModel, index: Int): ModelFittingParameters = {
    val rotatCenter: EuclideanVector[_3D] = model.referenceMesh.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / model.referenceMesh.pointSet.numberOfPoints.toDouble
    val initPoseParameters = PoseParameters(EuclideanVector3D(0, 0, 0), (0, 0, 0), rotationCenter = rotatCenter.toPoint)
    val initShapeParameters =
      if (index == 0) {
        println("Starting from mean")
        ShapeParameters(DenseVector.zeros[Double](model.rank))
      }
      else {
        val perturbationDistr = new MultivariateNormalDistribution(DenseVector.zeros(model.rank), DenseMatrix.eye[Double](model.rank) * 0.1)
        val rndsample = perturbationDistr.sample()
        ShapeParameters(rndsample)
      }
    ModelFittingParameters(initPoseParameters, initShapeParameters)
  }


  def main(args: Array[String]) {
    scalismo.initialize()

    println("Running ICP vs Sampling experiment (Euclidean and Hausdorff)")

    val normalNoise = 6.0
    val logPath = new File(generalPath, "log")

    val modelFile = new File(generalPath, "femur_gp_model_50-components.h5")
    val model = StatismoIO.readStatismoMeshModel(modelFile).get

    val subPath = "aligned"

    val targetMeshes = new File(generalPath, s"$subPath/meshes/").listFiles().filter(f => f.getName.endsWith(".stl")).sorted

    val experimentFile = new File(logPath, "experiment.json")
    val experimentLogger: JSONExperimentLogger = JSONExperimentLogger(experimentFile, modelFile.toString)

    val numOfICPpointSamples = model.referenceMesh.pointSet.numberOfPoints // Used for the ICP proposal
    val numOfEvalPoints = numOfICPpointSamples // Used for the likelihood evaluator

    println("Target names:")
    targetMeshes.foreach(println(_))

    val numOfConcurrentExecutions = 5 // Number of maximum parallel executions
    val loop = targetMeshes.zipWithIndex.par
    loop.tasksupport = new ForkJoinTaskSupport(new java.util.concurrent.ForkJoinPool(numOfConcurrentExecutions))

    loop.foreach { case (targetMeshFile, _) =>
      println(s"Working with targetMesh: ${targetMeshFile.toString}")
      val targetMesh = MeshIO.readMesh(targetMeshFile).get

      val basename = FileUtils.basename(modelFile)
      val targetname = FileUtils.basename(targetMeshFile)


      (0 to 100).foreach { case (i) =>
        println(s"Starting fitting with random initialization of shape parameters: $i")

        val proposalIcp = MixedProposalDistributions.mixedProposalICP(model, targetMesh, numOfICPpointSamples, projectionDirection = ModelAndTargetSampling)

        val initPars: ModelFittingParameters = randomInitPars(model, i)

        //        val ui = ScalismoUI(s"ICPvsMH-registration_{i}")
        val ui = ScalismoUIHeadless() // ScalismoUI()
      val modelGroup = ui.createGroup("modelGroup")
        val targetGroup = ui.createGroup("targetGroup")
        val finalGroup = ui.createGroup("finalGroup")

        val initialMesh: TriangleMesh[_3D] = ModelFittingParameters.transformedMesh(model, initPars)
        ui.show(finalGroup, initialMesh, "startingPoint").opacity = 0.0

        val showModel = ui.show(modelGroup, model, "model")
        val showTarget = ui.show(targetGroup, targetMesh, "target")
        showTarget.color = Color.YELLOW

        val bestDeterministicRegistration = IcpRegistration.fitting(model, targetMesh, model.referenceMesh.pointSet.numberOfPoints, 50, Option(showModel), initialParameters = Option(initPars))
        ui.show(finalGroup, bestDeterministicRegistration, "ICP_best")

        val evaluatorEuclidean = ProductEvaluators.proximityAndIndependent(model, targetMesh, ModelToTargetEvaluation, uncertainty = 1.0, numberOfEvaluationPoints = numOfEvalPoints)
        val evaluatorHausdorff = ProductEvaluators.proximityAndHausdorff(model, targetMesh, uncertainty = 100.0)

        val numOfSamples = 1000

        val samplingLoggerPathEuclidean = new File(logPath, s"ICPComparisonEuclidean-$normalNoise-$basename-$targetname-samples-$numOfSamples-$i-index.json")
        val bestSamplingRegistrationEuclidean = IcpProposalRegistration.fitting(model, targetMesh, evaluatorEuclidean, proposalIcp, numOfSamples, Option(showModel), samplingLoggerPathEuclidean, initialParameters = Option(initPars))

        val samplingLoggerPathHausdorff = new File(logPath, s"ICPComparisonHausdorff-$normalNoise-$basename-$targetname-samples-$numOfSamples-$i-index.json")
        val bestSamplingRegistrationHausdorff = IcpProposalRegistration.fitting(model, targetMesh, evaluatorHausdorff, proposalIcp, numOfSamples, Option(showModel), samplingLoggerPathHausdorff, initialParameters = Option(initPars))

        appendExperiment(model, experimentLogger, i, targetMesh, bestSamplingRegistrationEuclidean, bestSamplingRegistrationHausdorff, bestDeterministicRegistration, targetMeshFile.toString, samplingLoggerPathEuclidean.toString, samplingLoggerPathHausdorff.toString, initPars.shapeParameters.parameters, numOfEvalPoints, numOfSamples, normalNoise, "")
      }
    }
  }
}
