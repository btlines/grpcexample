scalaVersion := "2.12.2"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "scalapb-runtime"      % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf",
  // for gRPC
  "io.grpc"                %  "grpc-netty"           % "1.4.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
  // for JSON conversion
  "com.trueaccord.scalapb" %% "scalapb-json4s"       % "0.3.0"
)


