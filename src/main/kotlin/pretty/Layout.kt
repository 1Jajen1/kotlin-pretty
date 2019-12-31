package pretty

import arrow.core.Option
import arrow.core.extensions.fx
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import arrow.free.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

typealias FittingFun<A> = SimpleDoc<A>.(pageWidth: PageWidth, minNesting: Int, availableWidth: Option<Int>) -> Boolean

fun <A> Doc<A>.layoutPretty(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { _, _, avail ->
    avail.fold({ true }, { w ->
        cata<A, (Int) -> TrampolineF<Boolean>> {
            { i: Int ->
                Trampoline.defer {
                    if (i >= 0) when (it) {
                        is SimpleDocF.Text -> it.doc(i - it.str.length)
                        is SimpleDocF.AddAnnotation -> it.doc(i)
                        is SimpleDocF.RemoveAnnotation -> it.doc(i)
                        is SimpleDocF.Fail -> Free.just(false)
                        else -> Free.just(true)
                    } else Free.just(false)
                }
            }
        }(w).runT()
    })
}

fun <A> Doc<A>.layoutSmart(pageWidth: PageWidth): SimpleDoc<A> = layoutWadlerLeijen(pageWidth) { pw, minNest, avail ->
    avail.fold({ true }, { w ->
        cata<A, (PageWidth, Int, Int) -> TrampolineF<Boolean>> {
            { pw, m, w ->
                Trampoline.defer {
                    if (w >= 0) when (it) {
                        is SimpleDocF.Fail -> Free.just(false)
                        is SimpleDocF.Nil -> Free.just(true)
                        is SimpleDocF.RemoveAnnotation -> it.doc(pw, m, w)
                        is SimpleDocF.AddAnnotation -> it.doc(pw, m, w)
                        is SimpleDocF.Text -> it.doc(pw, m, w - it.str.length)
                        is SimpleDocF.Line -> when (pw) {
                            is PageWidth.Available -> if (m < it.i) it.doc(pw, m, pw.maxWidth - it.i) else Free.just(
                                false
                            )
                            else -> Free.just(true)
                        }
                    }
                    else Free.just(false)
                }
            }
        }(pw, minNest, w).runT()
    })
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

    return cata<A, (n: Int, k: Int, i: Int, nextEl: (nextN: Int, nextK: Int, nextI: Int) -> TrampolineF<Option<SimpleDoc<A>>>) -> TrampolineF<Option<SimpleDoc<A>>>> {
        { n, k, i, next ->
            Trampoline.defer {
                when (val dF = it) {
                    is DocF.Nil -> next(n, k, i)
                    is DocF.Text ->
                        next(n, k + dF.str.length, i).fix().map { it.map {
                            when (val rdF = it.unDoc) {
                                is SimpleDocF.Text -> SimpleDoc.text(dF.str + rdF.str, rdF.doc)
                                else -> SimpleDoc.text(dF.str, it)
                            }
                        } }
                    is DocF.Combined ->
                        dF.l(n, k, i) { a, b, _ -> Trampoline.defer { dF.r(a, b, i, next) } }
                    is DocF.Union ->
                        dF.l(n, k, i, next).flatMap { tL ->
                            Trampoline.later {
                                tL.map { l ->
                                    selectNicer(n, k, l) {
                                        dF.r(n, k, i, next).runT().getOrElse { SimpleDoc(SimpleDocF.Fail()) }
                                    }
                                }
                            }
                        }.flatMap { it.fold({ dF.r(n, k, i, next) }, { Free.just(it.some()) }) }
                    is DocF.Line -> next(i, i, 0).map { it.map { SimpleDoc.line(i, it) } }
                    is DocF.WithPageWidth -> dF.doc(pageWidth)(n, k, i, next)
                    is DocF.Annotated ->
                        dF.doc(n, k, i) { a, b, c ->
                            Trampoline.defer { next(a, b, c).map { it.map { SimpleDoc.removeAnnotation(it) } } }
                        }.map { it.map { SimpleDoc.addAnnotation(dF.ann, it) } }
                    is DocF.FlatAlt -> dF.l(n, k, i, next)
                    is DocF.Fail -> Free.just(none())
                    is DocF.Nest ->
                        dF.doc(n, k, i + dF.i) { a, b, _ ->
                            Trampoline.defer {
                                next(a, b, i)
                            }
                        }
                    is DocF.Column -> dF.doc(k)(n, k, i, next)
                    is DocF.Nesting -> dF.doc(i)(n, k, i, next)
                }
            }
        }
    }(0, 0, 0) { _, _, _ -> Trampoline.later { SimpleDoc.nil<A>().some() } }.runT()
        .getOrElse { SimpleDoc(SimpleDocF.Fail()) }
}