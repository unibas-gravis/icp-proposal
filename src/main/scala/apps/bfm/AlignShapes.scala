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

import apps.util.{AlignmentTransforms, FileUtils}
import scalismo.geometry.{Point3D, _3D}
import scalismo.io.{LandmarkIO, MeshIO, StatismoIO}
import scalismo.registration.ScalingTransformation
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random
import apps.bfm.Paths.generalPath

object AlignShapes {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]) {
    scalismo.initialize()

    val initialPath = new File(generalPath, "initial")
    val initialLMsPath = new File(initialPath, "landmarks")
    val initialMeshesPath = new File(initialPath, "PublicMM1/03_scans_ply")
    val alignedPath = new File(generalPath, "aligned")
    val alignedLMsPath = new File(alignedPath, "landmarks")
    val alignedMeshesPath = new File(alignedPath, "meshes")
    val partialPath = new File(generalPath, "partial")
    val partialLMsPath = new File(partialPath, "landmarks")
    val partialMeshesPath = new File(partialPath, "meshes")

    if (!alignedPath.exists()) {
      alignedPath.mkdir()
      alignedLMsPath.mkdir()
      alignedMeshesPath.mkdir()
    }
    if (!partialPath.exists()) {
      partialPath.mkdir()
      partialLMsPath.mkdir()
      partialMeshesPath.mkdir()
    }

    val modelLms = LandmarkIO.readLandmarksJson[_3D](new File(generalPath, "bfm.json")).get
    val model = StatismoIO.readStatismoMeshModel(new File(generalPath, "model2017-1_face12_nomouth.h5"), "shape").get
    val origin = Point3D(0, 0, 0)

    val ui = ScalismoUI()
    val modelGroup = ui.createGroup("model")
    ui.show(modelGroup, model, "model")
    ui.show(modelGroup, modelLms, "landmarks")

    val scalingTransform = ScalingTransformation[_3D](1 / 1000.0)

    initialMeshesPath.listFiles.foreach { f =>
      val basename = FileUtils.basename(f)
      val lmName = s"$basename.json"

      val meshInit = scalismo.faces.io.MeshIO.read(f).get.shape
      val mesh = meshInit.transform(scalingTransform)
      val lmsInit = LandmarkIO.readLandmarksJson[_3D](new File(initialLMsPath, lmName)).get
      val lms = lmsInit.map(_.transform(scalingTransform))

      val alignmentTransform = AlignmentTransforms.computeTransform(lms, modelLms, origin)
      val alignedMesh = mesh.transform(alignmentTransform)
      val alignedLms = lms.map(_.transform(alignmentTransform))

      MeshIO.writeMesh(alignedMesh, new File(alignedMeshesPath, basename + ".stl"))
      LandmarkIO.writeLandmarksJson[_3D](alignedLms, new File(alignedLMsPath, lmName))

      val noseCenterId = "center.nose.tip"
      val noseTipPoint = alignedLms.find(lm => lm.id == noseCenterId).get
      val noseCutIds = alignedMesh.pointSet.findNClosestPoints(noseTipPoint.point, 1000).map(p => p.id)
      val partialMesh = alignedMesh.operations.maskPoints(id => !noseCutIds.contains(id)).transformedMesh
      val partialLms = alignedLms.filter(lm => lm.id != noseCenterId)
      MeshIO.writeMesh(partialMesh, new File(partialMeshesPath, basename + ".stl"))
      LandmarkIO.writeLandmarksJson[_3D](partialLms, new File(partialLMsPath, lmName))

      val meshGroup = ui.createGroup(basename)
      ui.show(meshGroup, alignedMesh, basename)
      ui.show(meshGroup, alignedLms, "landmarks")
    }
  }
}
