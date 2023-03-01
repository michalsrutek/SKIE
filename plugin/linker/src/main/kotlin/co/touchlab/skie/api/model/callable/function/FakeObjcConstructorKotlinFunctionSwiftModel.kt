@file:Suppress("invisible_reference", "invisible_member")

package co.touchlab.skie.api.model.callable.function

import co.touchlab.skie.api.model.callable.parameter.ActualKotlinValueParameterSwiftModel
import co.touchlab.skie.api.model.callable.parameter.KotlinParameterSwiftModelCore
import co.touchlab.skie.api.model.factory.ObjCTypeProvider
import co.touchlab.skie.plugin.api.model.MutableSwiftModelScope
import co.touchlab.skie.plugin.api.model.SwiftModelVisibility
import co.touchlab.skie.plugin.api.model.callable.KotlinCallableMemberSwiftModelVisitor
import co.touchlab.skie.plugin.api.model.callable.KotlinDirectlyCallableMemberSwiftModelVisitor
import co.touchlab.skie.plugin.api.model.callable.MutableKotlinCallableMemberSwiftModelVisitor
import co.touchlab.skie.plugin.api.model.callable.MutableKotlinDirectlyCallableMemberSwiftModel
import co.touchlab.skie.plugin.api.model.callable.MutableKotlinDirectlyCallableMemberSwiftModelVisitor
import co.touchlab.skie.plugin.api.model.callable.function.KotlinFunctionSwiftModel
import co.touchlab.skie.plugin.api.model.callable.parameter.MutableKotlinValueParameterSwiftModel
import co.touchlab.skie.plugin.api.model.type.TypeSwiftModel
import co.touchlab.skie.plugin.api.model.type.bridge.MethodBridgeParameter
import co.touchlab.skie.plugin.api.model.type.bridge.valueParametersAssociated
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCNoneExportScope
import org.jetbrains.kotlin.descriptors.ClassDescriptor

internal class FakeObjcConstructorKotlinFunctionSwiftModel(
    private val baseModel: KotlinFunctionSwiftModelWithCore,
    receiverDescriptor: ClassDescriptor,
    private val swiftModelScope: MutableSwiftModelScope,
    objCTypeProvider: ObjCTypeProvider,
) : KotlinFunctionSwiftModelWithCore by baseModel {

    override val directlyCallableMembers: List<MutableKotlinDirectlyCallableMemberSwiftModel> = listOf(this)

    override val valueParameters: List<MutableKotlinValueParameterSwiftModel> by lazy {
        core.getMethodBridge(descriptor)
            .valueParametersAssociated(descriptor)
            .filterNot { it.first is MethodBridgeParameter.ValueParameter.ErrorOutParameter }
            .zip(core.swiftFunctionName.argumentLabels)
            .map { (parameterBridgeWithDescriptor, argumentLabel) ->
                KotlinParameterSwiftModelCore(
                    argumentLabel = argumentLabel,
                    parameterBridge = parameterBridgeWithDescriptor.first,
                    baseParameterDescriptor = parameterBridgeWithDescriptor.second,
                    allArgumentLabels = core.swiftFunctionName.argumentLabels,
                    getObjCType = { functionDescriptor, parameterDescriptor, isFlowMappingEnabled ->
                        objCTypeProvider.getFunctionParameterType(
                            function = functionDescriptor,
                            parameter = parameterDescriptor,
                            bridge = parameterBridgeWithDescriptor.first,
                            isFlowMappingEnabled = isFlowMappingEnabled,
                            genericExportScope = ObjCNoneExportScope,
                        )
                    }
                ) to parameterBridgeWithDescriptor.second
            }
            .mapIndexed { index, (core, parameterDescriptor) ->
                ActualKotlinValueParameterSwiftModel(
                    core,
                    descriptor,
                    parameterDescriptor,
                    index,
                ) { isFlowMappingEnabled ->
                    with(swiftModelScope) {
                        descriptor.getParameterType(
                            parameterDescriptor,
                            core.parameterBridge,
                            receiver.swiftGenericExportScope,
                            isFlowMappingEnabled,
                        )
                    }
                }
            }
    }

    override val receiver: TypeSwiftModel by lazy {
        with(swiftModelScope) {
            receiverDescriptor.swiftModel
        }
    }

    override var visibility: SwiftModelVisibility
        get() = SwiftModelVisibility.Removed
        set(value) {}

    override val reference: String
        get() = baseModel.core.reference(baseModel)

    override val name: String
        get() = baseModel.core.name(baseModel)

    override val original: KotlinFunctionSwiftModel = OriginalKotlinFunctionSwiftModel(this)

    override fun <OUT> accept(visitor: KotlinCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: KotlinDirectlyCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: MutableKotlinCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: MutableKotlinDirectlyCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)
}
