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

package api.sampling.loggers

import java.io._
import java.text.SimpleDateFormat
import java.util.Calendar

import api.sampling.{ModelFittingParameters, PoseParameters, ShapeParameters}
import breeze.linalg.DenseVector
import scalismo.geometry._
import scalismo.sampling.loggers.AcceptRejectLogger
import scalismo.sampling.{DistributionEvaluator, ProposalGenerator}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.SortedSet
import scala.collection.mutable.ListBuffer
import scala.io.Source

case class jsonLogFormat(index: Int, name: String, logvalue: Map[String, Double], status: Boolean, rigid: Seq[Double], coeff: Seq[Double], datetime: String)


object JsonLoggerProtocol {
  implicit val myJsonFormatLogger: RootJsonFormat[jsonLogFormat] = jsonFormat7(jsonLogFormat.apply)
}

case class JSONAcceptRejectLogger[A](filePath: File, evaluators: Option[Map[String, DistributionEvaluator[ModelFittingParameters]]] = None) extends AcceptRejectLogger[A] {

  import JsonLoggerProtocol._

  private var numOfRejected: Int = 0
  private var numOfAccepted: Int = 0
  private var generatedBy: SortedSet[String] = SortedSet()
  private val datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  println(s"JSONAcceptRejectLogger parent file: ${filePath.getParentFile}")
  if (filePath.getParentFile != null) {
    if (!filePath.getParentFile.exists()) {
      throw new IOException(s"JSON log path does not exist: ${filePath.getParentFile.toString}!")
    }
    else if (filePath.exists() && !filePath.canWrite) {
      throw new IOException(s"JSON file exist and cannot be overwritten: ${filePath.toString}!")
    }
    else if (!filePath.exists()) {
      try {
        filePath.createNewFile()
        filePath.delete()
      }
      catch {
        case e: Exception => throw new IOException(s"JSON file path cannot be written to: ${filePath.toString}!")
      }
    }
  }
  filePath.setReadable(true, false)
  filePath.setExecutable(true, false)
  filePath.setWritable(true, false)

  def totalSamples: Int = numOfRejected + numOfAccepted

  val logSamples: ListBuffer[ModelFittingParameters] = new ListBuffer[ModelFittingParameters]
  val logStatus: ListBuffer[jsonLogFormat] = new ListBuffer[jsonLogFormat]

  private def identifier(sample: A): String = {
    val name = sample.asInstanceOf[ModelFittingParameters].generatedBy
    generatedBy += name
    name
  }

  private def mapEvaluators(sample: ModelFittingParameters, default: Double): Map[String, Double] = {
    if (evaluators.isDefined) {
      evaluators.get.map { case (name, eval) => (name, eval.logValue(sample)) }
    }
    else {
      Map("product" -> default)
    }
  }

  override def accept(current: A, sample: A, generator: ProposalGenerator[A], evaluator: DistributionEvaluator[A]): Unit = {
    val locSample = sample.asInstanceOf[ModelFittingParameters]
    val evalValue = mapEvaluators(locSample, evaluator.logValue(sample))
    logStatus += jsonLogFormat(totalSamples, identifier(sample), evalValue, true, locSample.poseParameters.parameters.toArray.toSeq, locSample.shapeParameters.parameters.toArray.toSeq, datetimeFormat.format(Calendar.getInstance().getTime()))
    logSamples += locSample
    numOfAccepted += 1
  }

  override def reject(current: A, sample: A, generator: ProposalGenerator[A], evaluator: DistributionEvaluator[A]): Unit = {
    val locCurrent = current.asInstanceOf[ModelFittingParameters]
    val evalValue = mapEvaluators(locCurrent, evaluator.logValue(current))
    logStatus += jsonLogFormat(totalSamples, identifier(sample), evalValue, false, Seq(), Seq(), datetimeFormat.format(Calendar.getInstance().getTime())) // locCurrent.poseParameters.parameters.toArray.toSeq, locCurrent.shapeParameters.parameters.toArray.toSeq
    numOfRejected += 1
  }

  def percentRejected: Double = BigDecimal(numOfRejected.toDouble / totalSamples.toDouble).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  def percentAccepted: Double = 1.0 - percentRejected

  def writeLog(): Unit = {
    val content = logStatus.toIndexedSeq
    try {
      val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toString)))
      writer.write(content.toList.toJson.prettyPrint)
      writer.close()
    } catch {
      case e: Exception => throw new IOException("Writing JSON log file failed!")
    }
    println("Log written to: " + filePath.toString)
  }

  def loadLog(): IndexedSeq[jsonLogFormat] = {
    println(s"Loading JSON log file: ${filePath.toString}")
    Source.fromFile(filePath.toString).mkString.parseJson.convertTo[IndexedSeq[jsonLogFormat]]
  }

  def prettyPrint: String = {
    logStatus.toIndexedSeq.toList.toJson.prettyPrint
  }

  def sampleToModelParameters(sample: jsonLogFormat): ModelFittingParameters = {
    val r = sample.rigid.toIndexedSeq
    val translation: EuclideanVector[_3D] = EuclideanVector3D(r(0), r(1), r(2))
    val rotation: (Double, Double, Double) = (r(3), r(4), r(5))
    val center: Point[_3D] = Point3D(r(6), r(7), r(8))

    ModelFittingParameters(poseParameters = PoseParameters(translation, rotation, center), shapeParameters = ShapeParameters(DenseVector[Double](sample.coeff.toArray)))
  }

  def getBestFittingParsFromJSON: ModelFittingParameters = {
    val loggerSeq = loadLog().filter(_.status)
    val bestSample = loggerSeq.sortBy(f => f.logvalue("product")).reverse.head
    sampleToModelParameters(bestSample)
  }

  def printAcceptInfo(id: String = ""): Unit = {
    println(s"${id} Total accepted (${totalSamples}): ${percentAccepted}")
    generatedBy.foreach { name =>
      println(s"${id} ${name}: ${percentAcceptedOfType(name)}")
    }
    val logLast100 = logStatus.takeRight(100)
    println(s"${id} Last 100 samples accepted (${100}): ${logLast100.map(f => if (f.status) 1.0 else .0).sum / 100.0}")
    generatedBy.foreach { name =>
      println(s"${id} ${name}: ${percentAcceptedOfTypeLocal(name, logLast100)}")
    }
  }

  def percentAcceptedOfType(id: String): Double = {
    val filtered = logStatus.filter(f => f.name == id)
    val accepted = filtered.filter(f => f.status)
    accepted.length.toDouble / filtered.length.toDouble
  }

  def percentAcceptedOfTypeLocal(id: String, localLog: ListBuffer[jsonLogFormat]): Double = {
    val filtered = localLog.filter(f => f.name == id)
    val accepted = filtered.filter(f => f.status)
    accepted.length.toDouble / filtered.length.toDouble
  }

  override def toString: String = {
    "# of Accepted: " + numOfAccepted + " = " + percentAccepted + "%\n" +
      "# of Rejected: " + numOfRejected + " = " + percentRejected + "%"
  }
}

object JSONAcceptRejectLogger {
  val log = new JSONAcceptRejectLogger(new File(""))

  def sampleToModelParameters(sample: jsonLogFormat): ModelFittingParameters = log.sampleToModelParameters(sample)
}
