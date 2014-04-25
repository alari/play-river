organization := "play-infra"

name := "play-river-mongo"

version := "0.1.2"

scalaVersion := "2.10.4"

libraryDependencies += "play-infra" %% "play-river" % "0.1.2"

libraryDependencies += "play-infra" %% "play-mongo" % "0.1"

resolvers += "quonb" at "http://repo.quonb.org/"

play.Project.playScalaSettings

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

publishTo := Some(Resolver.file("file",  new File( "/mvn-repo" )) )

testOptions in Test += Tests.Argument("junitxml")