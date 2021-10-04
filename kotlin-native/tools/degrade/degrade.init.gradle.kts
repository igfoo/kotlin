import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState

rootProject {
    apply<DegradePlugin>()
}

class DegradePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target != target.rootProject) return

        Degrade(target).register()
    }
}

private class Degrade(val rootProject: Project) {
    private val scriptDir = rootProject.file("degrade")

    private class TaskLog {
        val stdout = StringBuilder()

        fun clear() {
            stdout.clear()
        }
    }

    fun register() {
        val taskToLog = mutableMapOf<Task, TaskLog>()
        val allScripts = mutableListOf<String>()
        val failedScripts = mutableListOf<String>()

        rootProject.allprojects {
            tasks.all {
                val task = this@all
                val state = taskToLog.getOrPut(task, ::TaskLog)
                task.logging.addStandardOutputListener(state.stdout::append)
            }
        }

        rootProject.gradle.addListener(object : TaskExecutionListener, BuildListener {
            override fun beforeExecute(task: Task) {
                taskToLog.getOrPut(task, ::TaskLog).clear()
            }

            override fun afterExecute(task: Task, state: TaskState) {
                val log = taskToLog[task] ?: return
                val script = generateScriptForTask(task, log) ?: return
                allScripts += script
                if (state.failure != null) {
                    failedScripts += script
                }
            }

            override fun buildFinished(result: BuildResult) {
                try {
                    generateAggregateScript("rerun-all.sh", allScripts)
                    generateAggregateScript("rerun-failed.sh", failedScripts)
                } finally {
                    failedScripts.clear()
                    allScripts.clear()
                }
            }

            override fun settingsEvaluated(settings: Settings) {}

            override fun projectsLoaded(gradle: Gradle) {}

            override fun projectsEvaluated(gradle: Gradle) {}
        })
    }

    private fun generateAggregateScript(name: String, scripts: List<String>) = generateScript(name) {
        appendLine("""cd "$(dirname "$0")"""")
        appendLine()
        scripts.forEach {
            appendLine("./$it")
        }
    }

    private fun generateScriptForTask(task: Task, taskLog: TaskLog): String? {
        val project = task.project

        val stdoutLinesIterator = taskLog.stdout.split('\n').iterator()
        val commands = parseKotlinNativeCommands { stdoutLinesIterator.takeIf { it.hasNext() }?.next() }

        if (commands.isEmpty()) return null

        val konanHome = project.properties["konanHome"]

        val scriptName = task.path.substring(1).replace(':', '_') + ".sh"

        generateScript(scriptName) {
            appendLine("""kotlinNativeDist="$konanHome"""")
            appendLine()
            commands.forEach { command ->
                appendLine(""""${"$"}kotlinNativeDist/bin/run_konan" \""")
                command.transformedArguments.forEachIndexed { index, argument ->
                    append("    ")
                    append(argument)
                    if (index != command.transformedArguments.lastIndex) {
                        appendLine(" \\")
                    }
                }
                appendLine()
                appendLine()
            }
        }

        return scriptName
    }

    private fun parseKotlinNativeCommands(nextLine: () -> String?): List<KotlinNativeCommand> {
        val result = mutableListOf<KotlinNativeCommand>()

        while (true) {
            val line = nextLine() ?: break
            if (line != "Main class = $kotlinNativeEntryPointClass"
                    && !line.startsWith("Entry point method = $kotlinNativeEntryPointClass.")) continue

            val transformedArguments = generateSequence { nextLine() }
                    .dropWhile { !it.startsWith("Transformed arguments = ") }.drop(1)
                    .takeWhile { it != "]" }
                    .map { it.trimStart() }
                    .toList()

            result += KotlinNativeCommand(transformedArguments)
        }

        return result
    }

    private class KotlinNativeCommand(val transformedArguments: List<String>)

    private companion object {
        const val kotlinNativeEntryPointClass = "org.jetbrains.kotlin.cli.utilities.MainKt"
    }

    private fun generateScript(name: String, generateBody: Appendable.() -> Unit) {
        scriptDir.mkdirs()
        val file = File(scriptDir, name)
        file.bufferedWriter().use { writer ->
            writer.appendLine("#!/bin/sh")
            writer.appendLine("set -e")
            writer.appendLine()

            writer.generateBody()
        }
        file.setExecutable(true)
    }
}
