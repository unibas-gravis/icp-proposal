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

import api.sampling.evaluators._
import scalismo.mesh.TriangleMesh3D
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.evaluators.ProductEvaluator
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.utils.Random.implicits._

object ProductEvaluators {

  def acceptAll(): Map[String, DistributionEvaluator[ModelFittingParameters]] = {
    val evaluator: ProductEvaluator[ModelFittingParameters] = ProductEvaluator(
      AcceptAllEvaluator()
    )
    val evaluatorMap: Map[String, DistributionEvaluator[ModelFittingParameters]] = Map(
      "product" -> evaluator
    )
    evaluatorMap
  }

  def proximityAndIndependent(model: StatisticalMeshModel, target: TriangleMesh3D, evaluationMode: EvaluationMode, uncertainty: Double = 1.0, numberOfEvaluationPoints: Int = 100): Map[String, DistributionEvaluator[ModelFittingParameters]] = {
    val likelihoodIndependent = breeze.stats.distributions.Gaussian(0, uncertainty)

    val indepPointEval = IndependentPointDistanceEvaluator(model, target, likelihoodIndependent, evaluationMode, numberOfEvaluationPoints)
    val evalProximity = ModelPriorEvaluator(model)

    val evaluator = ProductEvaluator(
      evalProximity,
      indepPointEval
    )

    val evaluatorMap: Map[String, DistributionEvaluator[ModelFittingParameters]] = Map(
      "product" -> evaluator,
      "prior" -> evalProximity,
      "distance" -> indepPointEval
    )
    evaluatorMap
  }

  def proximityAndHausdorff(model: StatisticalMeshModel, target: TriangleMesh3D, uncertainty: Double = 1.0): Map[String, DistributionEvaluator[ModelFittingParameters]] = {
    val likelihoodIndependent = breeze.stats.distributions.Exponential(uncertainty)

    val indepPointEval = HausdorffDistanceEvaluator(model, target, likelihoodIndependent)
    val evalProximity = ModelPriorEvaluator(model)

    val evaluator = ProductEvaluator(
      evalProximity,
      indepPointEval
    )

    val evaluatorMap: Map[String, DistributionEvaluator[ModelFittingParameters]] = Map(
      "product" -> evaluator,
      "prior" -> evalProximity,
      "distance_haussdorff" -> indepPointEval
    )
    evaluatorMap
  }

  def proximityAndCollectiveHausdorffBoundaryAware(model: StatisticalMeshModel, target: TriangleMesh3D, evaluationMode: EvaluationMode, uncertaintyAvg: Double = 1.0, uncertaintyMax: Double = 5.0, mean: Double = 0.0, numberOfEvaluationPoints: Int = 100): Map[String, DistributionEvaluator[ModelFittingParameters]] = {
    val likelihoodCollective = breeze.stats.distributions.Gaussian(mean, uncertaintyAvg)
    val likelihoodMax = breeze.stats.distributions.Exponential(uncertaintyMax)

    val collectivePointEval = CollectiveAverageHausdorffDistanceBoundaryAwareEvaluator(model, target, likelihoodCollective, likelihoodMax, evaluationMode, numberOfEvaluationPoints)
    val evalProximity = ModelPriorEvaluator(model)

    val evaluator = ProductEvaluator(
      evalProximity,
      collectivePointEval
    )

    val evaluatorMap: Map[String, DistributionEvaluator[ModelFittingParameters]] = Map(
      "product" -> evaluator,
      "prior" -> evalProximity,
      "collective_distance" -> collectivePointEval
    )
    evaluatorMap
  }
}
