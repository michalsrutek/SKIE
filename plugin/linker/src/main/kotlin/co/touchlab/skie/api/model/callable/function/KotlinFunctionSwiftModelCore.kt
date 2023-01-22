@file:Suppress("invisible_reference", "invisible_member")

package co.touchlab.skie.api.model.callable.function

import co.touchlab.skie.api.model.DescriptorBridgeProvider
import co.touchlab.skie.api.model.MethodBridgeParameter
import co.touchlab.skie.api.model.callable.parameter.KotlinParameterSwiftModelCore
import co.touchlab.skie.api.model.valueParametersAssociated
import co.touchlab.skie.plugin.api.model.SwiftModelVisibility
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCExportNamer
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor

internal class KotlinFunctionSwiftModelCore(
    val descriptor: FunctionDescriptor,
    private val namer: ObjCExportNamer,
    bridgeProvider: DescriptorBridgeProvider,
) {
    val methodBridge by lazy { bridgeProvider.bridgeMethod(descriptor) }

    private val swiftFunctionName = run {
        val swiftName = namer.getSwiftName(descriptor.original)

        val (identifier, argumentLabelsString) = swiftNameComponentsRegex.matchEntire(swiftName)?.destructured
            ?: error("Unable to parse swift name: $swiftName")

        val argumentLabels = argumentLabelsString.split(":").map { it.trim() }.filter { it.isNotEmpty() }

        SwiftFunctionName(identifier, argumentLabels)
    }

    var identifier: String = swiftFunctionName.identifier

    var visibility: SwiftModelVisibility = SwiftModelVisibility.Visible

    fun getParameterCoresWithDescriptors(
        functionDescriptor: FunctionDescriptor,
    ): List<Pair<KotlinParameterSwiftModelCore, ParameterDescriptor?>> =
        functionDescriptor.getParameterBridgesWithDescriptors()
            .zip(parameterCores)
            .map { it.second to it.first.second }

    private val parameterCores: List<KotlinParameterSwiftModelCore> =
        descriptor.getParameterBridgesWithDescriptors()
            .zip(swiftFunctionName.argumentLabels)
            .map { (parameterBridgeWithDescriptor, argumentLabel) ->
                KotlinParameterSwiftModelCore(
                    argumentLabel = argumentLabel,
                    parameterBridge = parameterBridgeWithDescriptor.first,
                    baseParameterDescriptor = parameterBridgeWithDescriptor.second,
                    allArgumentLabels = swiftFunctionName.argumentLabels,
                )
            }

    private fun FunctionDescriptor.getParameterBridgesWithDescriptors(): List<Pair<MethodBridgeParameter.ValueParameter, ParameterDescriptor?>> =
        methodBridge
            .valueParametersAssociated(this)
            .filterNot { it.first is MethodBridgeParameter.ValueParameter.ErrorOutParameter }

    val objCSelector: String = namer.getSelector(descriptor.original)

    private data class SwiftFunctionName(val identifier: String, val argumentLabels: List<String>)

    private companion object {

        val swiftNameComponentsRegex = "(.+?)\\((.*?)\\)".toRegex()
    }
}
