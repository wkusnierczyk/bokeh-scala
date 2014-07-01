import sbt._
import Keys._

import sbtassembly.{Plugin=>SbtAssembly}
import org.sbtidea.SbtIdeaPlugin
import com.typesafe.sbt.SbtPgp

object Dependencies {
    val isScala_2_10 = Def.setting {
        scalaVersion.value.startsWith("2.10")
    }

    def scala_2_10(moduleID: ModuleID) =
        Def.setting { if (isScala_2_10.value) Seq(moduleID) else Seq.empty }

    def scala_2_11_+(moduleID: ModuleID) =
        Def.setting { if (!isScala_2_10.value) Seq(moduleID) else Seq.empty }

    val scalaio = {
        val namespace = "com.github.scala-incubator.io"
        val version = "0.4.3"
        Seq(namespace %% "scala-io-core" % version,
            namespace %% "scala-io-file" % version)
    }

    val breeze = "org.scalanlp" %% "breeze" % "0.8.1"

    val shapeless = Def.setting {
        val version = "2.0.0"
        if (scalaVersion.value.startsWith("2.10"))
            "com.chuusai" %  "shapeless" % version cross CrossVersion.full
        else
            "com.chuusai" %% "shapeless" % version
    }

    val play_json = "com.typesafe.play" %% "play-json" % "2.3.1"

    val specs2 = "org.specs2" %% "specs2" % "2.3.11" % "test"

    val jopt = "net.sf.jopt-simple" % "jopt-simple" % "4.5"

    val opencsv = "net.sf.opencsv" % "opencsv" % "2.3"

    val reflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

    val paradise = "org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full

    val quasiquotes = scala_2_10("org.scalamacros" %% "quasiquotes" % "2.0.0")

    val xml = scala_2_11_+("org.scala-lang.modules" %% "scala-xml" % "1.0.2")
}

object BokehBuild extends Build {
    override lazy val settings = super.settings ++ Seq(
        organization := "org.continuumio",
        version := "0.2-SNAPSHOT",
        description := "Scala bindings for Bokeh plotting library",
        homepage := Some(url("http://bokeh.pydata.org")),
        licenses := Seq("MIT-style" -> url("http://www.opensource.org/licenses/mit-license.php")),
        scalaVersion := "2.11.1",
        crossScalaVersions := Seq("2.10.4", "2.11.1"),
        scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked", "-feature", "-language:_"),
        addCompilerPlugin(Dependencies.paradise),
        shellPrompt := { state =>
            "continuum (%s)> ".format(Project.extract(state).currentProject.id)
        },
        cancelable := true,
        resolvers ++= Seq(
            Resolver.sonatypeRepo("releases"),
            Resolver.sonatypeRepo("snapshots"),
            Resolver.typesafeRepo("releases"),
            Resolver.typesafeRepo("snapshots"))
    )

    val bokehDir = settingKey[File]("Location of Bokeh library.")
    val runAll = taskKey[Unit]("Run all discovered main classes.")

    lazy val publishSettings = Seq(
        publishTo := {
            val nexus = "https://oss.sonatype.org/"
            if (isSnapshot.value)
                Some("snapshots" at nexus + "content/repositories/snapshots")
            else
                Some("releases" at nexus + "service/local/staging/deploy/maven2")
        },
        publishMavenStyle := true,
        publishArtifact in Test := false,
        pomIncludeRepository := { _ => false },
        pomExtra := (
            <scm>
                <url>https://github.com/mattpap/bokeh-scala</url>
                <connection>scm:git:https://github.com/mattpap/bokeh-scala.git</connection>
            </scm>
            <developers>
                <developer>
                    <id>mattpap</id>
                    <name>Mateusz Paprocki</name>
                    <url>mateuszpaprocki.pl</url>
                </developer>
            </developers>
        )
    )

    lazy val commonSettings = publishSettings ++ Seq(
        bokehDir := file("..") / "bokeh",
        runAll := {
            val results = (discoveredMainClasses in Compile).value.sorted.map { mainClass =>
                val classpath = Attributed.data((fullClasspath in Compile).value)
                val logger = streams.value.log

                val result = (runner in run).value.run(mainClass, classpath, Nil, logger)

                result match {
                    case Some(msg) => logger.error(s"$mainClass: $msg"); Some(mainClass)
                    case None      => logger.success(mainClass);         None
                }
            } flatten

            if (results.nonEmpty) {
                val failures = results.mkString(", ")
                sys.error(s"failed to run: $failures")
            }
        }
    )

    lazy val pgpSettings = SbtPgp.settings

    lazy val ideaSettings = SbtIdeaPlugin.settings

    lazy val assemblySettings = SbtAssembly.assemblySettings ++ {
        import SbtAssembly.AssemblyKeys._
        Seq(test in assembly := {},
            jarName in assembly := "Bokeh.jar",
            target in assembly := target.value / "lib")
    }

    lazy val pluginSettings = pgpSettings ++ ideaSettings ++ assemblySettings

    lazy val bokehSettings = Defaults.coreDefaultSettings ++ commonSettings ++ pluginSettings ++ Seq(
        libraryDependencies ++= {
            import Dependencies._
            scalaio ++ xml.value ++ Seq(breeze, shapeless.value, jopt, play_json, specs2)
        },
        fork in run := true,
        parallelExecution in Test := false,
        initialCommands in Compile := """
            import scala.reflect.runtime.{universe=>u,currentMirror=>cm}
            import scalax.io.JavaConverters._
            import scalax.file.Path
            import play.api.libs.json.Json
            import org.continuumio.bokeh._
            """
    )

    lazy val coreSettings = Defaults.coreDefaultSettings ++ commonSettings ++ Seq(
        libraryDependencies ++= {
            import Dependencies._
            quasiquotes.value ++ Seq(reflect.value, shapeless.value, play_json, specs2)
        }
    )

    lazy val sampledataSettings = Defaults.coreDefaultSettings ++ commonSettings ++ Seq(
        libraryDependencies ++= {
            import Dependencies._
            scalaio ++ xml.value ++ Seq(opencsv, specs2)
        }
    )

    lazy val examplesSettings = Defaults.coreDefaultSettings ++ commonSettings ++ Seq(
        libraryDependencies ++= {
            import Dependencies._
            Seq(breeze, specs2)
        }
    )

    lazy val bokeh = project in file(".") settings(bokehSettings: _*) dependsOn(core, sampledata) aggregate(core, sampledata)
    lazy val core = project in file("core") settings(coreSettings: _*)
    lazy val sampledata = project in file("sampledata") settings(sampledataSettings: _*) dependsOn(core)
    lazy val examples = project in file("examples") settings(examplesSettings: _*) dependsOn(bokeh)

    override def projects = Seq(bokeh, core, sampledata, examples)
}
