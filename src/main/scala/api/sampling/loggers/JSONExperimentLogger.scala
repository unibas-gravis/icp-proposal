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

import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.mutable.ListBuffer
import scala.io.Source

case class jsonExperimentFormat(index: Int, modelPath: String, targetPath: String, samplingEuclideanLoggerPath: String, samplingHausdorffLoggerPath: String, coeffInit: Seq[Double], coeffSamplingEuclidean: Seq[Double], coeffSamplingHausdorff: Seq[Double], coeffIcp: Seq[Double],
                                samplingEuclidean: Map[String, Double], samplingHausdorff: Map[String, Double], icp: Map[String, Double], numOfEvaluationPoints: Int, numOfSamplePoints: Int, normalNoise: Double, datetime: String, comment: String)


object JsonExperimentProtocol {
  implicit val myJsonFormatExperiment: RootJsonFormat[jsonExperimentFormat] = jsonFormat17(jsonExperimentFormat.apply)
}

case class JSONExperimentLogger(filePath: File, modelPath: String = "") {

  import JsonExperimentProtocol._

  private val datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

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

  filePath.setReadable(true, false)
  filePath.setExecutable(true, false)
  filePath.setWritable(true, false)

  val experiments: ListBuffer[jsonExperimentFormat] = new ListBuffer[jsonExperimentFormat]

  def append(index: Int, targetPath: String = "", samplingEuclideanLoggerPath: String = "", samplingHausdorffLoggerPath: String = "", coeffInit: Seq[Double], coeffSamplingEuclidean: Seq[Double], coeffSamplingHausdorff: Seq[Double], coeffIcp: Seq[Double], samplingEuclidean: Map[String, Double], samplingHausdorff: Map[String, Double], icp: Map[String, Double], numOfEvaluationPoints: Int, numOfSamplePoints: Int, normalNoise: Double, comment: String): Unit = {
    experiments += jsonExperimentFormat(index = index, modelPath = modelPath, targetPath = targetPath, samplingEuclideanLoggerPath = samplingEuclideanLoggerPath, samplingHausdorffLoggerPath = samplingHausdorffLoggerPath, coeffInit = coeffInit, coeffSamplingEuclidean = coeffSamplingEuclidean, coeffSamplingHausdorff = coeffSamplingHausdorff, coeffIcp = coeffIcp,
      samplingEuclidean = samplingEuclidean, samplingHausdorff = samplingHausdorff, icp = icp, numOfEvaluationPoints = numOfEvaluationPoints, numOfSamplePoints = numOfSamplePoints, normalNoise = normalNoise, datetime = datetimeFormat.format(Calendar.getInstance().getTime()), comment = comment)
  }

  def writeLog(): Unit = {
    val content = experiments.toIndexedSeq
    try {
      val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toString)))
      writer.write(content.toList.toJson.prettyPrint)
      writer.close()
    } catch {
      case e: Exception => throw new IOException("Writing JSON log file failed!")
    }
    println("Log written to: " + filePath.toString)
  }

  def loadLog(): IndexedSeq[jsonExperimentFormat] = {
    println(s"Loading JSON log file: ${filePath.toString}")
    Source.fromFile(filePath.toString).mkString.parseJson.convertTo[IndexedSeq[jsonExperimentFormat]]
  }
}

