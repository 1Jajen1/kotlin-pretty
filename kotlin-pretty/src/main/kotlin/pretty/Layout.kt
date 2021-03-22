package pretty

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

typealias FittingFun<A> = SimpleDoc<A>.(pageWidth: PageWidth, minNesting: Int, availableWidth: Int) -> Boolean

fun <A> Doc<A>.layoutPretty(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { _, _, avail ->
    tailrec fun <A> SimpleDoc<A>.test(i: Int): Boolean =
        if (i < 0) false else when (val dF = unDoc()) {
            is SimpleDocF.Text -> dF.doc.test(i - dF.str.length)
            is SimpleDocF.AddAnnotation -> dF.doc.test(i)
            is SimpleDocF.RemoveAnnotation -> dF.doc.test(i)
            is SimpleDocF.Fail -> false
            is SimpleDocF.Nil -> true
            is SimpleDocF.Line -> true
        }
    test(avail)
}

fun <A> Doc<A>.layoutSmart(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { pw, minNest, avail ->
    tailrec fun <A> SimpleDoc<A>.test(pw: PageWidth, m: Int, w: Int): Boolean =
        if (w < 0) false else when (val dF = unDoc()) {
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
    test(pw, minNest, avail)
}

fun <A> Doc<A>.layoutWadlerLeijen(
    pageWidth: PageWidth,
    fits: FittingFun<A>
): SimpleDoc<A> {
    fun selectNicer(lineLen: Int, currCol: Int, x: SimpleDoc<A>, y: SimpleDoc<A>): SimpleDoc<A> =
        when (pageWidth) {
            is PageWidth.Available -> {
                val ribbonW = max(
                    0,
                    min(pageWidth.maxWidth, round(pageWidth.maxWidth * pageWidth.ribbonFract).toInt())
                )
                val colsLeftInRibbon = lineLen + ribbonW - currCol
                val colsLeftInLine = pageWidth.maxWidth - currCol
                if (x.fits(
                        pageWidth,
                        if (y.startsWithLine()) lineLen else currCol,
                        min(colsLeftInLine, colsLeftInRibbon)
                )) x else y
            }
            is PageWidth.Unbounded -> if (x.hasFailOnFirstLine()) y else x
        }

    // TODO Add tests how lazy this is, or if laziness is a benefit to Doc at all
    fun lBest(nl: Int, cc: Int, xs: List<Pair<Int, Doc<A>>?>): SimpleDoc<A> = SimpleDoc(Eval.defer {
        if (xs.isEmpty()) Eval.now(SimpleDocF.Nil)
        else xs.first()?.let { (i, fst) ->
            fst.unDoc.flatMap {
                when (val curr = it) {
                    is DocF.Fail -> Eval.now(SimpleDocF.Fail)
                    is DocF.Nil -> lBest(nl, cc, xs.tail()).unDoc
                    is DocF.Text -> Eval.later { SimpleDocF.Text(curr.str, lBest(nl, cc + curr.str.length, xs.tail())) }
                    is DocF.Line -> Eval.later { SimpleDocF.Line(i, lBest(i, i, xs.tail())) }
                    is DocF.FlatAlt -> lBest(nl, cc, listOf((i to curr.l)) + xs.tail()).unDoc
                    is DocF.Combined -> lBest(
                        nl, cc,
                        listOf((i to curr.l), (i to curr.r)) + xs.tail()
                    ).unDoc
                    is DocF.Nest -> lBest(nl, cc, listOf(((i + curr.i) to curr.doc)) + xs.tail()).unDoc
                    is DocF.Union -> {
                        val lEval = lBest(nl, cc, listOf((i to curr.l)) + xs.tail()).unDoc
                        val rEval = lBest(nl, cc, listOf((i to curr.r)) + xs.tail()).unDoc
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
                    is DocF.Column -> lBest(nl, cc, listOf((i to curr.doc(cc))) + xs.tail()).unDoc
                    is DocF.Nesting -> lBest(nl, cc, listOf((i to curr.doc(i))) + xs.tail()).unDoc
                    is DocF.WithPageWidth -> lBest(
                        nl,
                        cc,
                        listOf((i to curr.doc(pageWidth))) + xs.tail()
                    ).unDoc
                    is DocF.Annotated -> Eval.later {
                        SimpleDocF.AddAnnotation(
                            curr.ann,
                            lBest(nl, cc, listOf((i to curr.doc), null) + xs.tail())
                        )
                    }
                }
            }
        } ?: Eval.later { SimpleDocF.RemoveAnnotation(lBest(nl, cc, xs.tail())) }
    })

    return lBest(0, 0, listOf((0 to this)))
}

fun <A> Doc<A>.layoutCompact(): SimpleDoc<A> {
    fun scan(i: Int, ls: List<Doc<A>>): SimpleDoc<A> =
        if (ls.isEmpty()) SimpleDoc.nil()
        else {
            val (x, xs) = (ls.first() to ls.tail())
            SimpleDoc(
                x.unDoc.flatMap {
                    when (val dF = it) {
                        is DocF.Line -> Eval.now(SimpleDocF.Line(0, scan(0, xs)))
                        is DocF.Fail -> Eval.now(SimpleDocF.Fail)
                        is DocF.Text -> Eval.now(SimpleDocF.Text(dF.str, scan(i + dF.str.length, xs)))
                        is DocF.Nil -> scan(i, xs).unDoc
                        is DocF.FlatAlt -> scan(i, listOf(dF.l) + xs).unDoc
                        is DocF.Combined -> scan(i, listOf(dF.l, dF.r) + xs).unDoc
                        is DocF.Union -> scan(i, listOf(dF.r) + xs).unDoc
                        is DocF.Nest -> scan(i, listOf(dF.doc) + xs).unDoc
                        is DocF.Annotated -> scan(i, listOf(dF.doc) + xs).unDoc
                        is DocF.Column -> scan(i, listOf(dF.doc(i)) + xs).unDoc
                        is DocF.Nesting -> scan(i, listOf(dF.doc(0)) + xs).unDoc
                        is DocF.WithPageWidth -> scan(i, listOf(dF.doc(PageWidth.Unbounded)) + xs).unDoc
                    }
                }
            )
        }
    return scan(0, listOf(this))
}

tailrec fun <A> SimpleDoc<A>.startsWithLine(): Boolean =
    when (val dF = unDoc()) {
        is SimpleDocF.Line -> true
        is SimpleDocF.AddAnnotation -> dF.doc.startsWithLine()
        is SimpleDocF.RemoveAnnotation -> dF.doc.startsWithLine()
        else -> false
    }

tailrec fun <A> SimpleDoc<A>.hasFail(): Boolean = when (val dF = unDoc()) {
    is SimpleDocF.Fail -> true
    is SimpleDocF.Nil -> false
    is SimpleDocF.RemoveAnnotation -> dF.doc.hasFail()
    is SimpleDocF.AddAnnotation -> dF.doc.hasFail()
    is SimpleDocF.Line -> dF.doc.hasFail()
    is SimpleDocF.Text -> dF.doc.hasFail()
}

tailrec fun <A> SimpleDoc<A>.hasFailOnFirstLine(): Boolean = when (val dF = unDoc()) {
    is SimpleDocF.Fail -> true
    is SimpleDocF.Nil -> false
    is SimpleDocF.RemoveAnnotation -> dF.doc.hasFailOnFirstLine()
    is SimpleDocF.AddAnnotation -> dF.doc.hasFailOnFirstLine()
    is SimpleDocF.Line -> false
    is SimpleDocF.Text -> dF.doc.hasFailOnFirstLine()
}

