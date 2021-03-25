package pretty.lazy

// Copypasta from arrow-core
// See https://github.com/arrow-kt/arrow/blob/c2fe8ad96dd3650dfb1d25320d8e4b25e2100cea/arrow-libs/core/arrow-core-data/src/main/kotlin/arrow/core/Eval.kt
public sealed class Eval<out A> {
    internal class Pure<A>(val a: A): Eval<A>()
    internal class Defer<A>(eval: () -> Eval<A>): Eval<A>() {
        val res by lazy(eval)
    }
    internal class FlatMap<A>(val eval: Eval<Any?>, val ff: (Any?) -> Eval<Any?>): Eval<A>()
    internal class Memo<A>(var res: Any?): Eval<A>()

    public operator fun invoke(): A = when (this) {
        is Memo -> go().also { result -> this.res = result }
        else -> go()
    } as A

    public fun memo(): Eval<A> = Memo(this)

    public companion object {
        public fun <A> now(a: A): Eval<A> = Pure(a)
        public fun <A> later(f: () -> A): Eval<A> = Defer { now(f()) }
        public fun <A> defer(f: () -> Eval<A>): Eval<A> = Defer(f)
    }
}

private fun Eval<Any?>.go(): Any? {
    var curr = this
    val fs = mutableListOf<(Any?) -> Eval<Any?>>()
    while (true) {
        when (curr) {
            is Eval.Pure ->
                if (fs.isEmpty()) return curr.a
                else {
                    curr = fs.removeAt(0)(curr.a)
                }
            is Eval.Memo ->
                if (curr.res is Eval<*>) curr = curr.res as Eval<Any?>
                else {
                    if (fs.isEmpty()) return curr.res
                    else curr = fs.removeAt(0)(curr.res)
                }
            is Eval.Defer -> curr = curr.res
            is Eval.FlatMap -> {
                fs.add(0, curr.ff)
                curr = curr.eval
            }
        }
    }
}

public fun <A, B> Eval<A>.map(f: (A) -> B): Eval<B> = flatMap { Eval.now(f(it)) }

public fun <A, B> Eval<A>.flatMap(f: (A) -> Eval<B>): Eval<B> = when (this) {
    is Eval.Pure -> f(a)
    is Eval.Memo ->
        if (res is Eval<*>) Eval.Memo((res as Eval<A>).flatMap(f))
        else Eval.Memo(f(res as A))
    else -> Eval.FlatMap(this, f as (Any?) -> Eval<Any?>)
}
