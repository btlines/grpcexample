package io.grpc.routeguide

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Sink, Source}
import io.grpc.{ManagedChannelBuilder, Status}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.Try

class RouteGuideAkkaStreamClient(host: String, port: Int) {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideAkkaStreamClient].getName)

  val channel =
    ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext(true)
      .build()

  val stub = RouteGuideGrpcAkkaStream.stub(channel)

  def shutdown(): Unit = channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)

  import io.grpc.StatusRuntimeException

  /**
    * Non-Blocking unary call example.  Calls getFeature and prints the response.
    */
  def getFeature(lat: Int, lon: Int): Source[String, NotUsed] = {
    logger.info(s"*** GetFeature: lat=$lat lon=$lon")
    val request = Point(lat, lon)
    Source
      .single(request)
      .via(stub.getFeature)
      .map { feature =>
        val lat = RouteGuideService.getLatitude(feature.getLocation)
        val lon =  RouteGuideService.getLongitude(feature.getLocation)
        if (RouteGuideService.isValid(feature)) s"Found feature called '${feature.name}' at $lat, $lon"
        else s"Found no feature at $lat, $lon"
      }
  }

  /**
    * Non-Blocking server-streaming example. Calls listFeatures with a rectangle of interest. Prints each
    * response feature as it arrives.
    */
  def listFeatures(lowLat: Int, lowLon: Int, hiLat: Int, hiLon: Int): Source[String, NotUsed] = {
    logger.info(s"*** ListFeatures: lowLat=$lowLat lowLon=$lowLon hiLat=$hiLat hiLon=$hiLon")
    val request = Rectangle(
      lo = Some(Point(lowLat, lowLon)),
      hi = Some(Point(hiLat, hiLon))
    )
    Source
      .single(request)
      .via(stub.listFeatures)
      .zipWithIndex
      .map { case (feature, index) =>
        s"Result #$index: $feature"
      }
  }

  /**
    * Async client-streaming example. Sends {@code numPoints} randomly chosen points from {@code
    * features} with a variable delay in between. Prints the statistics when they are sent from the
    * server.
    */
  def recordRoute(features: Seq[Feature], numPoints: Int): Source[String, NotUsed] = {
    logger.info("*** RecordRoute")
    Source(features.take(numPoints).to[collection.immutable.Iterable].map(_.getLocation))
      .throttle(1, 500.millis, 1, ThrottleMode.shaping)
      .via(stub.recordRoute)
      .map { summary =>
        List(
          s"Finished trip with ${summary.pointCount} points.",
          s"Passed ${summary.featureCount} features.",
          s"Travelled ${summary.distance} meters.",
          s"It took ${summary.elapsedTime} seconds."
        ).mkString("\n")
      }
  }

  /**
    * Bi-directional example, which can only be asynchronous. Send some chat messages, and print any
    * chat messages that are sent from the server.
    */
  def routeChat: Source[String, NotUsed] = {
    logger.info("*** RouteChat")
    val requests = Seq(
      RouteNote(message = "First message", location = Some(Point(0, 0))),
      RouteNote(message = "Second message", location = Some(Point(0, 1))),
      RouteNote(message = "Third message", location = Some(Point(1, 0))),
      RouteNote(message = "Fourth message", location = Some(Point(1, 1)))
    )
    Source(requests.to[collection.immutable.Iterable])
      .throttle(1, 100.millis, 1, ThrottleMode.shaping)
      .via(stub.routeChat)
      .map { note =>
        s"=> Got message '${note.message}' at (${note.getLocation.latitude}, ${note.getLocation.longitude})"
      }
  }

}

object RouteGuideAkkaStreamClient extends App {
  val logger: Logger = Logger.getLogger(classOf[RouteGuideAkkaStreamClient].getName)

  val features: Seq[Feature] = Try {
    RouteGuidePersistence.parseFeatures(RouteGuidePersistence.defaultFeatureFile)
  } getOrElse {
    logger.warning("Can't load feature list from file")
    Seq.empty
  }

  implicit val system = ActorSystem(classOf[RouteGuideAkkaStreamClient].getSimpleName)
  implicit val materializer = ActorMaterializer.create(system)
  val client = new RouteGuideAkkaStreamClient("localhost", 8980)
  client.getFeature(409146138, -746188906).runForeach(println)
  client.getFeature(0, 0).runForeach(println)
  client.listFeatures(400000000, -750000000, 420000000, -730000000).runForeach(println)
  client.recordRoute(features, 10).runForeach(println)
  client.routeChat.runForeach(println)
}
