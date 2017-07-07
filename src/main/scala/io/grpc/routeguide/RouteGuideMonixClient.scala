package io.grpc.routeguide

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import io.grpc.{ManagedChannelBuilder, Status}
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.Try

class RouteGuideMonixClient(host: String, port: Int) {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideMonixClient].getName)

  val channel =
    ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext(true)
      .build()

  val stub = RouteGuideGrpcMonix.stub(channel)

  def shutdown(): Unit = channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)

  import io.grpc.StatusRuntimeException

  /**
    * Non-Blocking unary call example.  Calls getFeature and prints the response.
    */
  def getFeature(lat: Int, lon: Int): Task[Unit] = {
    logger.info(s"*** GetFeature: lat=$lat lon=$lon")
    val request = Point(lat, lon)
    stub
      .getFeature(request)
      .map { feature =>
        val lat = RouteGuideService.getLatitude(feature.getLocation)
        val lon =  RouteGuideService.getLongitude(feature.getLocation)
        if (RouteGuideService.isValid(feature)) {
          logger.info(s"Found feature called '${feature.name}' at $lat, $lon")
        } else {
          logger.info(s"Found no feature at $lat, $lon")
        }
      }
      .onErrorHandle {
        case e: StatusRuntimeException =>
          logger.warning(s"RPC failed:${e.getStatus}")
          throw e
      }
  }

  import io.grpc.StatusRuntimeException

  /**
    * Non-Blocking server-streaming example. Calls listFeatures with a rectangle of interest. Prints each
    * response feature as it arrives.
    */
  def listFeatures(lowLat: Int, lowLon: Int, hiLat: Int, hiLon: Int): Task[Unit] = {
    logger.info(s"*** ListFeatures: lowLat=$lowLat lowLon=$lowLon hiLat=$hiLat hiLon=$hiLon")
    val request = Rectangle(
      lo = Some(Point(lowLat, lowLon)),
      hi = Some(Point(hiLat, hiLon))
    )
    stub
      .listFeatures(request)
      .zipWithIndex
      .map { case (feature, index) =>
        logger.info(s"Result #$index: $feature")
      }
      .onErrorHandle {
        case e: StatusRuntimeException =>
          logger.warning(s"RPC failed: ${e.getStatus}")
          throw e
      }
      .completedL
  }

  /**
    * Async client-streaming example. Sends {@code numPoints} randomly chosen points from {@code
    * features} with a variable delay in between. Prints the statistics when they are sent from the
    * server.
    */
  @throws[InterruptedException]
  def recordRoute(features: Seq[Feature], numPoints: Int): Task[Unit] = {
    logger.info("*** RecordRoute")
    stub.recordRoute(
      Observable
        .fromIterable(features.map(_.getLocation))
        .delayOnNext(100.millis)
    ).map { summary =>
      logger.info(s"Finished trip with ${summary.pointCount} points. Passed ${summary.featureCount} features. " + s"Travelled ${summary.distance} meters. It took ${summary.elapsedTime} seconds.")
    }.onErrorHandle {
      case NonFatal(e) =>
        logger.warning(s"RecordRoute Failed: ${Status.fromThrowable(e)}")
        throw e
    }
  }

  /**
    * Bi-directional example, which can only be asynchronous. Send some chat messages, and print any
    * chat messages that are sent from the server.
    */
  def routeChat: Task[Unit] = {
    logger.info("*** RouteChat")
    val requests = Seq(
      RouteNote(message = "First message", location = Some(Point(0, 0))),
      RouteNote(message = "Second message", location = Some(Point(0, 1))),
      RouteNote(message = "Third message", location = Some(Point(1, 0))),
      RouteNote(message = "Fourth message", location = Some(Point(1, 1)))
    )
    stub
      .routeChat(
        Observable
          .fromIterable(requests)
          .delayOnNext(500.millis)
          .map { request =>
            logger.info(s"Sending message '${request.message}' at ${request.getLocation.latitude}, ${request.getLocation.longitude}")
            request
          }
      ).map { note =>
      logger.info(s"Got message '${note.message}' at ${note.getLocation.latitude}, ${note.getLocation.longitude}")
    }.onErrorHandle {
      case NonFatal(e) =>
        logger.warning(s"RouteChat Failed: ${Status.fromThrowable(e)}")
        throw e
    }.completedL
  }

}

object RouteGuideMonixClient extends App {
  val logger: Logger = Logger.getLogger(classOf[RouteGuideMonixClient].getName)

  val features: Seq[Feature] = Try {
    RouteGuidePersistence.parseFeatures(RouteGuidePersistence.defaultFeatureFile)
  } getOrElse {
    logger.warning("Can't load feature list from file")
    Seq.empty
  }

  val client = new RouteGuideMonixClient("localhost", 8980)
  val task = for { // Looking for a valid feature
    _ <- client.getFeature(409146138, -746188906)
    // Feature missing.
    _ <- client.getFeature(0, 0)
    // Looking for features between 40, -75 and 42, -73.
    _ <- client.listFeatures(400000000, -750000000, 420000000, -730000000)
    // Record a few randomly selected points from the features file.
    _ <- client.recordRoute(features, 10)
    // Send and receive some notes.
    _ <- client.routeChat
  } yield ()

  Await.result(
    task.runAsync(monix.execution.Scheduler.global),
    1.minute
  )
}
