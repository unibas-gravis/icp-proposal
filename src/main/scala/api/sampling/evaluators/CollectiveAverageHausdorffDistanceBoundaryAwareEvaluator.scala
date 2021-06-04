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

package api.sampling.evaluators

import api.sampling.ModelFittingParameters
import breeze.stats.distributions.ContinuousDistr
import scalismo.common.PointId
import scalismo.geometry.{Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.numerics.UniformMeshSampler3D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.utils.Random

case class CollectiveAverageHausdorffDistanceBoundaryAwareEvaluator(model: StatisticalMeshModel,
                                                                    targetMesh: TriangleMesh[_3D],
                                                                    likelihoodModelAvg: ContinuousDistr[Double],
                                                                    likelihoodModelMax: ContinuousDistr[Double],
                                                                    evaluationMode: EvaluationMode,
                                                                    numberOfPointsForComparison: Int)(implicit random: Random)
  extends DistributionEvaluator[ModelFittingParameters] with EvaluationCaching {

  private val decimatedModel = model.decimate(numberOfPointsForComparison)
  private val decimatedTarget = targetMesh.operations.decimate(numberOfPointsForComparison)

  private val randomPointIdsOnModel = decimatedModel.referenceMesh.pointSet.pointIds.toIndexedSeq
  private val randomPointsOnTarget = decimatedTarget.pointSet.points.toIndexedSeq

  def distModelToTarget(modelSample: TriangleMesh[_3D], targetMesh: TriangleMesh[_3D]): (Double, Double) = {

    val pointsOnSample = randomPointIdsOnModel.map(modelSample.pointSet.point)
    val dists = for (p <- pointsOnSample) yield {
      val pTarget = targetMesh.operations.closestPointOnSurface(p).point
      val pTargetId = targetMesh.pointSet.findClosestPoint(pTarget).id
      if (targetMesh.operations.pointIsOnBoundary(pTargetId)) -1.0
      else (pTarget - p).norm
    }
    val filteredDists = dists.filter(f => f > -1.0)
    (filteredDists.sum / filteredDists.size, filteredDists.max)
  }

  def distTargetToModel(modelSample: TriangleMesh[_3D], targetMesh: TriangleMesh[_3D]): (Double, Double) = {

    val dists = for (p <- randomPointsOnTarget) yield {
      val pSample = modelSample.operations.closestPointOnSurface(p).point
      val pTargetId = modelSample.pointSet.findClosestPoint(pSample).id
      if (targetMesh.operations.pointIsOnBoundary(pTargetId)) -1.0
      else (pSample - p).norm
    }
    val filteredDists = dists.filter(f => f > -1.0)
    (filteredDists.sum / filteredDists.size, filteredDists.max)
  }

  def computeLogValue(sample: ModelFittingParameters): Double = {
    val currentSample = ModelFittingParameters.transformedMesh(model, sample)
    val dist = evaluationMode match {
      case ModelToTargetEvaluation => distModelToTarget(currentSample, targetMesh)
      case TargetToModelEvaluation => distTargetToModel(currentSample, targetMesh)
      case SymmetricEvaluation => {
        val m2t = distModelToTarget(currentSample, targetMesh)
        val t2m = distTargetToModel(currentSample, targetMesh)
        (0.5 * m2t._1 + 0.5 * t2m._1, math.max(m2t._2, t2m._2))
      }
    }
    likelihoodModelAvg.logPdf(dist._1) + likelihoodModelMax.logPdf(dist._2)
  }
}