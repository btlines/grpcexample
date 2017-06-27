package io.grpc.routeguide

import java.util.logging.Logger

import grpcgateway.server.GrpcGatewayServerBuilder
import io.grpc.ManagedChannelBuilder

import scala.concurrent.ExecutionContext
import scala.sys.ShutdownHookThread

class RouteGuideRestGateway(port: Int, grpcHost: String, grpcPort: Int)(implicit ec: ExecutionContext) {
  private val logger: Logger = Logger.getLogger(classOf[RouteGuideServer].getName)

  private val channel = ManagedChannelBuilder
    .forAddress(grpcHost, grpcPort)
    .usePlaintext(true)
    .build()

  private val gateway = GrpcGatewayServerBuilder
    .forPort(port)
    .addService(new RouteGuideHandler(channel))
    .build()

  private var shutdownHook: Option[ShutdownHookThread] = None

  def start(): Unit = {
    gateway.start()
    logger.info(s"GRPC Gateway started, listening on $port")
    shutdownHook = Option(
      sys.addShutdownHook {
        // Use stderr here since the logger may has been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC gateway since JVM is shutting down")
        stop()
        System.err.println("*** gRPC Gateway shut down")
      }
    )
  }

  def stop(): Unit = gateway.shutdown()

  def blockUntilShutdown(): Unit = shutdownHook.foreach(_.join())
}

object RouteGuideRestGateway extends App {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Expects GRPC server to be running on localhost:8980
  val gateway = new RouteGuideRestGateway(8981, "localhost", 8980)

  gateway.start()
  gateway.blockUntilShutdown()
}
