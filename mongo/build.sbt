organization := "play-infra"

name := "play-river-mongo"

version := "0.3.2"

libraryDependencies += "play-infra" %% "play-river" % "0.3.0"

libraryDependencies += "play-infra" %% "play-mongo" % "0.3.1"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "quonb" at "http://repo.quonb.org/"

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