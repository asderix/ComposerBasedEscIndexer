val scala3Version = "3.4.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "CloudFunctionExample",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "esc" % "esc.asderix.com" % "2.2.0" from "https://esc.asderix.com/download/EscEntitySimilarityChecker_2.3.0.jar",
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.6.0-M1",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.9.0-M2",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.9.0-M2",
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.6.0-M1"
  )

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

assembly / assemblyJarName := "ComposerBasedEscIndexer.jar"
