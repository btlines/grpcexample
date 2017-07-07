scalaVersion := "2.12.2"

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value,
  // generate Swagger spec files into the `resources/specs`
  grpcgateway.generators.SwaggerGenerator -> (resourceDirectory in Compile).value / "specs",
  // generate the Rest Gateway source code
  grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value,
  // generate the reactive (monix) files
  grpcmonix.generators.GrpcMonixGenerator -> (sourceManaged in Compile).value
)

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "scalapb-runtime"      % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf",
  // for gRPC
  "io.grpc"                %  "grpc-netty"           % "1.4.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
  // for JSON conversion
  "com.trueaccord.scalapb" %% "scalapb-json4s"       % "0.3.0",
  // for GRPC Gateway
  "beyondthelines"         %% "grpcgatewayruntime"   % "0.0.1" % "compile,protobuf",
  // for GRPC Monix
  "beyondthelines"         %% "grpcmonixruntime"     % "0.0.0"
)


