package api.sampling.proposals

import api.sampling.ModelFittingParameters
import scalismo.sampling.{ProposalGenerator, TransitionProbability}

sealed trait RotationAxis

case object RollAxis extends RotationAxis

case object PitchAxis extends RotationAxis

case object YawAxis extends RotationAxis


case class GaussianAxisRotationProposal(sdevRot: Double, axis: RotationAxis, generatedBy: String = "RotationProposal")
  extends ProposalGenerator[ModelFittingParameters] with TransitionProbability[ModelFittingParameters] {

  val perturbationDistr = new breeze.stats.distributions.Gaussian(0, sdevRot)

  override def propose(theta: ModelFittingParameters): ModelFittingParameters = {
    val rotParams = theta.poseParameters.rotation
    val newRotParams = axis match {
      case RollAxis => rotParams.copy(_1 = rotParams._1 + perturbationDistr.sample())
      case PitchAxis => rotParams.copy(_2 = rotParams._2 + perturbationDistr.sample())
      case YawAxis => rotParams.copy(_3 = rotParams._3 + perturbationDistr.sample())
    }

    theta.copy(poseParameters = theta.poseParameters.copy(rotation = newRotParams), generatedBy = generatedBy)
  }

  override def logTransitionProbability(from: ModelFittingParameters, to: ModelFittingParameters): Double = {
    if (to.copy(poseParameters = to.poseParameters.copy(rotation = from.poseParameters.rotation)).allParameters != from.allParameters){
      Double.NegativeInfinity
    }
    else {
      val rotParamsFrom = from.poseParameters.rotation
      val rotParamsTo = to.poseParameters.rotation
      val residual = axis match {
        case RollAxis => rotParamsTo._1 - rotParamsFrom._1
        case PitchAxis => rotParamsTo._2 - rotParamsFrom._2
        case YawAxis => rotParamsTo._3 - rotParamsFrom._3
      }
      perturbationDistr.logPdf(residual)
    }
  }
}

case class GaussianAxisTranslationProposal(sdevTrans: Double, axis: Int, generatedBy: String = "TranslationProposal")
  extends ProposalGenerator[ModelFittingParameters] with TransitionProbability[ModelFittingParameters] {

  require(axis < 3)
  val perturbationDistr = new breeze.stats.distributions.Gaussian(0, sdevTrans)

  override def propose(theta: ModelFittingParameters): ModelFittingParameters = {

    val transParams = theta.poseParameters.translation
    val newTransParams = axis match {
      case 0 => transParams.copy(x = transParams(axis) + perturbationDistr.sample())
      case 1 => transParams.copy(y = transParams(axis) + perturbationDistr.sample())
      case 2 => transParams.copy(z = transParams(axis) + perturbationDistr.sample())
    }
    theta.copy(poseParameters = theta.poseParameters.copy(translation = newTransParams), generatedBy = generatedBy)
  }

  override def logTransitionProbability(from: ModelFittingParameters, to: ModelFittingParameters): Double = {
    if (to.copy(poseParameters = to.poseParameters.copy(translation = from.poseParameters.translation)).allParameters != from.allParameters){
      Double.NegativeInfinity
    }
    else {
      val residual = to.poseParameters.translation(axis) - from.poseParameters.translation(axis)
      perturbationDistr.logPdf(residual)
    }
  }
}