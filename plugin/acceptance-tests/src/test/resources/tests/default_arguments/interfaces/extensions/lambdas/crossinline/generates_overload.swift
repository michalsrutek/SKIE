let a: A = AImpl()

let r0 = AKt.foo(a, i: { KotlinInt(integerLiteral: $0.intValue + 1) }, k: { $0 })
let r1 = AKt.foo(a, k: { $0 })

if r0 == r1 && r1 == 0 {
    exit(0)
} else {
    fatalError("r0: \(r0), r1: \(r1)")
}
