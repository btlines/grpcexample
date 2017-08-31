package io.grpc.routeguide

import java.util.logging.Logger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.grpc.{Server, ServerBuilder}

class RouteGuideServer(server: Server) {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideServer].getName)

  def start(): Unit = {
    server.start()
    logger.info(s"Server started, listening on ${server.getPort}")
    sys.addShutdownHook {
      // Use stderr here since the logger may has been reset by its JVM shutdown hook.
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      stop()
      System.err.println("*** server shut down")
    }
    ()
  }

  def stop(): Unit = {
    server.shutdown()
  }

  /**
    * Await termination on the main thread since the grpc library uses daemon threads.
    */
  def blockUntilShutdown(): Unit = {
    server.awaitTermination()
  }
}

object RouteGuideServer extends App {
  val features = RouteGuidePersistence.parseFeatures(
    Thread.currentThread.getContextClassLoader.getResource("route_guide.json")
  )

  val server = new RouteGuideServer(
    ServerBuilder
      .forPort(8980)
      .addService(
        RouteGuideGrpc.bindService(
          new RouteGuideService(features),
          scala.concurrent.ExecutionContext.global
        )
      )
      .build()
  )
  server.start()
  server.blockUntilShutdown()
}

object RouteGuideMonixServer extends App {
  val features = RouteGuidePersistence.parseFeatures(
    Thread.currentThread.getContextClassLoader.getResource("route_guide.json")
  )
  val server = new RouteGuideServer(
    ServerBuilder
      .forPort(8980)
      .addService(
        RouteGuideGrpcMonix.bindService(
          new RouteGuideMonixService(features),
          monix.execution.Scheduler.global
        )
      )
      .build()
  )
  server.start()
  server.blockUntilShutdown()
}

object RouteGuideAkkaStreamServer extends App {
  val features = RouteGuidePersistence.parseFeatures(
    Thread.currentThread.getContextClassLoader.getResource("route_guide.json")
  )
  val system = ActorSystem("RouteGuideAkkaStreamServer")
  implicit val materializer = ActorMaterializer.create(system)

  val server = new RouteGuideServer(
    ServerBuilder
      .forPort(8980)
      .addService(
        RouteGuideGrpcAkkaStream.bindService(
          new RouteGuideAkkaStreamService(features)
        )
      )
      .build()
  )
  server.start()
  server.blockUntilShutdown()
  system.terminate()
}
