addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.9")

resolvers += Resolver.bintrayRepo("beyondthelines", "maven")

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "compilerplugin"       % "0.6.0-pre5",
  "beyondthelines"         %% "grpcgatewaygenerator" % "0.0.1",
  "beyondthelines"         %% "grpcmonixgenerator"   % "0.0.1"
)

