organization := "play-infra"

name := "play-river"

version := "0.3.0"

crossScalaVersions := Seq("2.10.4", "2.11.1")

lazy val root = (project in file(".")).enablePlugins(play.PlayScala)

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