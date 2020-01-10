package pretty

import arrow.core.*
import arrow.core.extensions.fx
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
                is PageWidth.Available -> if (m < dF.i)
                    dF.doc.test(pw, m, pw.maxWidth - dF.i)
                else true
                else -> true
            }
        }
    avail.fold({ true }, { w -> test(pw, minNest, w) })
}

fun <A> Doc<A>.layoutWadlerLeijen(
    pageWidth: PageWidth,
    fits: FittingFun<A>
): SimpleDoc<A> {
    fun selectNicer(lineLen: Int, currCol: Int, x: SimpleDoc<A>, y: SimpleDoc<A>): SimpleDoc<A> =
        if (x.fits(
                pageWidth,
                if (y.startsWithLine().value()) lineLen else currCol,
                Option.fx {
                    val colsLeft = !(when (pageWidth) {
                        is PageWidth.Available -> (pageWidth.maxWidth - currCol).some()
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
                        lineLen + ribbonW - currCol
                    }
                    min(colsLeft, colsLeftRibbon)
                })
        ) x else y

    fun lBest(nl: Int, cc: Int, xs: List<Option<Tuple2<Int, Doc<A>>>>): SimpleDoc<A> = SimpleDoc(Eval.defer {
        if (xs.isEmpty()) Eval.now(SimpleDocF.Nil)
        else xs.first().fold({ Eval.later { SimpleDocF.RemoveAnnotation(lBest(nl, cc, xs.tail())) } }, { (i, fst) ->
            fst.unDoc.flatMap {
                when (val curr = it) {
                    is DocF.Fail -> Eval.now(SimpleDocF.Fail)
                    is DocF.Nil -> lBest(nl, cc, xs.tail()).unDoc
                    is DocF.Text -> Eval.later { SimpleDocF.Text(curr.str, lBest(nl, cc + curr.str.length, xs.tail())) }
                    is DocF.Line -> Eval.later { SimpleDocF.Line(i, lBest(i, i, xs.tail())) }
                    is DocF.FlatAlt -> lBest(nl, cc, listOf((i toT curr.l).some()) + xs.tail()).unDoc
                    is DocF.Combined -> lBest(
                        nl, cc,
                        listOf((i toT curr.l).some(), (i toT curr.r).some()) + xs.tail()
                    ).unDoc
                    is DocF.Nest -> lBest(nl, cc, listOf(((i + curr.i) toT curr.doc).some()) + xs.tail()).unDoc
                    is DocF.Union -> {
                        val lEval = lBest(nl, cc, listOf((i toT curr.l).some()) + xs.tail()).unDoc
                        val rEval = lBest(nl, cc, listOf((i toT curr.r).some()) + xs.tail()).unDoc
                        lEval.flatMap { l ->
                            rEval.flatMap { r ->
                                selectNicer(
                                    nl,
                                    cc,
                                    SimpleDoc(Eval.now(l)),
                                    SimpleDoc(Eval.now(r))
                                ).unDoc
                            }
                        }
                    }
                    is DocF.Column -> lBest(nl, cc, listOf((i toT curr.doc(cc)).some()) + xs.tail()).unDoc
                    is DocF.Nesting -> lBest(nl, cc, listOf((i toT curr.doc(i)).some()) + xs.tail()).unDoc
                    is DocF.WithPageWidth -> lBest(
                        nl,
                        cc,
                        listOf((i toT curr.doc(pageWidth)).some()) + xs.tail()
                    ).unDoc
                    is DocF.Annotated -> Eval.later {
                        SimpleDocF.AddAnnotation(
                            curr.ann,
                            lBest(nl, cc, listOf((i toT curr.doc).some(), None) + xs.tail())
                        )
                    }
                }
            }
        })
    })

    return lBest(0, 0, listOf((0 toT this).some()))
}

fun <A> SimpleDoc<A>.startsWithLine(): Eval<Boolean> = unDoc.flatMap {
    when (val dF = it) {
        is SimpleDocF.Line -> Eval.now(true)
        is SimpleDocF.AddAnnotation -> dF.doc.startsWithLine()
        is SimpleDocF.RemoveAnnotation -> dF.doc.startsWithLine()
        else -> Eval.now(false)
    }
}
