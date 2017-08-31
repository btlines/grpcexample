package io.grpc.routeguide

import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.logging.Logger

import akka.NotUsed
import akka.stream.scaladsl.Flow
import concurrency.AtomicRef

class RouteGuideAkkaStreamService(features: Seq[Feature]) extends RouteGuideGrpcAkkaStream.RouteGuide {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideAkkaStreamService].getName)

  private val routeNotes: AtomicRef[Map[Point, Seq[RouteNote]]] = new AtomicRef(Map.empty)

  /**
    * Gets the {@link Feature} at the requested {@link Point}. If no feature at that location
    * exists, an unnamed feature is returned at the provided location.
    */
  override def getFeature: Flow[Point, Feature, NotUsed] =
    Flow[Point].map(findFeature)

  /**
    * Gets all features contained within the given bounding {@link Rectangle}.
    */
  override def listFeatures: Flow[Rectangle, Feature, NotUsed] =
    Flow[Rectangle].mapConcat { rectangle =>
      val left = Math.min(rectangle.getLo.longitude, rectangle.getHi.longitude)
      val right = Math.max(rectangle.getLo.longitude, rectangle.getHi.longitude)
      val top = Math.max(rectangle.getLo.latitude, rectangle.getHi.latitude)
      val bottom = Math.min(rectangle.getLo.latitude, rectangle.getHi.latitude)
      features.filter { feature =>
        val lat = feature.getLocation.latitude
        val lon = feature.getLocation.longitude
        RouteGuideService.isValid(feature) && lon >= left && lon <= right && lat >= bottom && lat <= top
      }.to[collection.immutable.Iterable]
    }

  /**
    * Gets a stream of points, and responds with statistics about the "trip": number of points,
    * number of known features visited, total distance traveled, and total time spent.
    */
  override def recordRoute: Flow[Point, RouteSummary, NotUsed] =
    Flow[Point].fold((RouteSummary.defaultInstance, None: Option[Point], System.nanoTime())) {
      case ((summary, previous, startTime), point) =>
        val feature = findFeature(point)
        val distance = previous.map(RouteGuideService.calcDistance(_, point)) getOrElse 0
        val updated = summary.copy(
          pointCount = summary.pointCount + 1,
          featureCount = summary.featureCount + (if (RouteGuideService.isValid(feature)) 1 else 0),
          distance = summary.distance + distance,
          elapsedTime = NANOSECONDS.toSeconds(System.nanoTime() - startTime).toInt
        )
        (updated, Some(point), startTime)
    }
    .map(_._1)

  /**
    * Receives a stream of message/location pairs, and responds with a stream of all previous
    * messages at each of those locations.
    */
  override def routeChat: Flow[RouteNote, RouteNote, NotUsed] =
    Flow[RouteNote].mapConcat { note =>
      addNote(note)
      getNotes(note.getLocation).to[collection.immutable.Iterable]
    }

  private def findFeature(point: Point): Feature = {
    features.find { feature =>
      feature.getLocation.latitude == point.latitude && feature.getLocation.longitude == point.longitude
    } getOrElse new Feature(location = Some(point))
  }

  private def getNotes(point: Point): Seq[RouteNote] = {
    routeNotes.get.getOrElse(point, Seq.empty)
  }

  private def addNote(note: RouteNote): Unit = {
    routeNotes.updateAndGet { notes =>
      val existingNotes = notes.getOrElse(note.getLocation, Seq.empty)
      val updatedNotes = existingNotes :+ note
      notes + (note.getLocation -> updatedNotes)
    }
  }
}


