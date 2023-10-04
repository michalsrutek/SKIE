package co.touchlab.skie.swiftmodel.callable.parameter

import co.touchlab.skie.phases.SkiePhase
import co.touchlab.skie.sir.flowMappingStrategy
import co.touchlab.skie.sir.type.SirType
import co.touchlab.skie.swiftmodel.callable.parameter.KotlinValueParameterSwiftModel.Origin
import co.touchlab.skie.swiftmodel.type.FlowMappingStrategy
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor

class ActualKotlinValueParameterSwiftModel(
    private val core: KotlinParameterSwiftModelCore,
    private val functionDescriptor: FunctionDescriptor,
    private val parameterDescriptor: ParameterDescriptor?,
    override val position: Int,
    private val skieContext: SkiePhase.Context,
    private val getParameterType: (flowMappingStrategy: FlowMappingStrategy) -> SirType,
) : MutableKotlinValueParameterSwiftModel {

    override val origin: Origin = core.getOrigin(parameterDescriptor)

    // override val original: KotlinValueParameterSwiftModel = OriginalKotlinValueParameterSwiftModel(this)

    // override val isChanged: Boolean
    //     get() = argumentLabel != original.argumentLabel || flowMappingStrategy != original.flowMappingStrategy

    override val objCType: ObjCType
        get() = core.getObjCType(functionDescriptor, parameterDescriptor, flowMappingStrategy)

    override var flowMappingStrategy: FlowMappingStrategy = with(skieContext) {
        functionDescriptor.flowMappingStrategy
    }

    override fun toString(): String = origin.toString()
}
