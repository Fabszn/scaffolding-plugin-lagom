
organization := "com.test.plugin"

scalaVersion in ThisBuild := "2.11.7"


lazy val root = Project("root",file("."))
  .enablePlugins(LagomScaffoldingPlugin)