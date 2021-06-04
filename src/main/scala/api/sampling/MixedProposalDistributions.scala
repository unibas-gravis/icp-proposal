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

package api.sampling

import api.other.{IcpProjectionDirection, ModelAndTargetSampling, ModelSampling, TargetSampling}
import api.sampling.proposals._
import scalismo.mesh.TriangleMesh3D
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.sampling.proposals.MixtureProposal.implicits._

object MixedProposalDistributions {

  def mixedRandomPoseProposal(rotYaw: Double = 0.01, rotPitch: Double = 0.01, rotRoll: Double = 0.01, transX: Double = 0.1, transY: Double = 0.1, transZ: Double = 0.1)(implicit rand: scalismo.utils.Random): ProposalGeneratorWithTransition[ModelFittingParameters] ={
    val mixproposal = MixtureProposal(
        0.5 *: GaussianAxisRotationProposal(rotYaw, YawAxis, generatedBy = s"RotationYaw-${rotYaw}") +
        0.5 *: GaussianAxisRotationProposal(rotPitch, PitchAxis, generatedBy = s"RotationPitch-${rotPitch}") +
        0.5 *: GaussianAxisRotationProposal(rotRoll, RollAxis, generatedBy = s"RotationRoll-${rotRoll}") +
        0.5 *: GaussianAxisTranslationProposal(transX, 0, generatedBy = s"TranslationX-${transX}") +
        0.5 *: GaussianAxisTranslationProposal(transY, 1, generatedBy = s"TranslationY-${transY}") +
        0.5 *: GaussianAxisTranslationProposal(transZ, 2, generatedBy = s"TranslationZ-${transZ}")
    )
    mixproposal
  }

  def mixedRandomShapeProposal(model: StatisticalMeshModel, steps: Seq[Double] = Seq(0.1))(implicit rand: scalismo.utils.Random): ProposalGeneratorWithTransition[ModelFittingParameters] = {
    val proposals = steps.map{s =>
      (0.5, RandomShapeUpdateProposal(model, s, generatedBy = s"RandomShape-${s}"))
    }
    MixtureProposal.fromProposalsWithTransition(proposals: _ *)
  }

  def mixedProposalICP(model: StatisticalMeshModel, target: TriangleMesh3D, numOfSamplePoints: Int, projectionDirection: IcpProjectionDirection = ModelAndTargetSampling, tangentialNoise: Double = 10.0, noiseAlongNormal: Double = 5.0, stepLength: Double = 0.1, boundaryAware: Boolean = true)(implicit rand: scalismo.utils.Random): ProposalGeneratorWithTransition[ModelFittingParameters] = {

    val rate = 0.5

    val modelSamplingProposals: Seq[(Double, NonRigidIcpProposal)] = Seq((rate, NonRigidIcpProposal(model, target, stepLength, tangentialNoise = tangentialNoise, noiseAlongNormal = noiseAlongNormal, numOfSamplePoints, projectionDirection = ModelSampling, boundaryAware, generatedBy = s"IcpProposal-ModelSampling-${stepLength}Step")))

    val targetSamplingProposals: Seq[(Double, NonRigidIcpProposal)] = Seq((rate, NonRigidIcpProposal(model, target, stepLength, tangentialNoise = tangentialNoise, noiseAlongNormal = noiseAlongNormal, numOfSamplePoints, projectionDirection = TargetSampling, boundaryAware, generatedBy = s"IcpProposal-TargetSampling-${stepLength}Step")))

    def proposals: Seq[(Double, NonRigidIcpProposal)] = {
      if (projectionDirection == TargetSampling) {
        targetSamplingProposals
      } else if (projectionDirection == ModelSampling) {
        modelSamplingProposals
      }
      else {
        targetSamplingProposals ++ modelSamplingProposals
      }
    }

    MixtureProposal.fromProposalsWithTransition(proposals: _ *)
  }

}
