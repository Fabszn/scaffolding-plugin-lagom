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

        val command =
          s"""package $packName;
             |
             |import com.lightbend.lagom.serialization.Jsonable;
             |
             |public interface ${name}Command extends Jsonable {}
           """.stripMargin

        val eventTag =
          s"""package $packName;
             |
             |import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
             |
             |public class ${name}EventTag {
             |
             |  public static final AggregateEventTag<${name}Event> INSTANCE =
             |    AggregateEventTag.of(${name}Event.class);
             |}
           """.stripMargin

        val event =
          s"""package $packName;
             |
             |import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
             |import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
             |import com.lightbend.lagom.serialization.Jsonable;
             |
             |public interface ${name}Event extends Jsonable, AggregateEvent<${name}Event> {
             |
             |  @Override
             |  default public AggregateEventTag<${name}Event> aggregateTag() {
             |    return ${name}EventTag.INSTANCE;
             |  }
             |}
           """.stripMargin

        val state =
          s"""package $packName;
             |
             |import javax.annotation.concurrent.Immutable;
             |import com.fasterxml.jackson.annotation.JsonCreator;
             |import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
             |import com.google.common.base.Preconditions;
             |import com.lightbend.lagom.serialization.Jsonable;
             |
             |@SuppressWarnings("serial")
             |@Immutable
             |@JsonDeserialize
             |public final class UserState implements Jsonable {
             |  public final String name;
             |
             |  @JsonCreator
             |  public ${name}State(String name) {
             |    this.name = Preconditions.checkNotNull(name, "name");
             |  }
             |}
           """.stripMargin

        val entity =
          s"""package $packName;
             |
             |import java.util.Optional;
             |import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
             |
             |public class ${name}Entity extends PersistentEntity<${name}Command, ${name}Event, ${name}State> {
             |  @Override
             |  public Behavior initialBehavior(Optional<${name}State> snapshotState) {
             |    BehaviorBuilder b = newBehaviorBuilder(snapshotState.orElse(
             |      new ${name}State("")));
             |
             |    return b.build();
             |  }
             |}
           """.stripMargin

        IO.append(dir / (s"${name}ServiceImpl.java"), implementation)
        IO.append(dir / (s"${name}ServiceModule.java"), module)
        IO.append(dir / (s"${name}Command.java"), command)
        IO.append(dir / (s"${name}EventTag.java"), eventTag)
        IO.append(dir / (s"${name}Event.java"), event)
        IO.append(dir / (s"${name}State.java"), state)
        IO.append(dir / (s"${name}Entity.java"), entity)

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


        if (input.packName.isEmpty) log.warn("No package has been defined")
        val sourceDir = "src/main/scala"
        val resourceDir = "src/main/resources"
        log.info(managePackageName(input.packName))
        val apiDir = baseDirectory.value / ((input.serviceName + "-api") + "/" + sourceDir + "/" + managePackageName(input.packName))
        val implDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/" + managePackageName(input.packName))
        val confDir = baseDirectory.value / ((input.serviceName + "-impl") + "/" + resourceDir)

        //create directories
        IO.createDirectories(List(apiDir, implDir))
        createInterfaceFile(input.packName, input.serviceName, apiDir)
        createImplFiles(input.packName, input.serviceName.capitalize, implDir)
        createConfFile(input.packName, input.serviceName, confDir)
        createConverterFile(baseDirectory.value / ((input.serviceName + "-impl") + "/" + sourceDir + "/"))
        addServiceConfToBuild(baseDirectory.value, input)
        log.info("Lagom(Scala) has been generated successfully")
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

        val command =
          s"""package $packName
             |
             |import com.lightbend.lagom.javadsl.persistence.PersistentEntity
              |import com.lightbend.lagom.serialization.Jsonable
              |import akka.Done
              |
              |sealed trait ${name}Command extends Jsonable
           """.stripMargin

        val event =
          s"""package $packName
             |
             |import com.lightbend.lagom.javadsl.persistence.AggregateEvent
              |import com.lightbend.lagom.serialization.Jsonable
              |import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
              |
              |object ${name}Event {
              |  val Tag = AggregateEventTag.of(classOf[${name}Event])
              |}
              |sealed trait ${name}Event extends AggregateEvent[${name}Event] with Jsonable {
              |  override def aggregateTag(): AggregateEventTag[${name}Event] = ${name}Event.Tag
              |}
           """.stripMargin

        val state =
          s"""package $packName
             |
             |import com.lightbend.lagom.serialization.Jsonable
             |
             |class ${name}State extends Jsonable {}
           """.stripMargin

        val entity =
          s"""package $packName
             |
             |import com.lightbend.lagom.javadsl.persistence.PersistentEntity
              |import scala.collection.JavaConverters._
              |import akka.Done
              |import java.util.Optional
              |import scala.compat.java8.OptionConverters._
              |
              |class ${name}Entity extends PersistentEntity[${name}Command, ${name}Event, ${name}State] {
              |
              |  override def initialBehavior(snapshotState: Optional[${name}State]): Behavior = {
              |    val b = newBehaviorBuilder(snapshotState.orElseGet(() => ${name}State()))
              |    b.build()
              |  }
              |}
           """.stripMargin

        IO.append(dir / (s"${name}ServiceImpl.scala"), implementation)
        IO.append(dir / (s"${name}ServiceModule.scala"), module)
        IO.append(dir / (s"${name}Commands.scala"), command)
        IO.append(dir / (s"${name}Events.scala"), event)
        IO.append(dir / (s"${name}State.scala"), state)
        IO.append(dir / (s"${name}Entity.scala"), entity)

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

      def addServiceConfToBuild(dir: File, input: Inputs) = {
        val sbtConf =
          s"""
             |
             |//${name} service
             |lazy val ${input.serviceName}Api = ${computeProjectDeclaration(input.serviceName + "-api", input.template)}
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies += lagomJavadslApi
             |  )
             |
             |lazy val ${input.serviceName}Impl = ${computeProjectDeclaration(input.serviceName + "-impl", input.template)}
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
             |  .dependsOn(${input.serviceName}Api)""".stripMargin

        IO.append(dir / ("build.sbt"), sbtConf)
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
