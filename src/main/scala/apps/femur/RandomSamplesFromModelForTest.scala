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

import api.sampling.ShapeParameters
import apps.femur.Paths.{dataFemurPath, generalPath}
import apps.util.{AlignmentTransforms, FileUtils}
import scalismo.geometry.{Point3D, _3D}
import scalismo.io.{LandmarkIO, MeshIO, StatismoIO}
import scalismo.utils.Random
import apps.femur.Paths.dataFemurPath
import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.mesh.TriangleMesh3D
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.ui.api.ScalismoUI


object RandomSamplesFromModelForTest {
  implicit val random: Random = Random(1024)

  def InitialiseShapeParameters(rank: Int, index: Int, variance: Double = 0.1): ShapeParameters = {
    val perturbationDistr = new MultivariateNormalDistribution(DenseVector.zeros(rank), DenseMatrix.eye[Double](rank) * variance)
    if (index == 0) {
      ShapeParameters(DenseVector.zeros[Double](rank))
    }
    else {
      ShapeParameters(perturbationDistr.sample())
    }
  }

  def main(args: Array[String]) {
    scalismo.initialize()

    val modelFile = new File(dataFemurPath, "femur_gp_model_50-components.h5")
    val model = StatismoIO.readStatismoMeshModel(modelFile).get
    val mean = model.mean
//    val subPath = "modelsamples"
//    val targetOutput = new File(dataFemurPath, s"${subPath}")
//    targetOutput.mkdir()
//    println(targetOutput)
//
//    for(i <- 0 to 100) {
//      println(s"${i}")
//      val pars = InitialiseShapeParameters(model.rank, i)
//      MeshIO.writeMesh(model.instance(pars.parameters), new File(targetOutput, s"${i}.vtk"))
//      MeshIO.writeMesh(model.instance(pars.parameters), new File(targetOutput, s"${i}.stl"))
//
//    }
    val pjustPs = MeshIO.readMesh(new File(dataFemurPath, "JustpointCloud.stl")).get
    val pjustPsMesh = TriangleMesh3D(pjustPs.pointSet, mean.triangulation)
    val ref = MeshIO.readMesh(new File("/export/skulls/projects/icp-proposal/data/femur/modelsamples/0.stl")).get
    val target = MeshIO.readMesh(new File("/export/skulls/projects/icp-proposal/data/femur/aligned/meshes/0.stl")).get
    println("done")
    val ui = ScalismoUI()
    ui.show(pjustPsMesh, "just")
    ui.show(ref, "ref")
    ui.show(target, "target")
  }
}
