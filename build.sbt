organization := "tv.noixion"

name := "noixion_delivery_network"

scalaVersion := "2.12.8"

version := "1.0"

val appDependencies = Seq(
  // Comment the next line for local development of the Play Authentication core:
  evolutions,
  javaWs,
  javaJdbc,
  "org.webjars" % "bootstrap" % "3.2.0",
  "mysql" % "mysql-connector-java" % "5.1.36",
  "org.easytesting" % "fest-assert" % "1.4" % "test",
  "org.seleniumhq.selenium" % "selenium-java" % "2.52.0" % "test",
  "org.web3j" % "core" % "3.3.1",
  "com.madgag.spongycastle" % "prov" % "1.53.0.0",
  "com.madgag.spongycastle" % "core" % "1.53.0.0",
  "org.apache.commons" % "commons-lang3" % "3.8.1",
  "io.grpc" % "grpc-protobuf" % "1.9.0",
  "com.google.code.findbugs" % "annotations" % "3.0.1u2",
  "com.google.errorprone" % "error_prone_annotations" % "2.0.19",
  "com.google.guava" % "guava" % "20.0",
  "commons-io" % "commons-io" % "2.5",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "com.google.code.gson" % "gson" % "2.8.0",
  "com.amazonaws" % "aws-java-sdk" % "1.11.674",
  "org.modelmapper" % "modelmapper" % "0.7.7"
)

libraryDependencies += guice

// https://mvnrepository.com/artifact/com.github.ipfs/java-ipfs-http-client
libraryDependencies += "com.github.ipfs" % "java-ipfs-http-client" % "v1.2.3"

// https://mvnrepository.com/artifact/org.apache.httpcomponents/httpmime
libraryDependencies += "org.apache.httpcomponents" % "httpmime" % "4.5.10"

// https://mvnrepository.com/artifact/org.apache.httpcomponents/httpclient
libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5.10"

// https://mvnrepository.com/artifact/redis.clients/jedis
libraryDependencies += "redis.clients" % "jedis" % "3.2.0"

libraryDependencies += "com.h2database" % "h2" % "1.4.192"

// add resolver for deadbolt and easymail snapshots
resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "jitpack.io" at "https://jitpack.io"

// display deprecated or poorly formed Java
javacOptions ++= Seq("-Xlint:unchecked")
javacOptions ++= Seq("-Xlint:deprecation")
javacOptions ++= Seq("-Xdiags:verbose")

//  Uncomment the next line for local development of the Play Authenticate core:
//lazy val playAuthenticate = project.in(file("modules/play-authenticate")).enablePlugins(PlayJava)

lazy val root = project.in(file("."))
  .enablePlugins(PlayJava, PlayEbean)
  .settings(
    libraryDependencies ++= appDependencies
  )
  /* Uncomment the next lines for local development of the Play Authenticate core: */
  //.dependsOn(playAuthenticate)
  //.aggregate(playAuthenticate)
