#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.41.0")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.endbug.AddAndCommitV9
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.domain.Job
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.actions.CustomAction
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.contexts.EnvContext.GITHUB_REF
import io.github.typesafegithub.workflows.dsl.expressions.contexts.GitHubContext
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
    RootProject("kollections", "kollections", listOf("interoperable", "atomic")),
    RootProject("kevlar", "kevlar", listOf("core")),
    RootProject("kase", "kase", listOf("core")),
    RootProject("koncurrent-executors", "koncurrent", listOf("core", "coroutines", "mock")),
    RootProject("koncurrent-later", "koncurrent", listOf("core", "coroutines", "test")),
//    RootProject("live", "live", listOf("core", "compose", "coroutines", "react", "test")),
//    RootProject("viewmodel", "viewmodel", listOf("core")),
//    RootProject("cache", "cache", listOf("api", "browser", "file", "mock", "react-native")),
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
//    RootProject("bitframe-actor", "bitframe", listOf("core", "app", "user", "space")),
//    RootProject("bitframe-dao", "bitframe", listOf("core", "mock", "mongo", "file")),
//    RootProject("bitframe", "bitframe", listOf("dao")),
//    RootProject("bitframe-service-builder", "bitframe", listOf("core", "daod", "rest")),
//    RootProject("bitframe-service-builder-api", "bitframe", listOf("core", "ktor", "mock")),
//    RootProject("bitframe-service-builder-sdk-client", "bitframe", listOf("core", "react" /* "mock",*/)),
//    RootProject("bitframe-service-builder-sdk-server", "bitframe", listOf("core")),
//    RootProject("bitframe-api", "bitframe", listOf("core" /* "ktor", "mock" */)),
//    RootProject("bitframe-sdk-server", "bitframe", listOf("core", "ktor", "test")),
//
//    // math libs
//    RootProject("math", "math", listOf("core")),
//    RootProject("math-spatial", "math", listOf("core")),
//    RootProject("math-vector", "math", listOf("core")),
//    RootProject("math-point", "math", listOf("core")),
//
//    RootProject("kida", "kida", listOf("api", "ktor", "core", "fake"))
)

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

//fun WorkflowBuilder.publishProject(rp: RootProject, after: Job<JobOutputs.EMPTY>) = job(
//    id = "${rp.repo}-publisher", runsOn = RunnerType.MacOSLatest, needs = listOf(after)
//) {
//    setupAndCheckout(rp)
//    val argument = "publishAllPublicationsToSonatypeRepository closeAndReleaseStagingRepository"
//    uses(
//        name = "publishing ${rp.repo}",
//        action = GradleBuildActionV2(arguments = argument, buildRootDirectory = "./${rp.path}")
//    )
//}

fun WorkflowBuilder.publishProject(rp: RootProject, after: Job<JobOutputs.EMPTY>) = job(
    id = "${rp.name}-publisher", runsOn = RunnerType.MacOSLatest, needs = listOf(after)
) {
    setupAndCheckout(rp)

    val argument =
        rp.subs.joinToString(separator = " ") { ":${rp.name}-$it:publishToSonatype" } + " closeAndReleaseStagingRepository"
    uses(
        name = "publishing " + rp.subs.joinToString(", ") { "${rp.name}-$it" },
        action = GradleBuildActionV2(arguments = argument, buildRootDirectory = "./${rp.path}")
    )
}

val workflow = workflow(
    name = "Build, Cache then Publish", on = listOf(Push(branches = listOf("main"))), sourceFile = __FILE__.toPath(),
    env = linkedMapOf(
        "ASOFT_MAVEN_PGP_PRIVATE_KEY" to expr { secrets["ASOFT_MAVEN_PGP_PRIVATE_KEY"].toString() },
        "ASOFT_MAVEN_PGP_PASSWORD" to expr { secrets["ASOFT_MAVEN_PGP_PASSWORD"].toString() },
        "ASOFT_NEXUS_PASSWORD" to expr { secrets["ASOFT_NEXUS_PASSWORD"].toString() },
        "ASOFT_NEXUS_USERNAME" to expr { secrets["ASOFT_NEXUS_USERNAME"].toString() },
        "TARGETING_ALL" to "true"
    ),
) {
    val buildJobs = projects.map { buildProject(it) }
    val rendezvous = job(id = "rendezvous", runsOn = RunnerType.UbuntuLatest, needs = buildJobs) {
        run("""echo "all builds completed. Beginning deployment"""")
    }
    projects.forEach { publishProject(it, rendezvous) }
//    projects.associateBy { it.repo }.values.forEach { publishProject(it, rendezvous) }
}

println(workflow.toYaml(addConsistencyCheck = false))