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

package api.sampling.proposals

import api.sampling.ModelFittingParameters
import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.sampling.{ProposalGenerator, SymmetricTransitionRatio, TransitionProbability}
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}
import scalismo.utils.Random

case class RandomShapeUpdateProposal(model: StatisticalMeshModel, stdev: Double, generatedBy: String = "RandomShapeUpdateProposal")(implicit random: Random)
  extends ProposalGenerator[ModelFittingParameters]
    with SymmetricTransitionRatio[ModelFittingParameters]
    with TransitionProbability[ModelFittingParameters] {

  private val rank: Int = model.rank
  private val perturbationDistr = new MultivariateNormalDistribution(DenseVector.zeros(rank), DenseMatrix.eye[Double](rank) * stdev * stdev)
  private val independentDistr = breeze.stats.distributions.Gaussian(0, stdev)

  override def propose(theta: ModelFittingParameters): ModelFittingParameters = {
    val currentCoeffs = theta.shapeParameters.parameters
    theta.copy(shapeParameters = theta.shapeParameters.copy(parameters = currentCoeffs + perturbationDistr.sample), generatedBy = generatedBy)
  }

  override def logTransitionProbability(from: ModelFittingParameters, to: ModelFittingParameters): Double = {
    val residual = to.shapeParameters.parameters - from.shapeParameters.parameters
    perturbationDistr.logpdf(residual)
  }
}
