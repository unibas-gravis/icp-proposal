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

import apps.femur.Paths.{dataFemurPath, generalPath}
import apps.util.{AlignmentTransforms, FileUtils}
import scalismo.geometry.{Point3D, _3D}
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.utils.Random

object AlignShapes {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]) {
    scalismo.initialize()

    val initialPath = new File(generalPath, "step2")
    val initialLMsPath = new File(initialPath, "landmarks")
    val initialMeshesPath = new File(initialPath, "meshes")
    val alignedPath = new File(generalPath, "aligned")
    alignedPath.mkdir()
    val alignedLMsPath = new File(alignedPath, "landmarks")
    alignedLMsPath.mkdir()
    val alignedMeshesPath = new File(alignedPath, "meshes")
    alignedMeshesPath.mkdir()

    val referenceMesh = MeshIO.readMesh(new File(dataFemurPath, "femur_reference.stl")).get
    val referenceLMs = LandmarkIO.readLandmarksJson[_3D](new File(dataFemurPath, "femur_reference.json")).get
    val origin = Point3D(0, 0, 0)

    initialMeshesPath.listFiles.foreach { f =>
      val basename = FileUtils.basename(f)
      val lmName = s"$basename.json"
      val mesh = MeshIO.readMesh(f).get
      val lms = LandmarkIO.readLandmarksJson[_3D](new File(initialLMsPath, lmName)).get
      val transform = AlignmentTransforms.computeTransform(lms, referenceLMs, origin)
      MeshIO.writeMesh(mesh.transform(transform), new File(alignedMeshesPath, f.getName))
      LandmarkIO.writeLandmarksJson[_3D](lms.map(_.transform(transform)), new File(alignedLMsPath, lmName))
    }
  }
}
