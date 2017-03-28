// Comment to get more information during initialization
//logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.13"

// Use the Play sbt plugin for Play projects
// Play 2.3.x the last smart version before the D.I nightmare
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")

addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.1")

addSbtPlugin("com.github.alexarchambault" % "coursier-sbt-plugin-java-6" % "1.0.0-M8")

// Dependency graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.0")

// web plugins

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-webdriver" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.0.0")
