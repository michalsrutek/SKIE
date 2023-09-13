package co.touchlab.skie.compilerinject.plugin

import co.touchlab.skie.compilerinject.interceptor.PhaseInterceptorRegistrar
import co.touchlab.skie.entrypoint.SkieIrGenerationExtension
import co.touchlab.skie.phases.context.MainSkieContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

class SkieComponentRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = false

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.mainSkieContext = MainSkieContext(configuration)

        IrGenerationExtension.registerExtension(SkieIrGenerationExtension(configuration))

        PhaseInterceptorRegistrar.setupPhaseInterceptors(configuration)
    }
}
