#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.0.1")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v3")
@file:DependsOn("actions:setup-java:v3")
@file:DependsOn("gradle:gradle-build-action:v2")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildAction
import io.github.typesafegithub.workflows.domain.Job
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

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
    fun p(name: String, path: String = name, id: String = path, builder: ModuleBuilder.() -> Unit) {
        projects.add(GradleProject(name, path, id, ModuleBuilder(name).apply(builder).modules))
    }
}

data class GradleProject(
    val name: String,
    val path: String,
    val id: String,
    val modules: List<String>
)

fun projects(builder: ProjectsBuilder.() -> Unit): List<GradleProject> = ProjectsBuilder().apply(builder).projects

val projects = projects {
    p("kotlinx-interoperable") {
        p("exports")
    }
    p("lexi") {
        p("api", "console", "file", "configuration", "formatters")
        p("test") { p("android") }
    }
    p("kommander") { p("core", "coroutines") }
//    p("kollections") { p("primitive", "atomic", "stacks") }
    p("kevlar") { p("core") }
    p("kase") {
        p("core", "optional", "possible")
    }
    p("response") {
        p("core")
        p("ktor") {
            p("server", "client")
        }
    }
//    p("koncurrent") {
//        p("utils")
//        p("executors") { p("core", "coroutines", "mock") }
//        p("awaited") { p("core", "coroutines", "test") }
//        p("later") { p("core", "coroutines", "test") }
//    }
    p("keep") {
        p("api", "browser", "file", "mock") // , "react-native") untill https://youtrack.jetbrains.com/issue/KT-80014 gets fixed
    }
    p("cinematic") {
        p("live") { p("core", "kollections", "compose", "coroutines", "react", "test") }
        p("scene") { p("core") }
    }
    p("krono", "krono-core") { p("api", "kotlinx") }
    p("habitat") {
        p("core")
    }
    p("kiota") {
        p("url", "sse")
        p("file") {
            p("core", "system", "virtual", "compose")
            listOf("picker", "manager").forEach {
                p(it) { p("core", "system", "virtual") }
            }
        }
        p("connection") { p("core", "http", "manual") }
    }
    p("neat") { p("validation", "formatting") }
//    p("epsilon", "epsilon-api") { p("core") }
//    p("epsilon", "epsilon-client") {
//        p("fields")
//        p("image") { p("core", "web") }
//        p("image-react") { p("core", "dom") }
//        p("image-compose") { p("core", "html") }
//    }
    p("symphony") {
        p("visibility", "paginator", "selector", "actions", "table", "list", "collections")
        p("input") { p("core", "text", "number", "choice", "dialog", "phone") }
    }
    p("nation") {
        p("countries", "currencies")
        p("flags") { p("compose") }
    }
    p("sim") { p("core") }
    p("captain") {
        p("navigator") { p("api", "browser", "basic") }
        p("router") {
            p("core")
            p("react") { p("core", "dom") }
            p("compose") { p("core", "html") }
        }
    }
    p("raven") {
        p("core", "config")
        p("outbox") { p("core", "local", "server", "client") }
        p("email") {
            p("markup")
            p("agent") { p("core", "config", "console", "brevo", "mailgun", "postmark") }
            p("resources") { p("core", "file") }
        }
        p("sms") { p("core", "config", "console", "kila") }
    }
    p("status") { p("core", "scene") }
    p("krest") { p("core") }
    // <todo>
//    p("klip") { p("api", "browser", "system") }
    // </todo>
    p("kida") { p("api", "brela", "fake") }
//    p("majestic") {
//        p("theme", "table", "graphs", "drawers", "screen", "loaders")
//        p("input") { p("core", "text", "color", "choice", "phone") }
//    }
}

fun JobBuilder<JobOutputs.EMPTY>.setupAndCheckout(gp: GradleProject) {
    uses(action = Checkout(submodules = true))
    uses(action = SetupJava(javaVersion = "18", distribution = SetupJava.Distribution.Corretto))
    run(
        name = "Make ./gradlew executable",
        command = "chmod +x ./gradlew",
        workingDirectory = gp.path,
    )
}

fun WorkflowBuilder.buildProject(gp: GradleProject) = job(
    id = "${gp.id}-builder",
    runsOn = RunnerType.MacOSLatest
) {
    setupAndCheckout(gp)

//    run(
//        name = "Remove kotlin-js-store so that installing upgrading package-lock doesn't fail",
//        command = """rm ./kotlin-js-store -rf | echo "done"""",
//        workingDirectory = gp.path,
//    )

//    uses(
//        name = "Updating package.lock",
//        action = GradleBuildAction(
//            arguments = "kotlinUpgradePackageLock kotlinWasmUpgradePackageLock",
//            buildRootDirectory = "./${gp.path}",
//            cacheDisabled = true
//        )
//    )

//    uses(
//        name = "Assuring package.lock is well updated",
//        action = GradleBuildAction(
//            arguments = "kotlinUpgradePackageLock kotlinWasmUpgradePackageLock --rerun-tasks",
//            buildRootDirectory = "./${gp.path}",
//            cacheDisabled = true
//        )
//    )

//    gp.modules.forEach { // Disabling building // untill https://youtrack.jetbrains.com/issue/KT-80014 gets fixed
//        uses(
//            name = "building $it",
//            action = GradleBuildAction(
//                arguments = "kotlinUpgradePackageLock kotlinWasmUpgradePackageLock :$it:build --rerun-tasks",
//                buildRootDirectory = "./${gp.path}",
//                cacheDisabled = true
//            )
//        )
//    }
}

fun WorkflowBuilder.publishProject(gp: GradleProject, after: Job<JobOutputs.EMPTY>) = job(
    id = "${gp.id}-publisher",
    runsOn = RunnerType.MacOSLatest,
    needs = listOf(after)
) {
    setupAndCheckout(gp)

//    uses(
//        name = "Updating package.lock",
//        action = GradleBuildAction(
//            arguments = "kotlinUpgradePackageLock --rerun-tasks",
//            buildRootDirectory = "./${gp.path}",
//            cacheDisabled = true
//        )
//    )

    val argument = gp.modules.joinToString(
        separator = " ",
        postfix = " --no-configuration-cache"
    ) {
        ":$it:publishAllPublicationsToMavenCentral"
    }

    uses(
        name = "publishing " + gp.modules.joinToString(", "),
        action = GradleBuildAction(arguments = argument, buildRootDirectory = "./${gp.path}", cacheDisabled = true)
    )
}

workflow(
    name = "Build, Cache then Publish", on = listOf(Push(branches = listOf("main"))), sourceFile = __FILE__,
    env = linkedMapOf(
        "ORG_GRADLE_PROJECT_mavenCentralUsername" to expr { secrets["ASOFT_NEXUS_USERNAME"].toString() },
        "ORG_GRADLE_PROJECT_mavenCentralPassword" to expr { secrets["ASOFT_NEXUS_PASSWORD"].toString() },
        "ORG_GRADLE_PROJECT_signingInMemoryKey" to expr { secrets["ORG_GRADLE_PROJECT_SIGNINGINMEMORYKEY"].toString() },
        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" to expr { secrets["ORG_GRADLE_PROJECT_SIGNINGINMEMORYKEYPASSWORD"].toString() },
        "TARGETING_ALL" to "true"
    ),
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
    val buildJobs = projects.map { buildProject(it) }
    val rendezvous = job(id = "rendezvous", runsOn = RunnerType.UbuntuLatest, needs = buildJobs) {
        run(command = """echo "all builds completed. Beginning deployment"""")
    }
    projects.forEach { publishProject(it, rendezvous) }
}
