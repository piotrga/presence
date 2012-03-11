name := "presence"

version := "1.0"

scalaVersion := "2.9.1"

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor" % "2.0",
  "com.typesafe.akka" % "akka-remote" % "2.0",
  "com.typesafe.akka" % "akka-slf4j" % "2.0",
  "org.jgroups" % "jgroups" % "3.0.5.Final",
  "log4j" % "log4j" % "1.2.16",
  "com.typesafe.akka" % "akka-testkit" % "2.0" % "test",
  "org.apache.camel" % "camel-core" % "2.8.0",
  "org.scalatest" %% "scalatest" % "1.6.1" % "test",
  "org.mockito" % "mockito-core" % "1.9.0" % "test",
 	"junit" % "junit" % "4.5" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint")

parallelExecution in Test := true