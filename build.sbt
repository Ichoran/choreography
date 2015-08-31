lazy val root = (project in file(".")).settings(
  name := "Choreography",
  version := "1.5.0",
  scalaVersion := "2.11.7",
  artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
    "Chore." + artifact.extension
  }
)
