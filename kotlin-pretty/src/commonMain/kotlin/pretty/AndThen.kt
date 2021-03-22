package pretty

internal typealias Eval<A> = AndThen<Unit, A>

internal fun <A> AndThen.Companion.now(a: A): Eval<A> = AndThen { a }
internal fun <A> AndThen.Companion.later(f: () -> A): Eval<A> = AndThen.Single({ f() }, 0)
internal fun <A> AndThen.Companion.defer(f: () -> Eval<A>): Eval<A> =
    AndThen.Single<Unit, AndThen<Unit, A>>({ f() }, 0).flatMap { it }

internal operator fun <A> Eval<A>.invoke(): A = invoke(Unit)

internal sealed class AndThen<in A, out B> {

    class Single<A, B>(val f: (A) -> B, val index: Int) : AndThen<A, B>()

    class Join<A, B>(val fa: AndThen<A, AndThen<A, B>>) : AndThen<A, B>() {
        override fun toString(): String = "AndThen.Join(...)"
    }

    class Concat<A, E, B>(val left: AndThen<A, E>, val right: AndThen<E, B>) : AndThen<A, B>() {
        override fun toString(): String = "AndThen.Concat(...)"
    }

    @Suppress("UNCHECKED_CAST")
    operator fun invoke(a: A): B = loop(this as AndThen<Any?, Any?>, a, 0)

    fun <X> andThenF(right: AndThen<B, X>): AndThen<A, X> = Concat(this, right)
    fun <X> composeF(right: AndThen<X, A>): AndThen<X, B> = Concat(right, this)

    fun <X> andThen(g: (B) -> X): AndThen<A, X> =
        when (this) {
            // Fusing calls up to a certain threshold
            is Single ->
                if (index != maxStackDepthSize) Single({ a: A -> g(this(a)) }, index + 1)
                else andThenF(AndThen(g))
            else -> andThenF(AndThen(g))
        }

    fun <C> compose(g: (C) -> A): AndThen<C, B> =
        when (this) {
            // Fusing calls up to a certain threshold
            is Single ->
                if (index != maxStackDepthSize) Single({ c: C -> this(g(c)) }, index + 1)
                else composeF(AndThen(g))
            else -> composeF(AndThen(g))
        }


    companion object {
        operator fun <A, B> invoke(f: (A) -> B): AndThen<A, B> = Single(f, 0)

        private const val maxStackDepthSize = 127
    }

    @Suppress("UNCHECKED_CAST")
    private tailrec fun loop(self: AndThen<Any?, Any?>, current: Any?, joins: Int): B = when (self) {
        is Single -> if (joins == 0) self.f(current) as B else loop(self.f(current) as AndThen<Any?, Any?>, null, joins - 1)
        is Join -> loop(
            self.fa.andThen { Concat(AndThen<Any?, Any?> { current }, it) },
            current,
            joins + 1
        )
        is Concat<*, *, *> -> {
            when (val oldLeft = self.left) {
                is Single<*, *> -> {
                    val left = oldLeft as Single<Any?, Any?>
                    val newSelf = self.right as AndThen<Any?, Any?>
                    loop(newSelf, left.f(current), joins)
                }
                is Join<*, *>,
                is Concat<*, *, *> -> loop(
                    rotateAccumulate(self.left as AndThen<Any?, Any?>, self.right as AndThen<Any?, Any?>),
                    current,
                    joins
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private tailrec fun rotateAccumulate(
        left: AndThen<Any?, Any?>,
        right: AndThen<Any?, Any?>
    ): AndThen<Any?, Any?> = when (left) {
        is Concat<*, *, *> -> rotateAccumulate(
            left.left as AndThen<Any?, Any?>,
            (left.right as AndThen<Any?, Any?>).andThenF(right)
        )
        is Join -> Join(left.fa.andThen { it.andThenF(right) })
        is Single<*, *> -> left.andThenF(right)
    }
}

internal fun <A, B, C> AndThen<A, B>.flatMap(f: (B) -> AndThen<A, C>): AndThen<A, C> =
    AndThen.Join(this.andThen(f))
