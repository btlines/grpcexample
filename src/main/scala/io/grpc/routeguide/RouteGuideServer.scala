package io.grpc.routeguide

import java.net.URL
import java.util.logging.Logger

import io.grpc.{Server, ServerBuilder}

import scala.concurrent.ExecutionContext

class RouteGuideServer(port: Int, featureFile: URL)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger.getLogger(classOf[RouteGuideServer].getName)

  private val server: Server = {
    val service = new RouteGuideService(
      RouteGuidePersistence.parseFeatures(featureFile)
    )
    ServerBuilder
      .forPort(port)
      .addService(RouteGuideGrpc.bindService(service, ec))
      .build()
  }

  def start(): Unit = {
    server.start()
    logger.info(s"Server started, listening on $port")
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
  import scala.concurrent.ExecutionContext.Implicits.global
  val server = new RouteGuideServer(8980, RouteGuidePersistence.defaultFeatureFile)
  server.start()
  server.blockUntilShutdown()
}
