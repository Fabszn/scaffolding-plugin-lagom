import bintray.Keys._

lazy val commonSettings = Seq(
  organization in ThisBuild := "com.lightbend.lagom.sbt"
)

lazy val root = (project in file(".")).
  settings(commonSettings ++ bintrayPublishSettings: _*).
  settings(
    sbtPlugin := true,
    name := "scaffolding-plugin-lagom",
    description := "desc",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    publishMavenStyle := false,
    repository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None
  )