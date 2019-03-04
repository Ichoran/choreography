// The main project
lazy val root: Project = (project in file(".")).
  settings(
    name := "MwtWconConvert",
    version := "0.1.0",
    scalaVersion := "2.12.1",
    scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation"),
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies += "com.github.ichoran" %% "kse" % "0.6-SNAPSHOT",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.9" % "test",
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "0.4.2",
    mainClass in (Compile, run) := Some("mwt.converter.WconToOld")
  )
