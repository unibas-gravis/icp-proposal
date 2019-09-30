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

import breeze.linalg.DenseMatrix
import breeze.linalg.svd.SVD
import scalismo.common.{Domain, RealSpace, VectorField}
import scalismo.geometry._
import scalismo.io.{MeshIO, StatismoIO}
import scalismo.kernels._
import scalismo.mesh.TriangleMesh3D
import scalismo.numerics.UniformMeshSampler3D
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, StatisticalMeshModel}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random
import apps.femur.Paths.dataFemurPath


object CreateGPModel {
  implicit val random: Random = Random(1024)

  def approxTotalVariance(gp: GaussianProcess[_3D, EuclideanVector[_3D]], evaluationDomain: TriangleMesh3D): Double = {
    val sampler = UniformMeshSampler3D(evaluationDomain, numberOfPoints = 50000)
    val points = sampler.sample.unzip._1
    val covValues = for (pt <- points) yield {
      gp.cov(pt, pt)
    }
    val variance = covValues.map(cm => cm(0, 0) + cm(1, 1) + cm(2, 2)).sum / points.size
    variance
  }

  def getAxisOfMainVariance(mesh: TriangleMesh3D): DenseMatrix[Double] = {
    val N = 1.0 / mesh.pointSet.numberOfPoints
    val centerOfMass = (mesh.pointSet.points.foldLeft[EuclideanVector[_3D]](EuclideanVector(0f, 0f, 0f))((acc, e) => acc + e.toVector) * N).toPoint
    val cov = mesh.pointSet.points.foldLeft[SquareMatrix[_3D]](SquareMatrix.zeros)((acc, e) => acc + (e - centerOfMass).outer(e - centerOfMass)) * N
    val SVD(u, _, _) = breeze.linalg.svd(cov.toBreezeMatrix)
    u
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val ui = ScalismoUI()

    Seq(50, 100).foreach { i =>
      val referenceMesh = MeshIO.readMesh(new File(dataFemurPath, "femur_reference.stl")).get

      val outputModelFile = new File(dataFemurPath, s"femur_gp_model_$i-components.h5")

      println("Num of points in ref: " + referenceMesh.pointSet.numberOfPoints)

      val zeroMean = VectorField(RealSpace[_3D], (_: Point[_3D]) => EuclideanVector.zeros[_3D])

      val cov: MatrixValuedPDKernel[_3D] = new MatrixValuedPDKernel[_3D]() {
        private val directionMatrix = getAxisOfMainVariance(referenceMesh)
        // Adds more variance along the main direction of variation (the bone length)
        private val baseMatrix = directionMatrix * DenseMatrix((10.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0)) * directionMatrix.t
        private val baseKernel = GaussianKernel[_3D](90) * 10.0
        private val midKernels = DiagonalKernel[_3D](GaussianKernel(40), 3) * 5.0
        private val smallKernels = DiagonalKernel[_3D](GaussianKernel(10), 3) * 3.0

        override protected def k(x: Point[_3D], y: Point[_3D]): DenseMatrix[Double] = {
          (baseMatrix * baseKernel(x, y)) + midKernels(x, y) + smallKernels(x, y)
        }

        override def outputDim = 3

        override def domain: Domain[_3D] = RealSpace[_3D]
      }

      val gp = GaussianProcess[_3D, EuclideanVector[_3D]](zeroMean, cov)

      val totalVariance = approxTotalVariance(gp, referenceMesh)
      println("total variance  " + totalVariance)

      val numOfSamplePoints = i
      println(s"num of sampled points: $numOfSamplePoints")
      val sampler = UniformMeshSampler3D(referenceMesh, numberOfPoints = numOfSamplePoints)
      val lowRankGP = LowRankGaussianProcess.approximateGPNystrom(gp, sampler, numBasisFunctions = i + 1)

      val approximatedVar = lowRankGP.klBasis.map(_.eigenvalue).sum
      println(s"Ratio of approximated variance " + approximatedVar / totalVariance)

      println(lowRankGP.klBasis.map(_.eigenvalue))
      val mm = StatisticalMeshModel(referenceMesh, lowRankGP)
      val modelGroup = ui.createGroup(s"Model-$i")
      ui.show(modelGroup, mm, "model")

      StatismoIO.writeStatismoMeshModel(mm, outputModelFile)
    }
  }
}
