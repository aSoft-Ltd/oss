#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.41.0")

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

data class RootProject(
    val name: String,
    val path: String,
    val subs: List<String>,
    val repo: String = path
)

val projects = listOf(
    RootProject("functions", "functions", listOf("core")),
    RootProject("kommander", "kommander", listOf("core", "coroutines")),
    RootProject("lexi", "lexi", listOf("api", "console", "file")),
    RootProject("lexi-test", "lexi", listOf("android")),
    RootProject("kollections", "kollections", listOf("interoperable", "atomic")),
    RootProject("kevlar", "kevlar", listOf("core")),
    RootProject("kase", "kase", listOf("core")),
//    RootProject("koncurrent-executors", "koncurrent", listOf("core", "coroutines", "mock")),
//    RootProject("koncurrent-later", "koncurrent", listOf("core", "coroutines", "test")),
//    RootProject("keep", "keep", listOf("api", "browser", "file", "mock", "react-native")),
    // =======================
//    RootProject("live", "live", listOf("core", "compose", "coroutines", "react", "test")),

//    RootProject("viewmodel", "viewmodel", listOf("core")),
//
//    RootProject("formatter", "formatter", listOf("core")),
//    RootProject("kash", "kash", listOf("currency", "money")),
//    RootProject("identifier", "identifier", listOf("core", "generators")),
//
//    RootProject("events", "events", listOf("core", "inmemory", "react")),
//    RootProject("response", "response", listOf("core")),
//
//    RootProject("geo", "geo", listOf("core", "languages", "countries")),
//    RootProject("krono", "krono", listOf("api")),
//    RootProject("presenters", "presenters", listOf("core", "actions", "mock", "krono", "geo")),
//
//    RootProject("mailer", "mailer", listOf("api", "mock", "smtp")),
//
//    // math libs
//    RootProject("math", "math", listOf("core")),
//    RootProject("math-spatial", "math", listOf("core")),
//    RootProject("math-vector", "math", listOf("core")),
//    RootProject("math-point", "math", listOf("core")),
//
//    RootProject("kida", "kida", listOf("api", "ktor", "core", "fake"))
).reversed()

fun JobBuilder<JobOutputs.EMPTY>.setupAndCheckout(rp: RootProject) {
    uses(CheckoutV3(submodules = true))
    uses(SetupJavaV3(javaVersion = "18", distribution = SetupJavaV3.Distribution.Corretto))
    run(
        name = "Make ./gradlew executable",
        command = "chmod +x ./gradlew",
        workingDirectory = rp.path,
    )
}

fun WorkflowBuilder.buildProject(rp: RootProject) = job(
    id = "${rp.name}-builder", runsOn = RunnerType.MacOSLatest
) {
    setupAndCheckout(rp)
    rp.subs.forEach {
        val task = ":${rp.name}-$it:build"
        uses(
            name = "./gradlew $task",
            action = GradleBuildActionV2(arguments = task, buildRootDirectory = "./${rp.path}")
        )
    }
}

fun WorkflowBuilder.publishProject(rp: RootProject, after: Job<JobOutputs.EMPTY>) = job(
    id = "${rp.name}-publisher", runsOn = RunnerType.MacOSLatest, needs = listOf(after)
) {
    setupAndCheckout(rp)

    val argument =
        rp.subs.joinToString(separator = " ") { ":${rp.name}-$it:publishAllPublicationsToMavenCentral" } + " --no-configuration-cache"
//    val argument = "publishToSonatype"
    uses(
        name = "publishing " + rp.subs.joinToString(", ") { "${rp.name}-$it" },
        action = GradleBuildActionV2(arguments = argument, buildRootDirectory = "./${rp.path}")
    )
}

val workflow = workflow(
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
//    val rendezvous = job(id = "rendezvous", runsOn = RunnerType.UbuntuLatest) {
        run("""echo "all builds completed. Beginning deployment"""")
    }
//    projects.forEach { publishProject(it, rendezvous) }
//    projects.associateBy { it.repo }.values.forEach { publishProject(it, rendezvous) }
}

println(workflow.toYaml(addConsistencyCheck = false))