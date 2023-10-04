@file:Suppress("invisible_reference", "invisible_member")

package co.touchlab.skie.swiftmodel.callable.function

import co.touchlab.skie.phases.SkiePhase
import co.touchlab.skie.sir.element.SirCallableDeclaration
import co.touchlab.skie.sir.element.SirConstructor
import co.touchlab.skie.sir.element.SirFunction
import co.touchlab.skie.sir.element.SirValueParameter
import co.touchlab.skie.sir.type.SirType
import co.touchlab.skie.swiftmodel.MutableSwiftModelScope
import co.touchlab.skie.swiftmodel.callable.KotlinCallableMemberSwiftModelVisitor
import co.touchlab.skie.swiftmodel.callable.KotlinDirectlyCallableMemberSwiftModelVisitor
import co.touchlab.skie.swiftmodel.callable.MutableKotlinCallableMemberSwiftModelVisitor
import co.touchlab.skie.swiftmodel.callable.MutableKotlinDirectlyCallableMemberSwiftModel
import co.touchlab.skie.swiftmodel.callable.MutableKotlinDirectlyCallableMemberSwiftModelVisitor
import co.touchlab.skie.swiftmodel.callable.parameter.ActualKotlinValueParameterSwiftModel
import co.touchlab.skie.swiftmodel.callable.parameter.KotlinParameterSwiftModelCore
import co.touchlab.skie.swiftmodel.callable.parameter.MutableKotlinValueParameterSwiftModel
import co.touchlab.skie.swiftmodel.callable.swiftGenericExportScope
import co.touchlab.skie.swiftmodel.factory.ObjCTypeProvider
import co.touchlab.skie.swiftmodel.type.KotlinTypeSwiftModel
import co.touchlab.skie.swiftmodel.type.bridge.MethodBridgeParameter
import co.touchlab.skie.swiftmodel.type.bridge.valueParametersAssociated
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCNoneExportScope
import org.jetbrains.kotlin.descriptors.ClassDescriptor

class FakeObjcConstructorKotlinFunctionSwiftModel(
    private val baseModel: KotlinFunctionSwiftModelWithCore,
    ownerDescriptor: ClassDescriptor,
    private val swiftModelScope: MutableSwiftModelScope,
    objCTypeProvider: ObjCTypeProvider,
    skieContext: SkiePhase.Context,
    kotlinSirConstructorFactory: () -> SirConstructor,
) : KotlinFunctionSwiftModelWithCore by baseModel {

    override val directlyCallableMembers: List<MutableKotlinDirectlyCallableMemberSwiftModel> = listOf(this)

    override var bridgedSirCallableDeclaration: SirCallableDeclaration?
        get() = bridgedSirConstructor
        set(value) {
            bridgedSirConstructor = value as SirConstructor?
        }

    override val kotlinSirConstructor: SirConstructor by lazy {
        kotlinSirConstructorFactory()
    }

    override val kotlinSirCallableDeclaration: SirCallableDeclaration
        get() = kotlinSirConstructor

    override var bridgedSirConstructor: SirConstructor? = null

    override val kotlinSirFunction: SirFunction
        get() = error("FakeObjcConstructorKotlinFunctionSwiftModel does not have a primarySirFunction.")

    override var bridgedSirFunction: SirFunction?
        get() = error("FakeObjcConstructorKotlinFunctionSwiftModel $this does not have a bridgedSirFunction.")
        set(_) = error("FakeObjcConstructorKotlinFunctionSwiftModel $this does not have a bridgedSirFunction.")

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
                    getObjCType = { functionDescriptor, parameterDescriptor, flowMappingStrategy ->
                        objCTypeProvider.getFunctionParameterType(
                            function = functionDescriptor,
                            parameter = parameterDescriptor,
                            bridge = parameterBridgeWithDescriptor.first,
                            flowMappingStrategy = flowMappingStrategy,
                            genericExportScope = ObjCNoneExportScope,
                        )
                    },
                ) to parameterBridgeWithDescriptor.second
            }
            .mapIndexed { index, (core, parameterDescriptor) ->
                ActualKotlinValueParameterSwiftModel(
                    core,
                    descriptor,
                    parameterDescriptor,
                    index,
                    skieContext,
                ) { flowMappingStrategy ->
                    with(swiftModelScope) {
                        descriptor.getParameterType(
                            parameterDescriptor,
                            core.parameterBridge,
                            swiftGenericExportScope,
                            flowMappingStrategy,
                        )
                    }
                }
            }
    }

    override val owner: KotlinTypeSwiftModel by lazy {
        with(swiftModelScope) {
            ownerDescriptor.swiftModel
        }
    }

    override val receiver: SirType by lazy {
        with(swiftModelScope) {
            ownerDescriptor.receiverType()
        }
    }
    override fun <OUT> accept(visitor: KotlinCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: KotlinDirectlyCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: MutableKotlinCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)

    override fun <OUT> accept(visitor: MutableKotlinDirectlyCallableMemberSwiftModelVisitor<OUT>): OUT =
        visitor.visit(this)
}
