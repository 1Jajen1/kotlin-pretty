package pretty

import arrow.core.Eval
import arrow.core.extensions.sequence.foldable.foldRight

fun main() {
    val t = (0..100_000).asSequence().foldRight(Eval.now(nil())) { v, acc ->
        acc.map {
            Doc(Eval.now(DocF.Union(v.doc(), it)))
        }
    }.value()

    t.pretty().also { println(it) }

    listOf(1,2,3,4,5,6,1,3123,15215,51)
        .map { it.doc() }.list()
        .layoutPretty(PageWidth.default())
        .renderString().also(::println)
}
