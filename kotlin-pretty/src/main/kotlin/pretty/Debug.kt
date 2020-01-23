package pretty

import arrow.core.AndThen
import arrow.core.Eval
import arrow.core.Tuple2
import arrow.core.extensions.eval.applicative.applicative
import arrow.core.toT

sealed class Diag<out A> {
    object Fail : Diag<Nothing>() {
        override fun toString(): String = "Fail"
    }
    object Nil : Diag<Nothing>() {
        override fun toString(): String = "Nil"
    }
    data class Text(val str: String) : Diag<Nothing>()
    object Line : Diag<Nothing>() {
        override fun toString(): String = "Line"
    }
    data class FlatAlt<A>(val l: Diag<A>, val r: Diag<A>): Diag<A>()
    data class Combined<A>(val l: Diag<A>, val r: Diag<A>): Diag<A>()
    data class Union<A>(val l: Diag<A>, val r: Diag<A>): Diag<A>()
    data class Nest<A>(val i: Int, val doc: Diag<A>): Diag<A>()
    data class Annotated<A>(val ann: A, val doc: Diag<A>): Diag<A>()
    data class WithPageWidth<A>(val sample: List<Tuple2<PageWidth, Diag<A>>>): Diag<A>()
    data class Nesting<A>(val sample: List<Tuple2<Int, Diag<A>>>): Diag<A>()
    data class Column<A>(val sample: List<Tuple2<Int, Diag<A>>>): Diag<A>()
}

// tailrec with cps if this ever overflows
fun <A> Doc<A>.diag(
    col: List<Int> = listOf(10),
    pw: List<PageWidth> = listOf(PageWidth.default()),
    nest: List<Int> = listOf(10)
): Diag<A> = when (val dF = unDoc.value()) {
    is DocF.Fail -> Diag.Fail
    is DocF.Nil -> Diag.Nil
    is DocF.Line -> Diag.Line
    is DocF.Text -> Diag.Text(dF.str)
    is DocF.Nest -> Diag.Nest(dF.i, dF.doc.diag(col, pw, nest))
    is DocF.Annotated -> Diag.Annotated(dF.ann, dF.doc.diag(col, pw, nest))
    is DocF.FlatAlt -> Diag.FlatAlt(dF.l.diag(col, pw, nest), dF.r.diag(col, pw, nest))
    is DocF.Union -> Diag.Union(dF.l.diag(col, pw, nest), dF.r.diag(col, pw, nest))
    is DocF.Combined -> Diag.Combined(dF.l.diag(col, pw, nest), dF.r.diag(col, pw, nest))
    is DocF.WithPageWidth -> Diag.WithPageWidth(pw.map { it toT dF.doc(it).diag(col, pw, nest) })
    is DocF.Column -> Diag.Column(col.map { it toT dF.doc(it).diag(col, pw, nest) })
    is DocF.Nesting -> Diag.Nesting(nest.map { it toT dF.doc(it).diag(col, pw, nest) })
}

fun main() {
    val a = ("x".text() + line()).group().group().group().group().diag()
    val b = ("x".text() + line()).group2().group2().group2().group2().diag()

    val c = hardLine() + lineBreak()
    val dc = c.diag()

    val c1 = c.group2()
    val c2 = c.group()

    val dc1 = c1.diag()
    val dc2 = c2.diag()

    val eq = c1.pretty(80) == c2.pretty(80)

    println(a.toString())
    println(b.toString())
}

fun <A> Doc<A>.group2(): Doc<A> = Doc(unDoc.flatMap {
    when (val dF = it) {
        is DocF.FlatAlt -> dF.r.flatten2().flatMap {
            when (val res = it) {
                is FlattenResult.HasLine -> dF.l.unDoc
                is FlattenResult.NoChange -> Eval.now(DocF.Union(dF.r, dF.l))
                is FlattenResult.Flattened -> Eval.now(DocF.Union(res.a, dF.l))
            }
        }

        is DocF.Combined -> Eval.applicative().mapN(dF.l.flatten2(), dF.r.flatten2()) { (l, r) ->
            when (l) {
                is FlattenResult.HasLine -> dF
                is FlattenResult.NoChange -> when (r) {
                    is FlattenResult.HasLine -> dF
                    is FlattenResult.NoChange -> dF
                    is FlattenResult.Flattened -> DocF.Combined(dF.l, Doc(Eval.now(DocF.Union(r.a, dF.r))))
                }
                is FlattenResult.Flattened -> when (r) {
                    is FlattenResult.HasLine -> dF
                    is FlattenResult.NoChange -> DocF.Combined(Doc(Eval.now(DocF.Union(l.a, dF.l))), dF.r)
                    is FlattenResult.Flattened -> DocF.Union(
                        l.a + r.a,
                        Doc(Eval.now(dF))
                    )
                }
            }
        }

        is DocF.Annotated -> Eval.now(DocF.Annotated(dF.ann, dF.doc.group2()))
        is DocF.Nest -> Eval.now(DocF.Nest(dF.i, dF.doc.group2()))

        is DocF.Column -> Eval.now(DocF.Column(AndThen(dF.doc).andThen { it.group2() }))
        is DocF.Nesting -> Eval.now(DocF.Nesting(AndThen(dF.doc).andThen { it.group2() }))
        is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(AndThen(dF.doc).andThen { it.group2() }))

        is DocF.Union -> Eval.now(dF)
        is DocF.Line -> Eval.now(dF)
        is DocF.Text -> Eval.now(dF)
        is DocF.Nil -> Eval.now(dF)

        is DocF.Fail -> Eval.now(dF)
    }
})

fun <A> Doc<A>.flatten2(): Eval<FlattenResult<Doc<A>>> = unDoc.flatMap {
    when (val dF = it) {
        is DocF.FlatAlt -> dF.r.flatten2().map {
            when (it) {
                is FlattenResult.HasLine -> FlattenResult.HasLine
                is FlattenResult.NoChange -> FlattenResult.Flattened(dF.r)
                is FlattenResult.Flattened -> FlattenResult.Flattened(it.a)
            }
        }

        is DocF.Union -> Eval.now(FlattenResult.Flattened(dF.l))

        is DocF.Combined -> Eval.applicative().mapN(dF.l.flatten2(), dF.r.flatten2()) { (l, r) ->
            when (l) {
                is FlattenResult.HasLine -> FlattenResult.HasLine
                is FlattenResult.NoChange -> when (r) {
                    is FlattenResult.HasLine -> FlattenResult.HasLine
                    is FlattenResult.NoChange -> FlattenResult.NoChange
                    is FlattenResult.Flattened -> FlattenResult.Flattened(Doc(Eval.now(DocF.Combined(dF.l, r.a))))
                }
                is FlattenResult.Flattened -> when (r) {
                    is FlattenResult.HasLine -> FlattenResult.HasLine
                    is FlattenResult.NoChange -> FlattenResult.Flattened(Doc(Eval.now(DocF.Combined(l.a, dF.r))))
                    is FlattenResult.Flattened -> FlattenResult.Flattened(Doc(Eval.now(DocF.Combined(l.a, r.a))))
                }
            }
        }

        is DocF.Nest -> dF.doc.flatten2().map {
            it.map {
                Doc(Eval.now(DocF.Nest(dF.i, it)))
            }
        }
        is DocF.Annotated -> dF.doc.flatten2().map {
            it.map {
                Doc(Eval.now(DocF.Annotated(dF.ann, it)))
            }
        }

        is DocF.Column ->
            Eval.now(FlattenResult.Flattened(Doc(Eval.now(DocF.Column(AndThen(dF.doc).andThen { it.flatten() })))))
        is DocF.Nesting ->
            Eval.now(FlattenResult.Flattened(Doc(Eval.now(DocF.Nesting(AndThen(dF.doc).andThen { it.flatten() })))))
        is DocF.WithPageWidth ->
            Eval.now(FlattenResult.Flattened(Doc(Eval.now(DocF.WithPageWidth(AndThen(dF.doc).andThen { it.flatten() })))))

        is DocF.Line -> Eval.now(FlattenResult.HasLine)

        is DocF.Text -> Eval.now(FlattenResult.NoChange)
        is DocF.Nil -> Eval.now(FlattenResult.NoChange)

        is DocF.Fail -> Eval.now(FlattenResult.NoChange)
    }
}

// Idea: Add FlatAlt case here so that Combined Line (FlatAlt x y) turns into Cat Line x
sealed class FlattenResult<out A> {
    object HasLine : FlattenResult<Nothing>()
    object NoChange : FlattenResult<Nothing>()
    data class Flattened<A>(val a: A): FlattenResult<A>()

    fun <B> map(f: (A) -> B): FlattenResult<B> = when (this) {
        is HasLine -> HasLine
        is NoChange -> NoChange
        is Flattened -> Flattened(f(a))
    }
}
