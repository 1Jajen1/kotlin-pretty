package pretty

import arrow.core.Eval
import arrow.core.extensions.eq
import arrow.core.toT
import arrow.typeclasses.Eq
import pretty.doc.arbitrary.arbitrary
import pretty.pagewidth.arbitrary.arbitrary
import pretty.simpledoc.birecursive.birecursive
import pretty.simpledoc.eq.eq
import propCheck.discardIf
import propCheck.eqv
import propCheck.forAll
import propCheck.instances.arbitrary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class LayoutTest : PropertySpec({
    "layoutPretty should be equal to best" {
        forAll(Doc.arbitrary(Int.arbitrary())) { d ->
            forAll(PageWidth.arbitrary()) { pw ->
                discardIf(
                    pw is PageWidth.Unbounded,
                    Eval.later {
                        d.layoutPretty(pw)
                            .eqv(
                                d.best(pw).fuseText(),
                                Eq { a, b ->
                                    when (a.unDoc) {
                                        is SimpleDocF.Fail -> b.hasFailure()
                                        else -> SimpleDoc.eq(Int.eq()).run { a.eqv(b) }
                                    }
                                }
                            )
                    }
                )
            }
        }
    }
})

fun <A> SimpleDoc<A>.fuseText(): SimpleDoc<A> = cata {
    when (it) {
        is SimpleDocF.Text -> when (val dF = it.doc.unDoc) {
            is SimpleDocF.Text -> SimpleDoc.text(it.str + dF.str, dF.doc)
            else -> SimpleDoc(it)
        }
        else -> SimpleDoc(it)
    }
}

// This is kept to verify against the current one (this one is almost the same as prettyPrinter's)
// this was the function used before the recursion scheme change
fun <A> Doc<A>.best(pw: PageWidth): SimpleDoc<A> = be(pw, 0, 0, Step.Cons(0, this, Step.Empty()))

internal fun ribbonW(w: Int, r: Float): Int = max(0, min(w, round(w * r).toInt()))

sealed class Step<A> {
    class Empty<A> : Step<A>()
    data class Cons<A>(val i: Int, val el: Doc<A>, val tail: Step<A>) : Step<A>()
    data class UndoAnnotation<A>(val tail: Step<A>) : Step<A>()
}

internal fun <A> be(pw: PageWidth, n: Int, k: Int, ls: Step<A>): SimpleDoc<A> = when (ls) {
    is Step.Empty -> SimpleDoc.nil()
    is Step.UndoAnnotation -> SimpleDoc.removeAnnotation(be(pw, n, k, ls.tail))
    is Step.Cons -> when (val dF = ls.el.unDoc) {
        is DocF.Fail -> SimpleDoc(SimpleDocF.Fail())
        is DocF.Nil -> be(pw, n, k, ls.tail)
        is DocF.Combined -> be(pw, n, k, Step.Cons(ls.i, dF.l, Step.Cons(ls.i, dF.r, ls.tail)))
        is DocF.Nest -> be(pw, n, k, Step.Cons(ls.i + dF.i, dF.doc, ls.tail))
        is DocF.Text -> SimpleDoc(SimpleDocF.Text(dF.str, be(pw, n, k + dF.str.length, ls.tail)))
        is DocF.Line -> SimpleDoc(SimpleDocF.Line(ls.i, be(pw, ls.i, ls.i, ls.tail)))
        is DocF.Union ->
            (be(pw, n, k, Step.Cons(ls.i, dF.l, ls.tail)) toT be(pw, n, k, Step.Cons(ls.i, dF.r, ls.tail)))
                .let { (x, y) ->
                    // to prevent returning Line i Fail which is perfectly valid for fit
                    if (x.hasFailure()) y
                    else nicest(
                        pw,
                        n, k,
                        x, y
                    )
                }
        is DocF.Nesting -> be(pw, n, k, Step.Cons(ls.i, dF.doc(ls.i), ls.tail))
        is DocF.Column -> be(pw, n, k, Step.Cons(ls.i, dF.doc(k), ls.tail))
        is DocF.Annotated -> SimpleDoc.addAnnotation(
            dF.ann,
            be(pw, n, k, Step.Cons(ls.i, dF.doc, Step.UndoAnnotation(ls.tail)))
        )
        is DocF.FlatAlt -> be(pw, n, k, Step.Cons(ls.i, dF.l, ls.tail))
        is DocF.WithPageWidth -> be(pw, n, k, Step.Cons(ls.i, dF.doc(pw), ls.tail))
    }
}

fun <A> nicest(pw: PageWidth, n: Int, k: Int, x: SimpleDoc<A>, y: SimpleDoc<A>): SimpleDoc<A> =
    if (x.fits_(min((pw as PageWidth.Available).maxWidth - k, (ribbonW(pw.maxWidth, pw.ribbonFract)) - k + n))) x else y

// simple line fit
fun <A> SimpleDoc<A>.fits_(w: Int): Boolean =
    SimpleDoc.birecursive<A>().run {
        this@fits_.cata<(Int) -> Boolean> {
            { i: Int ->
                i >= 0 && when (val dF = it.fix()) {
                    is SimpleDocF.Text -> dF.doc(i - dF.str.length)
                    is SimpleDocF.AddAnnotation -> dF.doc(i)
                    is SimpleDocF.RemoveAnnotation -> dF.doc(i)
                    is SimpleDocF.Fail -> false
                    else -> true
                }
            }
        }(w)
    }

fun <A> SimpleDoc<A>.hasFailure(): Boolean = SimpleDoc.birecursive<A>().run {
    cata {
        when (val dF = it.fix()) {
            is SimpleDocF.Fail -> true
            is SimpleDocF.Line -> dF.doc
            is SimpleDocF.Text -> dF.doc
            is SimpleDocF.AddAnnotation -> dF.doc
            is SimpleDocF.RemoveAnnotation -> dF.doc
            is SimpleDocF.Nil -> false
        }
    }
}