package pretty

import arrow.core.Eval

// TODO write a gen for doc to test the new layoutWadlerLeijen function
fun main() {
    val ds = (0..10000).fold("-".text()) { acc, v ->
        (Doc(Eval.now(DocF.Union(v.doc(), acc))))
    }.group()

    ds.renderPretty().renderString().also(::println)

    val f = T.L(10)
    val g = T.L(1000)
    val e = T.B(listOf(f, g, g))
    val d = T.B(listOf(f, g, g, g,g ,g ,e, e, e))
    val c = T.L(100)
    val b = T.B(listOf(e, d, c))
    val a = T.B(listOf(b, b, b, b, b, b, b, b, b, b, b))

    val ad = a.doc()
    // ad.printDoc().also(::println)
    val s = ad.layoutSmart(PageWidth.Available(80, 0.4F))
        .renderString().also(::println)
}

sealed class T {
    data class L(val x: Int): T()
    data class B(val bs: List<T>): T()

    fun doc(): Doc<Nothing> = when (this) {
        is L -> x.doc()
        is B -> "x:".text() softLine (bs
            .map { it.doc().nest(4) }
            .list().hang(4))
    }
}

fun <A> Doc<A>.printDoc(): String = cata<A, Doc<Nothing>> {
    when (val dF = it) {
        is DocF.FlatAlt -> "FlatAlt:".text() + hardLine() +
                ("-> ".text() + dF.l).nest(2) + hardLine() +
                ("-> ".text() + dF.r).nest(2)
        is DocF.Combined -> "Combined:".text() + hardLine() +
                ("-> ".text() + dF.l).nest(2) + hardLine() +
                ("-> ".text() + dF.r).nest(2)
        is DocF.Nest -> "Nest(${dF.i})".text() + hardLine() +
                ("-> ".text() + dF.doc).nest(2)
        is DocF.Fail -> "Fail".text()
        is DocF.Nil -> "Nil".text()
        is DocF.Line -> "Line".text()
        is DocF.Text -> "Text(${dF.str})".text()
        is DocF.Annotated -> "Annotated".text() // TODO
        is DocF.Union -> "Union:".text() + hardLine() +
                ("-> ".text() + dF.l).nest(2) + hardLine() +
                ("-> ".text() + dF.r).nest(2)
        is DocF.Nesting -> "Nesting:".text() + hardLine() +
                ("-> ".text() + dF.doc(0)).nest(2)
        is DocF.Column -> "Column:".text() + hardLine() +
                ("-> ".text() + dF.doc(0)).nest(2)
        is DocF.WithPageWidth -> "WithPageWidth:".text() + hardLine() +
                ("-> ".text() + dF.doc(PageWidth.Unbounded)).nest(2)
    }
}.renderPretty().renderString()