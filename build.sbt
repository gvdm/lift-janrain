name := "janrain"

organization := "net.liftmodules"

version := "0.7-SNAPSHOT"

val liftVersion = "3.0-M5"

val liftEdition = liftVersion.substring(0,3)

moduleName := name.value + "_" + liftEdition

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

crossScalaVersions := Seq("2.11.4", "2.10.4")

resolvers ++= Seq("snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots",
                  "staging"       at "https://oss.sonatype.org/content/repositories/staging",
                  "releases"      at "https://oss.sonatype.org/content/repositories/releases"
                 )

libraryDependencies ++= {
  Seq(
    "net.liftweb"		%% "lift-webkit"			% liftVersion		% "compile"
  )
}

publishMavenStyle := true

publishArtifact in Test := false
