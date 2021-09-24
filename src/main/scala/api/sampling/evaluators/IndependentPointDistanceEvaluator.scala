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
import scalismo.mesh.TriangleMesh3D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.StatisticalMeshModel

case class IndependentPointDistanceEvaluator(model: StatisticalMeshModel,
                                             targetMesh: TriangleMesh3D,
                                             likelihoodModel: ContinuousDistr[Double],
                                             evaluationMode: EvaluationMode,
                                             numberOfPointsForComparison: Int)
  extends DistributionEvaluator[ModelFittingParameters] with EvaluationCaching {

  private val decimatedModel = model.decimate(numberOfPointsForComparison)
  private val decimatedTarget = targetMesh.operations.decimate(numberOfPointsForComparison)

  private val randomPointsOnTarget: IndexedSeq[Point[_3D]] = decimatedTarget.pointSet.points.toIndexedSeq
  private val randomPointIdsOnModel: IndexedSeq[PointId] = decimatedModel.referenceMesh.pointSet.pointIds.toIndexedSeq

  def distModelToTarget(modelSample: TriangleMesh3D): Double = {
    val pointsOnSample = randomPointIdsOnModel.map(modelSample.pointSet.point)
    val dists = for (pt <- pointsOnSample) yield {
      likelihoodModel.logPdf((targetMesh.operations.closestPointOnSurface(pt).point - pt).norm)
    }
    dists.sum
  }


  def distTargetToModel(modelSample: TriangleMesh3D): Double = {
    val dists = for (pt <- randomPointsOnTarget) yield {
      likelihoodModel.logPdf((modelSample.operations.closestPointOnSurface(pt).point - pt).norm)
    }
    dists.sum
  }


  def computeLogValue(sample: ModelFittingParameters): Double = {

    val currentSample = ModelFittingParameters.transformedMesh(model, sample)
    val dist = evaluationMode match {
      case ModelToTargetEvaluation => distModelToTarget(currentSample)
      case TargetToModelEvaluation => distTargetToModel(currentSample)
      case SymmetricEvaluation => 0.5 * distModelToTarget(currentSample) + 0.5 * distTargetToModel(currentSample)
    }
    dist
  }
}



