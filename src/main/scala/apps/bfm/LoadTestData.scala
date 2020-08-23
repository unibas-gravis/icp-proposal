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

import scalismo.geometry._3D
import scalismo.io.{MeshIO, StatismoIO}
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.StatisticalMeshModel

import apps.bfm.Paths.generalPath
import apps.util.FileUtils

object LoadTestData {

  def modelAndTarget(targetIndex: Int = 0): (StatisticalMeshModel, String, TriangleMesh[_3D], TriangleMesh[_3D], File) = {

    val modelFile = new File(generalPath, "faceGPmodel_200c.h5")
    val model = StatismoIO.readStatismoMeshModel(modelFile).get

    println(s"Model file to be used: $modelFile")

    val alignedPath = new File(generalPath, "aligned")
    val alignedMeshesPath = new File(alignedPath, "meshes")
    val partialPath = new File(generalPath, "partial")
    val partialMeshesPath = new File(partialPath, "meshes")

    val targets = alignedMeshesPath.listFiles().filter(f => f.getName.endsWith(".stl")).sorted
    val targetMeshFile = targets(targetIndex)
    val targetName = FileUtils.basename(targetMeshFile)

    println(s"Target file to be used: $targetMeshFile")

    val targetMesh = MeshIO.readMesh(targetMeshFile).get
    val targetPartialMeshFile = partialMeshesPath.listFiles.find(f => f.getName.contains(targetName)).get
    val targetMeshPartial = MeshIO.readMesh(targetPartialMeshFile).get

    val logPath = new File(generalPath, "log")
    if (!logPath.exists()) {
      logPath.mkdir()
    }
    val targetLogFile = new File(logPath, targetName + ".json")

    println(s"Log: $targetLogFile")

    (model, targetName, targetMesh, targetMeshPartial, targetLogFile)
  }
}
