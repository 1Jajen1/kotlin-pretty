package pretty

import arrow.core.AndThen
import arrow.core.Eval
import arrow.core.extensions.eval.applicative.applicative
import arrow.core.extensions.monoid
import arrow.core.identity
import arrow.core.some
import arrow.fx.IO
import arrow.typeclasses.Show
import pretty.doc.arbitrary.arbitrary
import pretty.pagewidth.arbitrary.arbitrary
import propCheck.*
import propCheck.instances.arbitrary
import propCheck.testresult.testable.testable

class DocTest : PropertySpec({
    "layoutPretty should never render to a document that contains a fail"(Args(maxSuccess = 10_000)) {
        forAll(Doc.arbitrary(String.arbitrary()), Show { "<Doc>" }) { doc ->
            forAll(PageWidth.arbitrary()) { pw ->
                val sDoc = doc.layoutPretty(PageWidth.default())
                val hasFail = sDoc.hasFail().not()

                hasFail
            }
        }
    }
    "layoutSmart should never render to a document that contains a fail"(Args(maxSuccess = 10_000)) {
        forAll(Doc.arbitrary(String.arbitrary()), Show { "<Doc>" }) { doc ->
            forAll(PageWidth.arbitrary()) { pw ->
                val sDoc = doc.layoutSmart(PageWidth.default())
                val hasFail = sDoc.hasFail().not()

                hasFail
            }
        }
    }
    "layoutCompact should never render to a document that contains a fail"(Args(maxSuccess = 10_000)) {
        forAll(Doc.arbitrary(String.arbitrary()), Show { "<Doc>" }) { doc ->
            forAll(PageWidth.arbitrary()) { pw ->
                val sDoc = doc.layoutCompact()
                val hasFail = sDoc.hasFail().not()

                hasFail
            }
        }
    }
    "fuse should never change how a doc is rendered"(Args(maxSuccess = 1_000)) {
        forAll(Doc.arbitrary(String.arbitrary()), Show { "Doc: \"${renderDebug().value()}\"" }) { doc ->
            forAll(PageWidth.arbitrary()) { pw ->
                forAll(Boolean.arbitrary()) { b ->
                    // TODO clean this up... Exceptions will get caught in the propCheck rework
                    try {
                        val sDoc = doc.layoutPretty(pw).renderStringAnn()
                        val sDocFused = doc.fuse(b).layoutPretty(pw).renderStringAnn()
                        counterexample({
                            "Fused doc: ${doc.fuse(b).renderDebug().value()}"
                        }, sDoc.eqv(sDocFused))
                    } catch (e: Exception) {
                        TestResult.testable().run { failed("Exception", e.some()).property() }
                    }
                }
            }
        }
    }
})

fun Doc<String>.renderDebug(): Eval<String> = unDoc.flatMap { dF ->
    when (dF) {
        is DocF.Annotated -> dF.doc.renderDebug().map { "Annotated(ann=${dF.ann}, doc=$it)" }
        is DocF.Combined -> Eval.applicative().map(dF.l.renderDebug(), dF.r.renderDebug()) { (l, r) ->
            "Combined(l=$l, r=$r)"
        }
        is DocF.Text -> Eval.now("String(str=${dF.str})")
        is DocF.Nil -> Eval.now("Nil")
        is DocF.Fail -> Eval.now("Fail")
        is DocF.Column -> dF.doc(0).renderDebug().map { "Column(0) -> $it" }
        is DocF.Nesting -> dF.doc(0).renderDebug().map { "Nesting(0) -> $it" }
        is DocF.WithPageWidth -> dF.doc(PageWidth.default()).renderDebug().map { "PageWidth(<def>) -> $it" }
        is DocF.Nest -> dF.doc.renderDebug().map { "Nest(i=${dF.i}, doc=$it)" }
        is DocF.Line -> Eval.now("Line")
        is DocF.FlatAlt -> Eval.applicative().map(dF.l.renderDebug(), dF.r.renderDebug()) { (l, r) ->
            "FlatAlt(l=$l, r=$r)"
        }
        is DocF.Union -> Eval.applicative().map(dF.l.renderDebug(), dF.r.renderDebug()) { (l, r) ->
            "Union(l=$l, r=$r)"
        }
    }
}

fun SimpleDoc<String>.renderStringAnn(): String =
    renderDecorated(String.monoid(), ::identity, { "<-$it" }, { "$it->" })

tailrec fun <A> SimpleDoc<A>.hasFail(): Boolean = when (val dF = unDoc.value()) {
    is SimpleDocF.Fail -> true
    is SimpleDocF.Nil -> false
    is SimpleDocF.RemoveAnnotation -> dF.doc.hasFail()
    is SimpleDocF.AddAnnotation -> dF.doc.hasFail()
    is SimpleDocF.Line -> dF.doc.hasFail()
    is SimpleDocF.Text -> dF.doc.hasFail()
}