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

package api.other

import scalismo.geometry._3D
import scalismo.mesh.{MeshMetrics, TriangleMesh, TriangleMesh3D}

object RegistrationComparison {

  def evaluateReconstruction2GroundTruth(id: String, reconstruction: TriangleMesh3D, groundTruth: TriangleMesh3D): Unit = {
    val avgDist2Surf = MeshMetrics.avgDistance(reconstruction, groundTruth)

    val hausdorffDistance = MeshMetrics.hausdorffDistance(reconstruction, groundTruth)
    println(s"ID: ${id} average2surface: ${avgDist2Surf} hausdorff: ${hausdorffDistance}")
  }

  private def avgDistanceBoundaryAware(m1: TriangleMesh[_3D], m2: TriangleMesh[_3D]): (Double, Double) = {

    val pointsOnSample = m1.pointSet.points
    val dists = for (p <- pointsOnSample) yield {
      val pTarget = m2.operations.closestPointOnSurface(p).point
      val pTargetId = m2.pointSet.findClosestPoint(pTarget).id
      if (m2.operations.pointIsOnBoundary(pTargetId)) None
      else Option((pTarget - p).norm)
    }
    val filteredDists = dists.toIndexedSeq.filter(f => f.nonEmpty).flatten
    (filteredDists.sum / filteredDists.size, filteredDists.max)
  }


  def evaluateReconstruction2GroundTruthBoundaryAware(id: String, reconstruction: TriangleMesh3D, groundTruth: TriangleMesh3D): Unit = {
    val (avgDist2Surf, maxDist2Surf) = avgDistanceBoundaryAware(reconstruction, groundTruth)

    println(s"ID: ${id} average2surface: ${avgDist2Surf} max: ${maxDist2Surf}")
  }

}