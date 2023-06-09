#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.44.0")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.domain.Job
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.toYaml

class ModuleBuilder(val parent: String) {
    val modules = mutableListOf<String>()
    fun p(vararg names: String) {
        for (name in names) modules.add("$parent-$name")
    }

    fun p(name: String, builder: ModuleBuilder.() -> Unit) {
        modules.addAll(ModuleBuilder("$parent-$name").apply(builder).modules)
    }
}

class ProjectsBuilder {
    val projects = mutableListOf<GradleProject>()
    fun p(name: String, builder: ModuleBuilder.() -> Unit) {
        projects.add(GradleProject(name, ModuleBuilder(name).apply(builder).modules))
    }
}

data class GradleProject(
    val path: String,
    val modules: List<String>
)

fun projects(builder: ProjectsBuilder.() -> Unit): List<GradleProject> = ProjectsBuilder().apply(builder).projects

val projects = projects {
    p("liquid") { p("number") }
    p("kronecker") { p("core") }
    p("lexi") {
        p("api", "console", "file")
        p("test") { p("android") }
    }
    p("kommander") { p("core", "coroutines") }
    p("kollections") { p("interoperable", "atomic") }
    p("kevlar") { p("core") }
    p("kase") { p("core") }
    p("krest") { p("core") }
    p("koncurrent") {
        p("executors") { p("core", "coroutines", "mock") }
        p("later") { p("core", "coroutines", "test") }
    }
    p("keep") { p("api", "browser", "file", "mock", "react-native") }
    p("cinematic") {
        p("live") { p("core", "compose", "coroutines", "react", "test") }
        p("scene") { p("core") }
    }
    p("krono") { p("api", "kotlinx") }
    p("hormone") { p("core") }
    p("geo") { p("countries", "core") }
    p("kash") { p("currency", "money") }
    p("epsilon") {
        p("core", "file", "fake")
        p("network") { p("ktor") }
    }
    p("symphony") {
        p("paginator", "selector", "actions", "table", "list", "collections")
        p("input") { p("core", "form", "text", "number", "choice", "list", "file", "krono", "geo", "kash", "dialog") }// "identifier"),}
    }
    p("captain") {
        p("url")
        p("navigator") { p("api", "browser", "basic") }
        p("router") {
            p("core")
            p("react") { p("core", "dom") }
            p("compose") { p("core", "html") }
        }
    }
    p("kida") { p("api", "brela", "fake") }
}

fun JobBuilder<JobOutputs.EMPTY>.setupAndCheckout(gp: GradleProject) {
    uses(CheckoutV3(submodules = true))
    uses(SetupJavaV3(javaVersion = "18", distribution = SetupJavaV3.Distribution.Corretto))
    run(
        name = "Make ./gradlew executable",
        command = "chmod +x ./gradlew",
        workingDirectory = gp.path,
    )
}

fun WorkflowBuilder.buildProject(gp: GradleProject) = job(
    id = "${gp.path}-builder", runsOn = RunnerType.MacOSLatest
) {
    setupAndCheckout(gp)
    gp.modules.forEach {
        val task = ":$it:build"
        uses(
            name = "./gradlew $task",
            action = GradleBuildActionV2(arguments = task, buildRootDirectory = "./${gp.path}")
        )
    }
}

fun WorkflowBuilder.publishProject(gp: GradleProject, after: Job<JobOutputs.EMPTY>) = job(
    id = "${gp.path}-publisher", runsOn = RunnerType.MacOSLatest, needs = listOf(after)
) {
    setupAndCheckout(gp)

    val argument = gp.modules.joinToString(separator = " ") {
        ":$it:publishAllPublicationsToMavenCentral"
    } + " --no-configuration-cache"
    uses(
        name = "publishing " + gp.modules.joinToString(", "),
        action = GradleBuildActionV2(arguments = argument, buildRootDirectory = "./${gp.path}")
    )
}

val wf = workflow(
    name = "Build, Cache then Publish", on = listOf(Push(branches = listOf("main"))), sourceFile = __FILE__.toPath(),
    env = linkedMapOf(
        "ORG_GRADLE_PROJECT_mavenCentralUsername" to expr { secrets["ASOFT_NEXUS_USERNAME"].toString() },
        "ORG_GRADLE_PROJECT_mavenCentralPassword" to expr { secrets["ASOFT_NEXUS_PASSWORD"].toString() },
        "ORG_GRADLE_PROJECT_signingInMemoryKey" to expr { secrets["ORG_GRADLE_PROJECT_SIGNINGINMEMORYKEY"].toString() },
        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" to expr { secrets["ORG_GRADLE_PROJECT_SIGNINGINMEMORYKEYPASSWORD"].toString() },
        "TARGETING_ALL" to "true"
    ),
) {
    val buildJobs = projects.map { buildProject(it) }
    val rendezvous = job(id = "rendezvous", runsOn = RunnerType.UbuntuLatest, needs = buildJobs) {
        run("""echo "all builds completed. Beginning deployment"""")
    }
    projects.forEach { publishProject(it, rendezvous) }
}

println(wf.toYaml(addConsistencyCheck = false))