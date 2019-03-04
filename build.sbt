resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file(".")).settings(
  name := "Choreography",
  version := "1.5.0",
  mainClass := Some("mwt.Choreography"),
  scalaVersion := "2.12.4",
  libraryDependencies += "com.github.ichoran" %% "kse" % "0.7-SNAPSHOT",
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
  libraryDependencies += "org.apache.commons" % "commons-math3" % "3.0",
  libraryDependencies += "edu.emory.mathcs" % "JTransforms" % "2.4",
  artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
    "Chore." + artifact.extension
  }
)
