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

import apps.util.AlignmentTransforms
import scalismo.geometry.{Landmark, Point3D, _3D}
import scalismo.io.{LandmarkIO, MeshIO, StatismoIO}
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.utils.Random.implicits._

object LoadTestData {

  def modelAndTarget(): (StatisticalMeshModel, Seq[Landmark[_3D]], TriangleMesh[_3D], Seq[Landmark[_3D]]) = {
    val dataFemurPath = new File("data/femur")

    val modelFile = new File(dataFemurPath, "femur_gp_model_50-components.h5")
    val model = StatismoIO.readStatismoMeshModel(modelFile).get
    val modelLmsFile = new File(dataFemurPath, "femur_reference.json")
    val modelLms = LandmarkIO.readLandmarksJson[_3D](modelLmsFile).get
    println(s"Model file to be used: $modelFile")

    val targetMeshFile = new File(dataFemurPath, "femur_target.stl")
    val targetMeshInit = MeshIO.readMesh(targetMeshFile).get
    val targetLmsFile = new File(dataFemurPath, s"femur_target.json")
    val targetLmsInit = LandmarkIO.readLandmarksJson[_3D](targetLmsFile).get

    val targetTransform = AlignmentTransforms.computeTransform(targetLmsInit, modelLms, Point3D(0, 0, 0))
    val targetMesh = targetMeshInit.transform(targetTransform)
    val targetLms = targetLmsInit.map(_.transform(targetTransform))

    (model, modelLms, targetMesh, targetLms)
  }
}
