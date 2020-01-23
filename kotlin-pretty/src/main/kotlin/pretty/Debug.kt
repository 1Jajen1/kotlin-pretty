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

