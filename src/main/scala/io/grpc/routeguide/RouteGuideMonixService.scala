package io.grpc.routeguide

import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.logging.Logger

import concurrency.AtomicRef
import monix.eval.Task
import monix.reactive.Observable

class RouteGuideMonixService(features: Seq[Feature]) extends RouteGuideGrpcMonix.RouteGuide {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideMonixService].getName)

  private val routeNotes: AtomicRef[Map[Point, Seq[RouteNote]]] = new AtomicRef(Map.empty)

  /**
    * Gets the {@link Feature} at the requested {@link Point}. If no feature at that location
    * exists, an unnamed feature is returned at the provided location.
    *
    * @param request the requested location for the feature.
    */
  override def getFeature(request: Point): Task[Feature] = {
    Task.eval(findFeature(request))
  }

  /**
    * Gets all features contained within the given bounding {@link Rectangle}.
    *
    * @param request          the bounding rectangle for the requested features.
    * @return the observable of the corresponding features.
    */
  override def listFeatures(request: Rectangle): Observable[Feature] = {
    val left = Math.min(request.getLo.longitude, request.getHi.longitude)
    val right = Math.max(request.getLo.longitude, request.getHi.longitude)
    val top = Math.max(request.getLo.latitude, request.getHi.latitude)
    val bottom = Math.min(request.getLo.latitude, request.getHi.latitude)

    Observable.fromIterable(
      features.filter { feature =>
        val lat = feature.getLocation.latitude
        val lon = feature.getLocation.longitude
        RouteGuideService.isValid(feature) && lon >= left && lon <= right && lat >= bottom && lat <= top

      }
    )
  }

  /**
    * Gets a stream of points, and responds with statistics about the "trip": number of points,
    * number of known features visited, total distance traveled, and total time spent.
    *
    * @param points an observable of the requested route points.
    * @return a Task containing the response summary.
    */
  override def recordRoute(points: Observable[Point]): Task[RouteSummary] =
    points.foldLeftL((RouteSummary.defaultInstance, None: Option[Point], System.nanoTime())) {
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
    }.map(_._1)

  /**
    * Receives a stream of message/location pairs, and responds with a stream of all previous
    * messages at each of those locations.
    *
    * @param notes an observable of requested message/location pairs.
    * @return an observable of all received messages at the notes locations
    */
  override def routeChat(notes: Observable[RouteNote]): Observable[RouteNote] =
    notes.flatMap { note =>
      addNote(note)
      Observable.fromIterable(getNotes(note.getLocation))
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


