package com.veinhorn.sbt.plugin.profile

import java.io.PrintWriter

import sbt.Keys._
import sbt._
import sbt.complete.Parsers.spaceDelimited

import scala.io.Source

/**
  * Created by Boris Korogvich on 12.09.2017.
  */
object ProfilePlugin extends AutoPlugin {
  type Property = (String, String)

  case class Profile(id: String,
                     properties: Seq[Property] = List.empty,
                     default: Boolean = false,
                     resourceDirs: Seq[File] = List.empty)

  object autoImport {
    // Settings
    val profiles: SettingKey[Seq[Profile]] = settingKey[Seq[Profile]]("Specify profiles")
    // Tasks
    lazy val showProfiles: TaskKey[Unit] = taskKey[Unit]("Show all profiles")
  }

  import autoImport._

  lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
    /** By default add resource directories from default profile */
    (unmanagedResourceDirectories in Compile) ++= {
      profiles.value.find(_.default).map(_.resourceDirs) getOrElse List.empty
    },

    /** Replace properties in copied resources with properties from default profile */
    (copyResources in Compile) := {
      def replaceInFile(resFile: File) = {
        println(s"Replacing ${resFile.toPath.toString} ...")

        val res = Source.fromFile(resFile).mkString
        var replaced = res

        profiles.value.find(_.default).foreach { profile =>
          println(s"Selected *${profile.id}* profile")

          profile.properties.foreach { case (key, value) =>
            val regex = "\\$\\{" + key + "\\}"

            replaced = replaced.replaceAll(regex, value)
          }
        }
        new PrintWriter(resFile) { write(replaced); close() }
      }

      val copied = (copyResources in Compile).value

      val targetDir = (classDirectory in Compile).value

      Option(targetDir.listFiles())
        .map(_.filter(_.isFile))
        .foreach(_.foreach(replaceInFile))

      copied
    },

    (showProfiles in Compile) := {
      println("Profiles:")
      profiles.value.map {
        case Profile(id, _, true, _)  => s"\t-$id *"
        case Profile(id, _, false, _) => s"\t-$id"
      } foreach println
    },

    commands ++= Seq(
      Command.single("selectProfile") { (state, profile) =>
        println(s"Selecting profile: $profile")

        val extracted = Project.extract(state)

        val modifiedProfiles = profiles.value.map {
          case p@Profile(_, _, true, _)                => p.copy(default = false)
          case p@Profile(id, _, _, _) if id == profile => p.copy(default = true)
          case p                                       => p
        }

        val modifiedResources = {
          ((unmanagedResourceDirectories in Compile).value.head :: Nil) ++ modifiedProfiles.find(_.default).map(_.resourceDirs).getOrElse(List.empty)
        }

        extracted.append(Seq(
          profiles := modifiedProfiles,
          (unmanagedResourceDirectories in Compile) := modifiedResources
        ), state)
      }
    )
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = baseSettings

}
