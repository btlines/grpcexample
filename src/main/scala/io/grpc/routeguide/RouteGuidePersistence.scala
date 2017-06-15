package io.grpc.routeguide

import java.net.URL
import java.util.logging.Logger

import com.trueaccord.scalapb.json.JsonFormat

import scala.io.Source

object RouteGuidePersistence {
  val logger: Logger = Logger.getLogger(getClass.getName)

  val defaultFeatureFile: URL = getClass.getClassLoader.getResource("route_guide.json")

  /**
    * Parses the JSON input file containing the list of features.
    */
  def parseFeatures(file: URL): Seq[Feature] = {
    logger.info(s"Loading features from ${file.getPath}")
    var features: Seq[Feature] = Seq.empty
    val input = file.openStream
    try {
      val source = Source.fromInputStream(input)
      try {
        features = JsonFormat.fromJsonString[PersistedFeatures](source.getLines().mkString("\n")).features
      } finally source.close()
    } finally input.close
    logger.info(s"Loaded ${features.size} features")
    features
  }

}
