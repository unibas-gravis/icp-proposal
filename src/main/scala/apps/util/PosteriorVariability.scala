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

import breeze.linalg.{DenseMatrix, DenseVector, trace}
import scalismo.common.DiscreteField.ScalarMeshField
import scalismo.common.ScalarMeshField
import scalismo.geometry._3D
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}

object PosteriorVariability {
  private val sampleDim = 3
  private val zeroVec = DenseVector.zeros[Double](sampleDim)
  private val zeroMatrix = DenseMatrix.zeros[Double](sampleDim, sampleDim)

  def computeDistanceMapFromMeshesTotal(meshes: IndexedSeq[TriangleMesh[_3D]], ref: TriangleMesh3D): ScalarMeshField[Double] = {
    val numSamples = meshes.length

    def outer(v1: DenseVector[Double], v2: DenseVector[Double]): DenseMatrix[Double] = v1.toDenseMatrix.t * v2.toDenseMatrix

    val pointIdPointSeq: IndexedSeq[Seq[DenseVector[Double]]] = meshes.head.pointSet.pointIds.toIndexedSeq.map {
      id =>
        meshes.map(m => m.pointSet.point(id).toBreezeVector)
    }

    val variance = pointIdPointSeq.map {
      samples =>
        val mean = samples.foldLeft(zeroVec)((acc, s) => acc + s) * (1.0 / numSamples)
        val cov = samples.foldLeft(zeroMatrix)((acc, s) => acc + outer(s - mean, s - mean)) * (1.0 / (numSamples - 1))
        val sum_variance = trace(cov)

        (mean, cov, sum_variance)
    }

    ScalarMeshField(ref, variance.map(_._3))
  }

  def computeDistanceMapFromMeshesNormal(meshes: IndexedSeq[TriangleMesh[_3D]], ref: TriangleMesh3D, sumNormals: Boolean): ScalarMeshField[Double] = {
    val numSamples = meshes.length

    val pointIdPointSeq: IndexedSeq[Seq[DenseVector[Double]]] = meshes.head.pointSet.pointIds.toIndexedSeq.map {
      id =>
        meshes.map(m => m.pointSet.point(id).toBreezeVector)
    }

    val variance = (pointIdPointSeq zip ref.pointSet.pointIds.toIndexedSeq).map {
      case (samples, pId) =>
        val m: DenseVector[Double] = samples.foldLeft(zeroVec)((acc, s) => acc + s) * (1.0 / numSamples)

        val n = if (sumNormals) {
          meshes.foldLeft(zeroVec)((acc, s) => acc + s.vertexNormals.atPoint(pId).normalize.toBreezeVector) * (1.0 / numSamples)
        }
        else {
          ref.vertexNormals.atPoint(pId).normalize.toBreezeVector
        }
        samples.foldLeft(0.0)((acc, s) => acc + math.pow(n.dot(s - m), 2)) * (1.0 / (numSamples - 1))
    }
    ScalarMeshField(ref, variance)
  }
}
