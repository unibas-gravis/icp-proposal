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

package apps.bfm

import java.io.File

import apps.bfm.Paths.generalPath
import scalismo.common.ScalarArray
import scalismo.geometry._
import scalismo.io.StatismoIO
import scalismo.mesh.ScalarMeshField
import scalismo.numerics.UniformMeshSampler3D
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, StatisticalMeshModel}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random


object CreateGPModel {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]): Unit = {
      scalismo.initialize()


      val modelFile = new File(generalPath, "model2017-1_face12_nomouth.h5")
      val model = StatismoIO.readStatismoMeshModel(modelFile, "shape").get

      val numOfBasisFunctions = 200
//      val referenceMesh = model.referenceMesh
      val referenceMesh = model.referenceMesh.operations.decimate(2000) // Speed up - but makes the model more coarse


      val numSamplePoints = 800

      val sa: ScalarArray[Int] = ScalarArray(Array.fill[Int](referenceMesh.pointSet.numberOfPoints)(3))
      val smf: ScalarMeshField[Int] = ScalarMeshField(referenceMesh, sa)
      val sem: ScalarMeshField[Int] = ScalarMeshField(referenceMesh, sa)
      val faceMask = FaceMask(smf, sem)

      val faceKernel = FaceKernel(faceMask, referenceMesh)
      val gp = GaussianProcess[_3D, EuclideanVector[_3D]](faceKernel)
      val sampler = UniformMeshSampler3D(referenceMesh, numberOfPoints = numSamplePoints)
      val lowRankGP: LowRankGaussianProcess[_3D, EuclideanVector[_3D]] = LowRankGaussianProcess.approximateGPNystrom(gp, sampler, numBasisFunctions = numOfBasisFunctions)

      val gpModel = StatisticalMeshModel(referenceMesh, lowRankGP)

      val ui = ScalismoUI()
      ui.show(gpModel, "GP")

      StatismoIO.writeStatismoMeshModel(gpModel, new File(generalPath, s"faceGPmodel_${numOfBasisFunctions}c.h5"))
  }
}
