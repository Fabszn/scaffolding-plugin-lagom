package com.lightbend.lagom.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser


/**
  * Created by fsznajderman on 29/06/16.
  */
object LagomScaffoldingPlugin extends AutoPlugin {

  //definition of parser

  val optionnalParam: Parser[(Option[String], Option[Boolean])] = (Space ~> "org:" ~> StringBasic).? ~ (Space ~> "template:" ~> Bool).?
  val cmdParser: Parser[(String, (Option[String], Option[Boolean]))] =
    (Space ~> (StringBasic ~ optionnalParam) !!! ("Command should looks like : newService <name of service> [org:name of organisation]"))


  val newJavaService = inputKey[Unit]("Create new Lagom service based on Java")
  val newScalaService = inputKey[Unit]("Create new Lagom service based on Scala")

  case class Inputs(serviceName: String, packName: String, template: Boolean)


  def paramExtractor(serviceInfo: (String, (Option[String], Option[Boolean])), organisation: String)(implicit log: Logger): Inputs = {
    val (cmd, (org, template)) = serviceInfo
    val packName = org.getOrElse(Option(organisation).getOrElse(""))
    if (packName.isEmpty) log.warn("No package has been defined")
    val isTemplate: Boolean = template.getOrElse(false)
    Inputs(cmd, packName, isTemplate)
  }

  def addServiceConfToBuild(name: String, dir: File) = {
    val sbtConf =
      s"""
         |
           |//${name} service
         |lazy val ${name}Api = (project in file("$name-api"))
         |  .settings(
         |    version := "1.0-SNAPSHOT",
         |    libraryDependencies += lagomJavadslApi
         |  )
         |
              |lazy val ${name}Impl = (project in file("$name-impl"))
         |  .enablePlugins(LagomJava)
         |  .settings(
         |    scalacOptions in Compile += "-Xexperimental", // this enables Scala lambdas to be passed as Java SAMs
         |    version := "1.0-SNAPSHOT",
         |    libraryDependencies ++= Seq(
         |      lagomJavadslPersistence,
         |      lagomJavadslTestKit
         |    )
         |  )
         |  .settings(lagomForkedTestSettings: _*)
         |  .dependsOn("${name}Api")""".stripMargin

    IO.append(dir / ("build.sbt"), sbtConf)
  }



  override def projectSettings = {
    Seq(newJavaService := {
      implicit val log = streams.value.log
      buildService(paramExtractor(cmdParser.parsed, organization.value))

      def buildService(input: Inputs): Unit = {

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
        addServiceConfToBuild(input.serviceName, baseDirectory.value)
        log.info("Lagom(Java) has been generated successfully")
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

        IO.append(dir / (s"${name.capitalize}Service.java"), interface)
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

        IO.append(dir / (s"${name}ServiceImpl.java"), implementation)
        IO.append(dir / (s"${name}ServiceModule.java"), module)

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


        if (input.packName.isEmpty) log.warn("No package has been defined")
        val sourceDir = "src/main/scala"
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
        createConverterFile(baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/"))
        addServiceConfToBuild(input.serviceName, baseDirectory.value)
        log.info("Lagom(Scala) has been generated successfully")
      }

      def managePack(packName: String): String = packName.replace(".", "/")

      def createInterfaceFile(packName: String, name: String, dir: File): Unit = {

        val interface =
          s"""
             |/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName
             |
           |import akka.stream.javadsl.Source
             |
           |import akka.NotUsed
             |import com.lightbend.lagom.javadsl.api.Descriptor
             |import com.lightbend.lagom.javadsl.api.ScalaService._
             |import com.lightbend.lagom.javadsl.api.Service
             |import com.lightbend.lagom.javadsl.api.ServiceCall
             |
           |trait ${name.capitalize}Service extends Service {
             |
           |  override def descriptor(): Descriptor = {
             |    named("${name.capitalize}")
             |    }
             |}
        """.stripMargin

        IO.append(dir / (s"${name.capitalize}Service.scala"), interface)
      }

      def createImplFiles(packName: String, name: String, dir: File): Unit = {
        val implementation =
          s"""/*
              | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
              | */
              |package $packName
              |
            |import javax.inject.Inject
              |
            |import com.lightbend.lagom.javadsl.api.ServiceCall
              |import akka.Done
              |import akka.NotUsed
              |
            |import scala.concurrent.{ExecutionContext, Future}
              |
            |class ${name}ServiceImpl @Inject()()(implicit ex: ExecutionContext) extends ${name}Service {
              |
            |  // Needed to convert some Scala types to Java
              |  import converter.ServiceCallConverter._
              |}
              |
            |""".stripMargin

        val module =
          s"""package $packName
              |
              |import com.google.inject.AbstractModule
              |import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
              |
            |/**
              | * The module that binds the ${name}ServiceModule so that it can be served.
              | */
              |class ${name}ServiceModule extends AbstractModule with ServiceGuiceSupport {
              |override def configure(): Unit = bindServices(serviceBinding(classOf[${name}Service], classOf[${name}ServiceImpl]))
              |
            |}""".stripMargin

        IO.append(dir / (s"${name}ServiceImpl.scala"), implementation)
        IO.append(dir / (s"${name}ServiceModule.scala"), module)

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


      def createConverterFile(dir: File) = {
        val serviceCallConverter =
          s"""
             |
             |package converter
             |
             |import java.util.concurrent.CompletionStage
             |
             |import com.lightbend.lagom.javadsl.api.ServiceCall
             |
             |object ServiceCallConverter extends CompletionStageConverters {
             |  implicit def liftToServiceCall[Request, Response](f: Request => CompletionStage[Response]): ServiceCall[Request,Response] =
             |    new ServiceCall[Request,Response] {
             |      def invoke(request: Request): CompletionStage[Response] = f(request)
             |  }
             |}""".stripMargin

        IO.append(dir / "converter" / ("ServiceCallConverter.scala"), serviceCallConverter)

        val CompletionStageConverters =
          s"""
             |
             |package converter
             |
             |import java.util.concurrent.CompletionStage
             |
             |import scala.compat.java8.FutureConverters.CompletionStageOps
             |import scala.compat.java8.FutureConverters.FutureOps
             |import scala.concurrent.Future
             |
             |import akka.NotUsed
             |
             |trait CompletionStageConverters {
             |
             |  implicit def asCompletionStage[A](f: Future[A]): CompletionStage[A] = f.toJava
             |  implicit def asFuture[A](f: CompletionStage[A]): Future[A] = f.toScala
             |
             |  implicit def asUnusedCompletionStage(f: CompletionStage[_]): CompletionStage[NotUsed] = f.thenApply(_ => NotUsed)
             |}
             |
             |object CompletionStageConverters extends CompletionStageConverters""".stripMargin

        IO.append(dir / "converter" / ("CompletionStageConverters.scala"), CompletionStageConverters)
      }

    })

  }


}
