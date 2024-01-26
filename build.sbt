ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.12"

name := "double_jointures_ks"

libraryDependencies ++=
  (Seq(
    "com.lihaoyi" %% "fastparse" % "2.2.2",
    "org.jline" % "jline-terminal" % "3.20.0",
    "org.jline" % "jline-reader" % "3.20.0",
    "io.scalaland" %% "chimney" % "0.6.1",
  ) ++ kafkaDeps ++ avro4sDeps ++ loggingDeps ++ testDeps)

resolvers ++= Seq(
  "confluent" at "https://packages.confluent.io/maven/"
)

val libVersion = new {
  val avro4s          = "4.0.0"
  val http4s          = "0.22.3"
  val kafka           = "2.8.0"
  val logback         = "1.2.3"
  val magnolia        = "0.17.0"
  val scalatest       = "3.2.5"
}
lazy val kafkaDeps = Seq(
  "org.apache.kafka"  % "kafka-streams"            % libVersion.kafka,
  "org.apache.kafka" %% "kafka-streams-scala"      % libVersion.kafka,
  "org.apache.kafka"  % "kafka-clients"            % libVersion.kafka,
  "io.confluent"      % "kafka-streams-avro-serde" % "5.3.0" exclude ("org.apache.kafka", "kafka-clients")
)
lazy val avro4sDeps = Seq(
  "com.sksamuel.avro4s" %% "avro4s-core"  % libVersion.avro4s exclude ("com.propensive", "magnolia"),
  "com.sksamuel.avro4s" %% "avro4s-kafka" % libVersion.avro4s,
  "com.propensive"      %% "magnolia"     % libVersion.magnolia
)
lazy val loggingDeps = Seq(
  "ch.qos.logback" % "logback-core"    % libVersion.logback,
  "ch.qos.logback" % "logback-classic" % libVersion.logback
)
lazy val testDepsWithoutClassifier = Seq(
  "org.scalatest"     %% "scalatest"                                                   % libVersion.scalatest,
  "org.apache.kafka"   % "kafka-streams-test-utils"                                    % libVersion.kafka,
)

lazy val testDeps = testDepsWithoutClassifier.map(_ % Test)