package co.touchlab.skie.api.phases.debug

import co.touchlab.skie.api.phases.SkieLinkingPhase
import co.touchlab.skie.configuration.Configuration
import co.touchlab.skie.configuration.features.SkieFeature
import co.touchlab.skie.plugin.api.skieBuildDirectory
import co.touchlab.skie.plugin.api.skieContext
import co.touchlab.skie.plugin.api.util.FrameworkLayout
import co.touchlab.skie.util.Command
import org.jetbrains.kotlin.backend.common.CommonBackendContext

sealed class DumpSwiftApiPhase(
    private val context: CommonBackendContext,
    private val framework: FrameworkLayout,
) : SkieLinkingPhase {

    class BeforeApiNotes(
        configuration: Configuration,
        context: CommonBackendContext,
        framework: FrameworkLayout,
    ) : DumpSwiftApiPhase(context, framework) {

        override val isActive: Boolean = SkieFeature.DumpSwiftApiBeforeApiNotes in configuration.enabledFeatures
    }

    class AfterApiNotes(
        configuration: Configuration,
        context: CommonBackendContext,
        framework: FrameworkLayout,
    ) : DumpSwiftApiPhase(context, framework) {

        override val isActive: Boolean = SkieFeature.DumpSwiftApiAfterApiNotes in configuration.enabledFeatures
    }

    override fun execute() {
        val moduleName = framework.moduleName
        val apiFileBaseName = "${moduleName}_${this::class.simpleName}"
        val apiFile = context.skieContext.skieBuildDirectory.debug.dumps.apiFile(apiFileBaseName)
        val logFile = context.skieContext.skieBuildDirectory.debug.logs.apiFile(apiFileBaseName)

        Command(
            "zsh",
            "-c",
            """echo "import Kotlin\n:type lookup $moduleName" | swift repl -F "${framework.framework.parentFile.absolutePath}" > "${apiFile.absolutePath}"""",
        ).execute(handleError = false, logFile = logFile)
    }
}
