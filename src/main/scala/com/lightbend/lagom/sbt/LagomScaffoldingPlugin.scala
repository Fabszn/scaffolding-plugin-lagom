package com.lightbend.lagom.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser


/**
  * Created by fsznajderman on 29/06/16.
  */
object LagomScaffoldingPlugin extends AutoPlugin {


  private def rootProjectFilter(root: URI, currentUri: URI): Boolean = {

    root == currentUri

  }

  //definition of parser

  val optionnalParam: Parser[(Option[String], Option[Boolean])] = (Space ~> "org:" ~> StringBasic).? ~ (Space ~> "isTemplate:" ~> Bool).?
  val cmdParser: Parser[(String, (Option[String], Option[Boolean]))] =
    (Space ~> (StringBasic ~ optionnalParam) !!! ("Command should looks like : newService <name of service> [org:name of organisation][isTemplate:true || false]"))

  object autoImport {
    val newJavaService = inputKey[Unit]("Create new Lagom service based on Java")
    val newScalaService = inputKey[Unit]("Create new Lagom service based on Scala")
  }

  case class Inputs(serviceName: String, packName: String, template: Boolean)


  def paramExtractor(serviceInfo: (String, (Option[String], Option[Boolean])), organisation: String)(implicit log: Logger): Inputs = {
    val (cmd, (org, template)) = serviceInfo
    val packName = org.getOrElse(Option(organisation).getOrElse(""))
    if (packName.isEmpty) log.warn("No package has been defined")
    val isTemplate: Boolean = template.getOrElse(false)
    Inputs(cmd, packName, isTemplate)
  }

  def computeProjectDeclaration(name: String, template: Boolean): String = {
    if (template) {
      "project(\"" + name + "\")"
    } else {
      "(project in file(\"" + name + "\"))"
    }

  }

  import autoImport._

  override def trigger = allRequirements

  override def projectSettings = {
    Seq(newJavaService := {
      implicit val log = streams.value.log
      buildService(paramExtractor(cmdParser.parsed, organization.value))

      def buildService(input: Inputs): Unit = {
        val bs = buildStructure.value
        if (rootProjectFilter(bs.root, thisProject.value.base.toURI)) {
          val sourceDir = "src/main/java"
          val resourceDir = "src/main/resources"
          log.info(managePack(input.packName))
          val apiDir = baseDirectory.value / ((input.serviceName + "-api") + "/" + sourceDir + "/" + managePack(input.packName))
          val implDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/" + managePack(input.packName))
          val confDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + resourceDir)

          //create directories
          IO.createDirectories(List(apiDir, implDir))
          createInterfaceFile(input.packName, input.serviceName, apiDir)
          createImplFiles(input.packName, input.serviceName.capitalize, implDir)
          createConfFile(input.packName, input.serviceName, confDir)
          addServiceConfToBuild(baseDirectory.value, input)
          log.info("Lagom(Java) has been generated successfully")
        }
      }

      def managePack(packName: String): String = packName.replace(".", "/")

      def createInterfaceFile(packName: String, name: String, dir: File): Unit = {

        val interface =
          s"""
             |/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName ;
             |
             |import com.lightbend.lagom.javadsl.api.Descriptor;
             |import com.lightbend.lagom.javadsl.api.Service;
             |import static com.lightbend.lagom.javadsl.api.Service.*;
             |
             |public interface ${name.capitalize}Service extends Service {
             |
             |    @Override
             |    default Descriptor descriptor() {
             |        return named("$name");
             |    }
             |}
        """.stripMargin

        IO.append(dir / s"${name.capitalize}Service.java", interface)
      }

      def createImplFiles(packName: String, name: String, dir: File): Unit = {
        val implementation =
          s"""/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName;
             |
              |import $packName.${name}Service;
             |
              |
              |/**
             | * Implementation of the ${name}Service.
             | */
             |public class ${name}ServiceImpl implements ${name}Service {
             |
              |    //TODO implement service interface
             |
              |}
             |""".stripMargin

        val module =
          s"""package $packName;
             |
              |import com.google.inject.AbstractModule;
             |import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
             |import $packName.${name}Service;
             |import $packName.${name}ServiceImpl;
             |
              |/**
             | * The module that binds the ${name}ServiceModule so that it can be served.
             | */
             |public class ${name}ServiceModule extends AbstractModule implements ServiceGuiceSupport {
             |  @Override
             |  protected void configure() {
             |    bindServices(serviceBinding(${name}Service.class, ${name}ServiceImpl.class));
             |  }
             |}""".stripMargin

        IO.append(dir / s"${name}ServiceImpl.java", implementation)
        IO.append(dir / s"${name}ServiceModule.java", module)

      }

      def addServiceConfToBuild(dir: File, input: Inputs) = {
        val sbtConf =
          s"""
             |
             |//${input.serviceName} service
             |lazy val ${input.serviceName}Api = ${computeProjectDeclaration(input.serviceName + "-api", input.template)}
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies += lagomJavadslApi
             |  )
             |
             |lazy val ${input.serviceName}Impl = ${computeProjectDeclaration(input.serviceName + "-impl", input.template)}
             |  .enablePlugins(LagomJava)
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies ++= Seq(
             |      lagomJavadslPersistence,
             |      lagomJavadslTestKit
             |    )
             |  )
             |  .settings(lagomForkedTestSettings: _*)
             |  .dependsOn("${input.serviceName}Api")""".stripMargin

        IO.append(dir / ("build.sbt"), sbtConf)
      }


      def createConfFile(packName: String, name: String, dir: File): Unit = {

        val applicationConf =
          s"""#
             |# Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             |#
             |play.modules.enabled += $packName.${name.capitalize}ServiceModule
             |""".stripMargin

        IO.append(dir / ("application.conf"), applicationConf)
      }


    }, newScalaService := {
      implicit val log = streams.value.log

      buildService(paramExtractor(cmdParser.parsed, organization.value))

      def buildService(input: Inputs): Unit = {

        val bs = buildStructure.value
        if (rootProjectFilter(bs.root, thisProject.value.base.toURI)) {
          if (input.packName.isEmpty) log.warn("No package has been defined")
          val sourceDir = "src/main/scala"
          val resourceDir = "src/main/resources"
          log.info(managePackageName(input.packName))
          log.info(baseDirectory.value.toString)
          val apiDir = baseDirectory.value / ((input.serviceName + "-api") + "/" + sourceDir + "/" + managePackageName(input.packName))
          val implDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/" + managePackageName(input.packName))
          val confDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + resourceDir)

          //create directories
          IO.createDirectories(List(apiDir, implDir))
          createInterfaceFile(input.packName, input.serviceName, apiDir)
          createImplFiles(input.packName, input.serviceName.capitalize, implDir)
          createConfFile(input.packName, input.serviceName, confDir)
          //createConverterFile(baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/"))
          addServiceConfToBuild(baseDirectory.value, input)
          log.info("Lagom(Scala) has been generated successfully")
        }
      }

      def managePackageName(packName: String): String = packName.replace(".", "/")

      def createInterfaceFile(packName: String, name: String, dir: File): Unit = {

        val interface =
          s"""
             |/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName
             |
             |import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
             |import akka.NotUsed
             |
             |
             |trait ${name.capitalize}Service extends Service {
             |
             |  def sample: ServiceCall[NotUsed, String]
             |
             | override final def descriptor = {
             |   import Service._
             |    named("${name.capitalize}").withCalls(
             |         namedCall("/api/sample", sample _).withAutoAcl(true)
             |      )
             |    }
             |}
        """.stripMargin

        IO.append(dir / (s"${name.capitalize}Service.scala"), interface)
      }

      def createImplFiles(packName: String, name: String, dir: File): Unit = {
        val loader =
          s"""
             |package $packName
             |
            |import com.lightbend.lagom.scaladsl.api.ServiceLocator
             |import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
             |import com.lightbend.lagom.scaladsl.server._
             |import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
             |import play.api.libs.ws.ahc.AhcWSComponents
             |import com.softwaremill.macwire._
             |
            |class ${name}Loader extends LagomApplicationLoader {
             |
            |  override def load(context: LagomApplicationContext): LagomApplication =
             |    new ${name}Application(context) {
             |      override def serviceLocator: ServiceLocator = NoServiceLocator
             |    }
             |
            |  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
             |    new ${name}Application(context) with LagomDevModeComponents
             |
            |  override def describeServices = List(
             |    readDescriptor[${name}Service]
             |  )
             |}
             |
            |abstract class ${name}Application(context: LagomApplicationContext)
             |  extends LagomApplication(context)
             |    with AhcWSComponents {
             |
            |  // Bind the service that this server provides
             |  override lazy val lagomServer = serverFor[${name}Service](wire[${name}ServiceImpl])
             |
             |
            |}
             |
          """.stripMargin

        val implementation =
          s"""/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName
             |
             |import akka.NotUsed
             |import com.lightbend.lagom.scaladsl.api.ServiceCall
             |
             |import scala.concurrent.Future
             |
             |class ${name}ServiceImpl  extends ${name}Service {
             |
             |override def sample: ServiceCall[NotUsed, String] = _ => Future.successful("sample")
             |
             |}
             |
            |""".stripMargin


        IO.append(dir / (s"${name.capitalize}ServiceImpl.scala"), implementation)
        IO.append(dir / (s"${name.capitalize}Loader.scala"), loader)


      }

      def createConfFile(packName: String, name: String, dir: File): Unit = {

        val applicationConf =
          s"""#
             |# Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             |#
             |play.application.loader = $packName.${name.capitalize}Loader
             |""".stripMargin

        IO.append(dir / ("application.conf"), applicationConf)
      }

      def addServiceConfToBuild(dir: File, input: Inputs) = {
        val sbtConf =
          s"""
             |
             |
             |//${input.serviceName} service
             |lazy val ${input.serviceName}Api = ${computeProjectDeclaration(input.serviceName + "-api", input.template)}
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies += lagomScaladslApi
             |  )
             |
             |lazy val ${input.serviceName}Impl = ${computeProjectDeclaration(input.serviceName + "-impl", input.template)}
             |  .enablePlugins(LagomScala)
             |  .settings(
             |    scalacOptions in Compile += "-Xexperimental", // this enables Scala lambdas to be passed as Java SAMs
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies ++= Seq(
             |      "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
             |    )
             |  )
             |  .settings(lagomForkedTestSettings: _*)
             |  .dependsOn(${input.serviceName}Api)""".stripMargin

        IO.append(dir / ("build.sbt"), sbtConf)
      }


    })

  }


}
