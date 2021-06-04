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
import apps.util.{AlignmentTransforms, FileUtils}
import scalismo.common.PointId
import scalismo.geometry.{Point3D, _3D}
import scalismo.io.{LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.transformations.Scaling
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

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
    val model = StatisticalModelIO.readStatisticalMeshModel(new File(generalPath, "model2017-1_face12_nomouth.h5"), "shape").get
    val origin = Point3D(0, 0, 0)

    val ui = ScalismoUI()
    val modelGroup = ui.createGroup("model")
    ui.show(modelGroup, model, "model")
    ui.show(modelGroup, modelLms, "landmarks")

    val scalingTransform = Scaling[_3D](1 / 1000.0)

    val mouthMaskIdsInit = Seq(6293, 6294, 6295, 6421, 6422, 6423, 6424, 6425, 6426, 6549, 6550, 6551, 6552, 6553, 6554, 6555, 6556, 6678, 6679, 6680, 6681, 6682, 6683, 6684, 6685, 6686, 6808, 6809, 6810, 6811, 6812, 6813, 6814, 6815, 6816, 6936, 6937, 6938, 6939, 6940, 6941, 6942, 6943, 6944, 6945, 7066, 7067, 7068, 7069, 7070, 7071, 7072, 7073, 7074, 7194, 7195, 7196, 7197, 7198, 7199, 7200, 7201, 7202, 7203, 7323, 7324, 7325, 7326, 7327, 7328, 7329, 7330, 7331, 7453, 7454, 7455, 7456, 7457, 7458, 7459, 7460, 7461, 7582, 7583, 7584, 7585, 7586, 7587, 7588, 7589, 7590, 7710, 7711, 7712, 7713, 7714, 7715, 7716, 7717, 7718, 7719, 7839, 7840, 7841, 7842, 7843, 7844, 7845, 7846, 7847, 7848, 7968, 7969, 7970, 7971, 7972, 7973, 7974, 7975, 7976, 7977, 7978, 8098, 8099, 8100, 8101, 8102, 8103, 8104, 8105, 8106, 8107, 8226, 8227, 8228, 8229, 8230, 8231, 8232, 8233, 8234, 8235, 8236, 8355, 8356, 8357, 8358, 8359, 8360, 8361, 8362, 8363, 8364, 8365, 8486, 8487, 8488, 8489, 8490, 8491, 8492, 8493, 8613, 8614, 8615, 8616, 8617, 8618, 8619, 8620, 8621, 8622, 8623, 8742, 8743, 8744, 8745, 8746, 8747, 8748, 8749, 8750, 8751, 8872, 8873, 8874, 8875, 8876, 8877, 8878, 8879, 8880, 9000, 9001, 9002, 9003, 9004, 9005, 9006, 9007, 9008, 9009, 9129, 9130, 9131, 9132, 9133, 9134, 9135, 9136, 9137, 9138, 9258, 9259, 9260, 9261, 9262, 9263, 9264, 9265, 9266, 9267, 9388, 9389, 9390, 9391, 9392, 9393, 9394, 9395, 9396, 9397, 9516, 9517, 9518, 9519, 9520, 9521, 9522, 9523, 9524, 9525, 9645, 9646, 9647, 9648, 9649, 9650, 9651, 9652, 9653, 9654, 9775, 9776, 9777, 9778, 9779, 9780, 9781, 9782, 9783, 9904, 9905, 9906, 9907, 9908, 9909, 9910, 9911, 9912, 10033, 10034, 10035, 10036, 10037, 10038, 10039, 10040, 10162, 10163, 10164, 10165, 10166, 10167, 10168, 10293, 10294, 10295, 10296, 10422, 10423, 10424)
    val mouthMaskIds = mouthMaskIdsInit.map(id => PointId(id))

    initialMeshesPath.listFiles.sorted.foreach { f =>
      println(s"Processing file: ${f}")
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
      val allCutIds = noseCutIds ++ mouthMaskIds
      val partialMesh = alignedMesh.operations.maskPoints(id => !allCutIds.contains(id)).transformedMesh
      val partialLms = alignedLms.filter(lm => lm.id != noseCenterId)
      MeshIO.writeMesh(partialMesh, new File(partialMeshesPath, basename + ".stl"))
      LandmarkIO.writeLandmarksJson[_3D](partialLms, new File(partialLMsPath, lmName))

      val meshGroup = ui.createGroup(basename)
      ui.show(meshGroup, partialMesh, basename).opacity = 0.0
      ui.show(meshGroup, partialLms, "landmarks").foreach(_.opacity = 0.0)
    }
  }
}
