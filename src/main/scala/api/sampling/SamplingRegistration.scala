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

package api.sampling

import java.io.File

import api.other.RegistrationComparison
import api.sampling.loggers.JSONAcceptRejectLogger
import breeze.linalg.DenseVector
import com.typesafe.scalalogging.Logger
import scalismo.geometry._
import scalismo.mesh.TriangleMesh3D
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.algorithms.MetropolisHastings
import scalismo.sampling.loggers.ChainStateLogger.implicits._
import scalismo.sampling.loggers.{BestSampleLogger, ChainStateLoggerContainer}
import scalismo.sampling.proposals.MixtureProposal
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.ui.api.StatisticalMeshModelViewControls
import scalismo.utils.Random


class SamplingRegistration(model: StatisticalMeshModel, sample: TriangleMesh3D, modelUi: Option[StatisticalMeshModelViewControls] = None, modelUiUpdateInterval: Int = 1000, acceptInfoPrintInterval: Int = 10000) {
  implicit val random: Random = Random(1024)

  val rotatCenter: EuclideanVector[_3D] = model.referenceMesh.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / model.referenceMesh.pointSet.numberOfPoints.toDouble
  val initPoseParameters = PoseParameters(EuclideanVector3D(0, 0, 0), (0, 0, 0), rotationCenter = rotatCenter.toPoint)
  val initShapeParameters = ShapeParameters(DenseVector.zeros[Double](model.rank))
  val initialParametersZero = ModelFittingParameters(initPoseParameters, initShapeParameters)

  def runfitting(evaluators: Map[String, DistributionEvaluator[ModelFittingParameters]], generator: MixtureProposal.ProposalGeneratorWithTransition[ModelFittingParameters], numOfSamples: Int, initialModelParameters: Option[ModelFittingParameters] = None, jsonName: File = new File("tmp.json")): ModelFittingParameters = {
    val logger: Logger = Logger(s"MCMC-${jsonName.getName}")

    val initialParameters = initialModelParameters.getOrElse(initialParametersZero)

    val acceptRejectLogger = new JSONAcceptRejectLogger[ModelFittingParameters](jsonName, Option(evaluators))

    val evaluator = evaluators("product")

    val chain: MetropolisHastings[ModelFittingParameters] = MetropolisHastings(generator, evaluator)

    val bestSamplelogger = BestSampleLogger(evaluator)

    val mhIt = chain.iterator(initialParameters, acceptRejectLogger) loggedWith ChainStateLoggerContainer(Seq(bestSamplelogger))

    val samplingIterator = for ((theta, i) <- mhIt.zipWithIndex) yield {
      if (i % modelUiUpdateInterval == 0 && i != 0) {
        logger.debug(" index: " + i + " LOG: " + bestSamplelogger.currentBestValue().getOrElse(theta))
        if (modelUi.isDefined) {
          val thetaToUse = if (acceptRejectLogger.logSamples.nonEmpty) {
            acceptRejectLogger.logSamples.last // Get last accepted sample
          }
          else {
            theta
          }
          val rigidTrans = ModelFittingParameters.poseTransform(thetaToUse)
          modelUi.get.shapeModelTransformationView.poseTransformationView.transformation = rigidTrans
          modelUi.get.shapeModelTransformationView.shapeTransformationView.coefficients = thetaToUse.shapeParameters.parameters
        }
      }
      if (i % acceptInfoPrintInterval == 0 && i != 0) {
        acceptRejectLogger.writeLog()
        acceptRejectLogger.printAcceptInfo(jsonName.getName)
        val bestTheta = bestSamplelogger.currentBestSample().getOrElse(theta)
        val rigidTrans = ModelFittingParameters.poseTransform(bestTheta)
        val bestStuff = model.instance(bestTheta.shapeParameters.parameters).transform(rigidTrans)
        RegistrationComparison.evaluateReconstruction2GroundTruthBoundaryAware("Sampling", bestStuff, sample)
      }
      theta
    }
    samplingIterator.take(numOfSamples).toSeq.last

    logger.info("Done fitting - STATS:")
    acceptRejectLogger.writeLog()
    acceptRejectLogger.printAcceptInfo()

    val bestSampleCoeff: ModelFittingParameters = bestSamplelogger.currentBestSample().get
    bestSampleCoeff
  }
}

