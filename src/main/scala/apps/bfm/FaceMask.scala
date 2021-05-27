/*
 * Copyright University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package apps.bfm

import scalismo.common.DiscreteField.ScalarMeshField
import scalismo.common.{PointId, ScalarMeshField, UnstructuredPointsDomain, UnstructuredPointsDomain3D}
import scalismo.geometry.{Point, _3D}
import scalismo.kernels.GaussianKernel
import scalismo.mesh.TriangleMesh
import scalismo.utils.Memoize

case class FaceMask(levelMask: ScalarMeshField[Int], semanticMask: ScalarMeshField[Int]) {

  def isEarRegion(id : PointId) : Boolean = {
    semanticMask(id) == 3 //1
  }

  def isLipPoint(id : PointId) : Boolean = {
    semanticMask(id) == 3 //2
  }

  def isNoseRegion(id : PointId) : Boolean = {
    semanticMask(id) == 3
  }

  // Returns a value in the interval [0,1] indicating whether a point belongs to the region
  def computeSmoothedRegions(referenceMesh: TriangleMesh[_3D], level : Int, stddev : Double) : Point[_3D] => Double = {

    val transformedMask = ScalarMeshField(referenceMesh, levelMask.data)
    val pointsWithRegions = transformedMask.pointsWithValues.toIndexedSeq

    val regionSmoother = GaussianKernel[_3D](stddev)
    val regionPts = UnstructuredPointsDomain3D(pointsWithRegions.filter(_._2 >= level).map(_._1))

    def regionWeight(p : Point[_3D]) : Double = {
      regionSmoother(regionPts.pointSet.findClosestPoint(p).point,p)
    }

    Memoize(regionWeight,referenceMesh.pointSet.numberOfPoints)
  }

}

