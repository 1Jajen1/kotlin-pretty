package pretty

import arrow.core.*
import arrow.core.extensions.eval.functor.functor
import arrow.core.extensions.fx
import arrow.mtl.typeclasses.ComposedFunctor
import arrow.recursion.hyloC
import pretty.doc.birecursive.birecursive
import pretty.docf.functor.functor
import pretty.simpledoc.birecursive.birecursive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

typealias FittingFun<A> = SimpleDoc<A>.(pageWidth: PageWidth, minNesting: Int, availableWidth: Option<Int>) -> Boolean

fun <A> Doc<A>.layoutPretty(pageWidth: PageWidth): SimpleDoc<A> {
    fun <A> SimpleDoc<A>.fits(w: Int): Boolean =
        SimpleDoc.birecursive<A>().run {
            this@fits.cata<(Int) -> Boolean> {
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

    return layoutWadlerLeijen(pageWidth) { _, _, avail ->
        avail.fold({ true }, { w -> fits(w) })
    }
}

fun <A> Doc<A>.layoutWadlerLeijen(
    pageWidth: PageWidth,
    fits: FittingFun<A>
): SimpleDoc<A> = Doc.birecursive<A>().run {
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

    this@layoutWadlerLeijen.cata<(n: Int, k: Int, i: Int, nextEl: (nextN: Int, nextK: Int, nextI: Int) -> Option<SimpleDoc<A>>) -> Option<SimpleDoc<A>>> {
        { n, k, i, next ->
            when (val dF = it.fix()) {
                is DocF.Nil -> next(n, k, i)
                is DocF.Text ->
                    next(n, k + dF.str.length, i).map { SimpleDoc.text(dF.str, it) }
                is DocF.Combined ->
                    dF.l(n, k, i) { a, b, _ -> dF.r(a, b, i, next) }
                is DocF.Union ->
                    dF.l(n, k, i, next).map { l ->
                        selectNicer(n, k, l) {
                            dF.r(n, k, i, next).getOrElse { SimpleDoc(SimpleDocF.Fail()) }
                        }
                    }.orElse { dF.r(n, k, i, next) }
                is DocF.Line -> next(i, i, 0).map { SimpleDoc.line(i, it) }
                is DocF.WithPageWidth -> dF.doc(pageWidth)(n, k, i, next)
                is DocF.Annotated ->
                    dF.doc(n, k, i) { a, b, c ->
                        next(a, b, c).map { SimpleDoc.removeAnnotation(it) }
                    }.map { SimpleDoc.addAnnotation(dF.ann, it) }
                is DocF.FlatAlt -> dF.l(n, k, i, next)
                is DocF.Fail -> none()
                is DocF.Nest -> dF.doc(n, k, i + dF.i) { a, b, _ -> next(a, b, i) }
                is DocF.Column -> dF.doc(k)(n, k, i, next)
                is DocF.Nesting -> dF.doc(i)(n, k, i, next)
            }
        }
    }
}(0, 0, 0) { _, _, _ -> SimpleDoc.nil<A>().some() }.getOrElse { SimpleDoc(SimpleDocF.Fail()) }
