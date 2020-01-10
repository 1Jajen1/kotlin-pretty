package pretty

import arrow.core.*
import arrow.core.extensions.fx
import arrow.free.*
import arrow.syntax.collections.tail
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

typealias FittingFun<A> = SimpleDoc<A>.(pageWidth: PageWidth, minNesting: Int, availableWidth: Option<Int>) -> Boolean

fun <A> Doc<A>.layoutPretty(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { _, _, avail ->
    tailrec fun <A> SimpleDoc<A>.test(i: Int): Boolean =
        if (i < 0) false else when (val dF = unDoc.value()) {
            is SimpleDocF.Text -> dF.doc.test(i - dF.str.length)
            is SimpleDocF.AddAnnotation -> dF.doc.test(i)
            is SimpleDocF.RemoveAnnotation -> dF.doc.test(i)
            is SimpleDocF.Fail -> false
            is SimpleDocF.Nil -> true
            is SimpleDocF.Line -> true
        }
    avail.fold({ true }, { w -> test(w) })
}

fun <A> Doc<A>.layoutSmart(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { pw, minNest, avail ->
    tailrec fun <A> SimpleDoc<A>.test(pw: PageWidth, m: Int, w: Int): Boolean =
        if (w < 0) false else when (val dF = unDoc.value()) {
            is SimpleDocF.Fail -> false
            is SimpleDocF.Nil -> true
            is SimpleDocF.RemoveAnnotation -> dF.doc.test(pw, m, w)
            is SimpleDocF.AddAnnotation -> dF.doc.test(pw, m, w)
            is SimpleDocF.Text -> dF.doc.test(pw, m, w - dF.str.length)
            is SimpleDocF.Line -> when (pw) {
                is PageWidth.Available -> if (m < dF.i) dF.doc.test(pw, m, pw.maxWidth - dF.i) else false
                else -> true
            }
        }
    avail.fold({ true }, { w -> test(pw, minNest, w) })
}

fun <A> Doc<A>.layoutWadlerLeijen(
    pageWidth: PageWidth,
    fits: FittingFun<A>
): SimpleDoc<A> {
    fun selectNicer(n: Int, k: Int, x: SimpleDoc<A>, y: () -> SimpleDoc<A>): SimpleDoc<A> =
        if (x.fits(
                pageWidth,
                min(n, k),
                Option.fx {
                    val colsLeft = !(when (pageWidth) {
                        is PageWidth.Available -> (pageWidth.maxWidth - k).some()
                        else -> none()
                    })
                    val colsLeftRibbon = !Option.fx {
                        val ribbonW = !(when (pageWidth) {
                            is PageWidth.Available ->
                                max(
                                    0,
                                    min(pageWidth.maxWidth, round(pageWidth.maxWidth * pageWidth.ribbonFract).toInt())
                                ).some()
                            else -> none()
                        })
                        n + ribbonW - k
                    }
                    min(colsLeft, colsLeftRibbon)
                })
        ) x else y()

    fun lBest(nl: Int, cc: Int, xs: List<Option<Tuple2<Int, Doc<A>>>>): SimpleDoc<A> = SimpleDoc(Eval.later {
        if (xs.isEmpty()) SimpleDocF.Nil
        else xs.first().fold({ SimpleDocF.RemoveAnnotation(lBest(nl, cc, xs.tail())) }, { (i, fst) ->
            when (val curr = fst.unDoc.value()) {
                is DocF.Fail -> SimpleDocF.Fail
                is DocF.Nil -> lBest(nl, cc, xs.tail()).unDoc.value()
                is DocF.Text -> SimpleDocF.Text(curr.str, lBest(nl, cc + curr.str.length, xs.tail()))
                is DocF.Line -> SimpleDocF.Line(i, lBest(i, i, xs.tail()))
                is DocF.FlatAlt -> lBest(nl, cc, listOf((i toT curr.l).some()) + xs.tail()).unDoc.value()
                is DocF.Combined -> lBest(nl, cc, listOf((i toT curr.l).some(), (i toT curr.r).some()) + xs.tail()).unDoc.value()
                is DocF.Nest -> lBest(nl, cc, listOf(((i + curr.i) toT curr.doc).some()) + xs.tail()).unDoc.value()
                is DocF.Union -> {
                    val lEval = Eval.later { lBest(nl, cc, listOf((i toT curr.l).some()) + xs.tail()) }
                    val rEval = Eval.later { lBest(nl, cc, listOf((i toT curr.r).some()) + xs.tail()) }
                    lEval.flatMap { selectNicer(nl, cc, it) { rEval.value }.unDoc }.value()
                }
                is DocF.Column -> lBest(nl, cc, listOf((i toT curr.doc(cc)).some()) + xs.tail()).unDoc.value()
                is DocF.Nesting -> lBest(nl, cc, listOf((i toT curr.doc(i)).some()) + xs.tail()).unDoc.value()
                is DocF.WithPageWidth -> lBest(nl, cc, listOf((i toT curr.doc(pageWidth)).some()) + xs.tail()).unDoc.value()
                is DocF.Annotated -> SimpleDocF.AddAnnotation(curr.ann, lBest(nl, cc, listOf((i toT curr.doc).some(), None) + xs.tail()))
            }
        })
    })

    return lBest(0, 0, listOf((0 toT this).some()))
}