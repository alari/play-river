organization := "ru.mirari"

name := "play-river-elasticsearch"

version := "1.0"

scalaVersion := "2.10.4"

publishTo := {
  val artifactory = "http://mvn.quonb.org/artifactory/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("Artifactory Realm" at artifactory + "plugins-snapshot-local/")
  else
    Some("Artifactory Realm" at artifactory + "plugins-release-local/")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

play.Project.playScalaSettings

resolvers += "quonb" at "http://mvn.quonb.org/repo/"

testOptions in Test += Tests.Argument("junitxml")