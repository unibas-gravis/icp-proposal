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

package apps.util

import scalismo.geometry.{Landmark, Point, _3D}
import scalismo.registration.{LandmarkRegistration, RigidTransformation}

object AlignmentTransforms {

  def computeTransform(lm1: Seq[Landmark[_3D]], lm2: Seq[Landmark[_3D]], center: Point[_3D]): RigidTransformation[_3D] = {
    val commonLmNames = lm1.map(_.id) intersect lm2.map(_.id)

    val landmarksPairs = commonLmNames.map(name => (lm1.find(_.id == name).get.point, lm2.find(_.id == name).get.point))
    LandmarkRegistration.rigid3DLandmarkRegistration(landmarksPairs, center)
  }
}
