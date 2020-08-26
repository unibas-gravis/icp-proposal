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

import api.sampling.ShapeParameters
import scalismo.utils.Random
import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.statisticalmodel.MultivariateNormalDistribution


object RandomSamplesFromModel {
  implicit val random: Random = Random(1024)

  def InitialiseShapeParameters(rank: Int, index: Int, variance: Double = 0.1): ShapeParameters = {
    if (index == 0) {
      ShapeParameters(DenseVector.zeros[Double](rank))
    }
    else {
      val perturbationDistr = new MultivariateNormalDistribution(DenseVector.zeros(rank), DenseMatrix.eye[Double](rank) * variance)
      ShapeParameters(perturbationDistr.sample())
    }
  }
}
