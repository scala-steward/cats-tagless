import com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll
import com.typesafe.sbt.SbtGit.git
import org.typelevel.Dependencies._
import de.heikoseeberger.sbtheader.license.Apache2_0

addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

val gh = GitHubSettings(org = "com.kailuowang", proj = "mainecoon", publishOrg = "com.kailuowang", license = apache)
val devs = Seq(Dev("Kailuo Wang", "kauowang"))

val vAll = Versions(versions, libraries, scalacPlugins)

lazy val rootSettings = buildSettings ++ commonSettings ++ publishSettings ++ scoverageSettings
lazy val module = mkModuleFactory(gh.proj, mkConfig(rootSettings, commonJvmSettings, commonJsSettings))
lazy val prj = mkPrjFactory(rootSettings)

lazy val rootPrj = project
  .configure(mkRootConfig(rootSettings,rootJVM))
  .aggregate(rootJVM, rootJS, testsJS, macrosJS)
  .dependsOn(rootJVM, rootJS, testsJS, macrosJS)
  .settings(noPublishSettings)

lazy val rootJVM = project
  .configure(mkRootJvmConfig(gh.proj, rootSettings, commonJvmSettings))
  .aggregate(coreJVM, lawsJVM, testsJVM, macrosJVM, docs)
  .dependsOn(coreJVM, lawsJVM, testsJVM, macrosJVM)


lazy val rootJS = project
  .configure(mkRootJsConfig(gh.proj, rootSettings, commonJsSettings))
  .aggregate(coreJS, lawsJS)

lazy val core    = prj(coreM)
lazy val coreJVM = coreM.jvm
lazy val coreJS  = coreM.js
lazy val coreM   = module("core", CrossType.Pure)
  .settings(addLibs(vAll, "cats-core"))
  .settings(addTestLibs(vAll, "scalatest"))
  .settings(metaMacroSettings)
  .settings(simulacrumSettings(vAll))
  .enablePlugins(AutomateHeaderPlugin)

lazy val laws    = prj(lawsM)
lazy val lawsJVM = lawsM.jvm
lazy val lawsJS  = lawsM.js
lazy val lawsM   = module("laws", CrossType.Pure)
  .dependsOn(coreM)
  .settings(addLibs(vAll, "cats-laws"))
  .settings(disciplineDependencies)
  .enablePlugins(AutomateHeaderPlugin)


lazy val macros    = prj(macrosM)
lazy val macrosJVM = macrosM.jvm
lazy val macrosJS  = macrosM.js
lazy val macrosM   = module("macros", CrossType.Pure)
  .dependsOn(coreM)
  .settings(metaMacroSettings)
  .settings(copyrightHeader)
  .enablePlugins(AutomateHeaderPlugin)


lazy val tests    = prj(testsM)
lazy val testsJVM = testsM.jvm
lazy val testsJS  = testsM.js
lazy val testsM   = module("tests", CrossType.Pure)
  .dependsOn(coreM, lawsM, macrosM)
  .settings(disciplineDependencies)
  .settings(metaMacroSettings)
  .settings(noPublishSettings)
  .settings(addTestLibs(vAll, "scalatest" ))
  .enablePlugins(AutomateHeaderPlugin)


/** Docs - Generates and publishes the scaladoc API documents and the project web site.*/
lazy val docs = project.configure(mkMyDocConfig(gh, rootSettings ++ metaMacroSettings ++ unidocCommonSettings ++ simulacrumSettings(vAll), commonJvmSettings,
  coreJVM, macrosJVM))

def mkMyDocConfig(gh: GitHubSettings, projSettings: Seq[sbt.Setting[_]], jvmSettings: Seq[sbt.Setting[_]],
                deps: Project*): Project ⇒ Project =
  _.settings(projSettings)
    .settings(moduleName := gh.proj + "-docs")
    .settings(noPublishSettings)
    .settings(ghpages.settings)
    .settings(jvmSettings)
    .dependsOn(deps.map( ClasspathDependency(_, Some("compile;test->test"))):_*)
    .enablePlugins(MicrositesPlugin)
    .settings(
      organization  := gh.organisation,
      autoAPIMappings := true,
      micrositeName := "Mainecoon",
      micrositeGithubOwner := "kailuowang",
      micrositeGithubRepo := "mainecoon",
      ghpagesNoJekyll := false,
      tutScalacOptions ~= (_.filterNot(Set("-Ywarn-unused-import", "-Ywarn-dead-code"))),
      git.remoteRepo := gh.repo,
      includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md")


lazy val buildSettings = sharedBuildSettings(gh, vAll)

lazy val commonSettings = sharedCommonSettings ++ Seq(
  parallelExecution in Test := false,
  scalacOptions ++= scalacAllOptions,
  crossScalaVersions := Seq(vAll.vers("scalac_2.11"), scalaVersion.value)
) ++ xlintSettings ++ warnUnusedImport ++ unidocCommonSettings ++
  addCompilerPlugins(vAll, "kind-projector") ++ copyrightHeader

lazy val commonJsSettings = Seq(scalaJSStage in Global := FastOptStage)

lazy val commonJvmSettings = Seq()

lazy val publishSettings = sharedPublishSettings(gh, devs) ++ credentialSettings ++ sharedReleaseProcess

lazy val scoverageSettings = sharedScoverageSettings(60)

lazy val disciplineDependencies = addLibs(vAll, "discipline", "scalacheck")

lazy val metaMacroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies += "org.scalameta" %% "scalameta" % "1.7.0" % Provided,
  scalacOptions in (Compile, console) := Seq(), // macroparadise plugin doesn't work in repl yet.
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M8" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  sources in (Compile, doc) := Nil // macroparadise doesn't work with scaladoc yet.
)

lazy val copyrightHeader = Seq(
  headers := Map(
    "scala" -> Apache2_0("2017", "Kailuo Wang"),
    "java" -> Apache2_0("2017", "Kailuo Wang"))
  )
