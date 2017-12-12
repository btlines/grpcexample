scalaVersion := "2.12.4"

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value,
  // generate Swagger spec files into the `resources/specs`
  grpcgateway.generators.SwaggerGenerator -> (resourceDirectory in Compile).value / "specs",
  // generate the Rest Gateway source code
  grpcgateway.generators.GatewayGenerator -> (sourceManaged in Compile).value,
  // generate the reactive (monix) files
  grpcmonix.generators.GrpcMonixGenerator() -> (sourceManaged in Compile).value,
  // generate the akka stream files
  grpc.akkastreams.generators.GrpcAkkaStreamGenerator() -> (sourceManaged in Compile).value
)

val scalapbVersion = com.trueaccord.scalapb.compiler.Version.scalapbVersion

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "scalapb-runtime"       % scalapbVersion % "protobuf",
  // for gRPC
  "io.grpc"                %  "grpc-netty"            % "1.8.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"  % scalapbVersion,
  // for JSON conversion
  "com.trueaccord.scalapb" %% "scalapb-json4s"        % "0.3.0",
  // for GRPC Gateway
  "beyondthelines"         %% "grpcgatewayruntime"    % "0.0.6" % "compile,protobuf",
  // for GRPC Monix
  "beyondthelines"         %% "grpcmonixruntime"      % "0.0.5",
  // for GRPC Akkastream
  "beyondthelines"         %% "grpcakkastreamruntime" % "0.0.5"
)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

