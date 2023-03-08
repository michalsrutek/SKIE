import Foundation

public final class SkieSwiftFlow<T: Swift.AnyObject>: _Concurrency.AsyncSequence, Swift._ObjectiveCBridgeable {

    public typealias AsyncIterator = SkieSwiftFlow<T>.Iterator

    public typealias Element = T

    public typealias _ObjectiveCType = SkieKotlinFlow<Swift.AnyObject>

    internal let flow: Skie.class__org_jetbrains_kotlinx_kotlinx_coroutines_core__kotlinx_coroutines_flow_Flow

    private init(_ flow: SkieKotlinFlow<Swift.AnyObject>) {
        self.flow = flow
    }

    public init(_ flow: SkieKotlinFlow<T>) {
        self.flow = flow
    }

    public func makeAsyncIterator() -> SkieSwiftFlow<T>.Iterator {
        return Iterator(flow: flow)
    }

    public func _bridgeToObjectiveC() -> _ObjectiveCType {
        return SkieKotlinFlow(delegate: flow)
    }

    public static func _forceBridgeFromObjectiveC(_ source: _ObjectiveCType, result: inout SkieSwiftFlow<T>?) {
        result = fromObjectiveC(source)
    }

    public static func _conditionallyBridgeFromObjectiveC(_ source: _ObjectiveCType, result: inout SkieSwiftFlow<T>?) -> Bool {
        result = fromObjectiveC(source)
        return true
    }

    public static func _unconditionallyBridgeFromObjectiveC(_ source: _ObjectiveCType?) -> SkieSwiftFlow<T> {
        return fromObjectiveC(source)
    }

    private static func fromObjectiveC(_ source: _ObjectiveCType?) -> SkieSwiftFlow<T> {
        return SkieSwiftFlow(source!)
    }

    public class Iterator: AsyncIteratorProtocol {

        public typealias Element = T

        private let iterator: SkieColdFlowIterator<T>

        init(flow: Skie.class__org_jetbrains_kotlinx_kotlinx_coroutines_core__kotlinx_coroutines_flow_Flow) {
            iterator = SkieColdFlowIterator(flow: flow)
        }

        deinit {
            iterator.cancel()
        }

        public func next() async -> Element? {
            let hasNext = try? await SkieColdFlowIteratorKt.hasNext(iterator)

            if (hasNext?.boolValue ?? false) {
                return .some(iterator.next()!)
            } else {
                return nil
            }
        }
    }
}
