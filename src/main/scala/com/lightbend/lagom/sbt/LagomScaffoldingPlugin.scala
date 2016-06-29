package com.lightbend.lagom.sbt

import sbt.Keys._
import sbt.{IO, _}

import scala.util.{Failure, Success, Try}

/**
  * Created by fsznajderman on 29/06/16.
  */
object LagomScaffoldingPlugin extends AutoPlugin {


  val newService = inputKey[Unit]("Generate new Lagom service")

  import sbt.complete.Parsers._

  override def projectSettings = {
    Seq(newService := {
      val log = streams.value.log

      Try(spaceDelimited("<arg>").parsed) match {
        case Success(head :: tail) => buildService(head)
        case Failure(e) => Nil
      }

      def buildService(serviceName: String): Unit = {

        val packName = Option(organization.value)
        if (packName.isEmpty) log.warn("no package has been defined")
        val sourceDir = "src/main/java"
        val resourceDir = "src/main/resources"
        log.info(managePack(packName))
        val apiDir = baseDirectory.value / ((serviceName + "-api") + "/" + sourceDir + "/" + managePack(packName))
        val implDir = baseDirectory.value / ((serviceName + "-impl") + "/" + sourceDir + "/" + managePack(packName))
        val confDir = baseDirectory.value / ((serviceName + "-impl") + "/" + resourceDir)

        //create directories
        IO.createDirectories(List(apiDir, implDir))
        createInterfaceFile(packName.getOrElse(""), serviceName, apiDir)
        createImplFiles(packName.getOrElse(""), serviceName.capitalize, implDir)
        createConfFile(packName.getOrElse(""), serviceName, confDir)
        addServiceConfToBuild(serviceName, baseDirectory.value)
      }

      def managePack(packName: Option[String]): String = packName.map(_.replace(".", "/")).getOrElse("")

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

      def addServiceConfToBuild(name: String, dir: File) = {
        val sbtConf =
          s"""
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

    })

  }


}
