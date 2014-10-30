import sbt._

object Dependencies {

  object V {
    val Play_22            = "2.2.3"
    val Play_23            = "2.3.5"

    val Spire              = "0.8.2"
    val Shapeless          = "2.0.0"
    val Discipline         = "0.2.1"

    val CommonsMath3       = "3.3"

    // Test libraries
    val Specs2             = "2.4.2"
    val ScalaCheck         = "1.11.6"

    val Jmh                = "1.0"
  }

  // Compile
  object Compile {
    val playJson_22       = "com.typesafe.play"        %% "play-json"               % V.Play_22 % "provided"
    val playJson_23       = "com.typesafe.play"        %% "play-json"               % V.Play_23 % "provided"

    val spire             = "org.spire-math"           %% "spire"                   % V.Spire
    val shapeless_10      = "com.chuusai"               % "shapeless_2.10.4"        % V.Shapeless
    val shapeless_11      = "com.chuusai"               % "shapeless_2.11"          % V.Shapeless

    val commonsMath3      = "org.apache.commons"        % "commons-math3"           % V.CommonsMath3
  }

  // Test
  object Test {
    val specs2          =   "org.specs2"            %% "specs2"                      % V.Specs2        % "test"
    val scalaCheck      =   "org.scalacheck"        %% "scalacheck"                  % V.ScalaCheck    % "test"
    val spireLaws       =   "org.spire-math"        %% "spire-scalacheck-binding"    % V.Spire         % "test"
    val discipline      =   "org.typelevel"         %% "discipline"                  % V.Discipline    % "test"
  }

  import sbt.Keys._

  val macroParadise: Seq[Setting[_]] = Seq(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          Seq()
        case Some((2, 10)) =>
          Seq(
            compilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full),
            "org.scalamacros" %% "quasiquotes" % "2.0.0")
      }
    }
  )
}
