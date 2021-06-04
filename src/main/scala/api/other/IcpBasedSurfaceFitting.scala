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

package api.other

import breeze.linalg.DenseVector
import com.typesafe.scalalogging.Logger
import scalismo.common.PointId
import scalismo.common.interpolation.NearestNeighborInterpolator
import scalismo.geometry._
import scalismo.mesh.TriangleMesh3D
import scalismo.numerics.UniformMeshSampler3D
import scalismo.registration.GaussianProcessTransformationSpace
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.transformations.{TranslationAfterRotation, TranslationAfterRotationSpace3D}
import scalismo.ui.api.StatisticalMeshModelViewControls
import scalismo.utils.Random

case class IcpBasedSurfaceFitting(model: StatisticalMeshModel, target: TriangleMesh3D, numOfSamplePoints: Int, stepLength: Double = 1.0, projectionDirection: IcpProjectionDirection, showModel: Option[StatisticalMeshModelViewControls] = None) {
  implicit val random: Random = Random(1024)
  private val rigidIdentity = TranslationAfterRotationSpace3D(rotationCenter = Point3D(0, 0, 0)).identityTransformation

  private val transformationSpace = GaussianProcessTransformationSpace[_3D](model.gp.interpolate(NearestNeighborInterpolator()))
  private val zeroParameters = (DenseVector.zeros[Double](model.rank), rigidIdentity)

  private val rw = 1.0
  private val lmSeq = Seq(Landmark("", Point3D(0, 0, 0)))
  private val defIterations = Seq(rw, rw / 10.0, rw / 100.0)

  val logger: Logger = Logger("ICP-Logger")


  def runfitting(numIterations: Int, iterationSeq: Seq[Double] = defIterations, initialModelParameters: Option[(DenseVector[Double], TranslationAfterRotation[_3D])] = None): TriangleMesh3D = {

    val initialParameters = initialModelParameters.getOrElse(zeroParameters)

    val center: EuclideanVector[_3D] = model.referenceMesh.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / model.referenceMesh.pointSet.numberOfPoints.toDouble
    val targetPointSamples = UniformMeshSampler3D(target, numOfSamplePoints).sample.map(s => s._1)
    val modelPointSamples = UniformMeshSampler3D(model.referenceMesh, numOfSamplePoints).sample.map(s => s._1)
    val pointIds = modelPointSamples.map { s => model.referenceMesh.pointSet.findClosestPoint(s).id }

    def recursion(params: DenseVector[Double], nbIterations: Int, sigma: Double, currentTrans: TranslationAfterRotation[_3D] = rigidIdentity): (DenseVector[Double], TranslationAfterRotation[_3D]) = {
      if ((numIterations - nbIterations) % 10 == 0) {
        logger.debug(s"Iteration: (${numIterations - nbIterations}) / ${numIterations}")
      }
      val finalTrans = currentTrans

      val instanceAligned = model.transform(currentTrans).instance(params)

      val projectionDirectionLocal =
        if (projectionDirection == ModelSampling) ModelSampling
        else if (projectionDirection == TargetSampling) TargetSampling
        else {
          if (scala.util.Random.nextBoolean) ModelSampling
          else TargetSampling
        }

      val corrPandId: IndexedSeq[(PointId, Point[_3D])] = if (projectionDirectionLocal == ModelSampling) {
        val currentPoints = pointIds.map(id => (id, instanceAligned.pointSet.point(id)))
        currentPoints.map { case (id, pt) => (id, target.operations.closestPointOnSurface(pt).point) }.toIndexedSeq
      }
      else {
        targetPointSamples.map { pt =>
          (instanceAligned.pointSet.findClosestPoint(pt).id, pt)
        }.toIndexedSeq
      }

      val posterior = model.posterior(corrPandId, sigma)
      val fit = posterior.mean

      val newCoeffInit = model.coefficients(fit)
      val newCoeff = params + (newCoeffInit - params) * stepLength


      if (showModel.isDefined) {
        showModel.get.shapeModelTransformationView.shapeTransformationView.coefficients = newCoeff
        showModel.get.shapeModelTransformationView.poseTransformationView.transformation = finalTrans

      }

      if (nbIterations > 0) {
        try {
          recursion(newCoeff, nbIterations - 1, sigma, finalTrans)
        }
        catch {
          case e: Exception => {
            System.err.println(s"An error occured in IcpBasedSurfaceFitting, iteration: ${numIterations - nbIterations}) / ${numIterations}")
            System.err.println(e)
            (newCoeff, finalTrans)
          }
        }
      }
      else {
        (newCoeff, finalTrans)
      }
    }

    val finalRegResult = iterationSeq.foldLeft(initialParameters) {
      (params, w) =>
        logger.info(s"Sigma: ${w}")

        val (pars, rigid) = recursion(params._1, numIterations, w, params._2)

        if (showModel.isDefined) {
          showModel.get.shapeModelTransformationView.shapeTransformationView.coefficients = pars
          showModel.get.shapeModelTransformationView.poseTransformationView.transformation = rigid
        }
        (pars, rigid)
    }
    val finalTransform = transformationSpace.transformationForParameters(finalRegResult._1)

    model.transform(finalRegResult._2).instance(finalRegResult._1)
  }
}
