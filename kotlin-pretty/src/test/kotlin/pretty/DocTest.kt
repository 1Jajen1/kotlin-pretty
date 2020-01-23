package pretty

import arrow.core.extensions.eq
import arrow.core.extensions.monoid
import arrow.core.extensions.show
import arrow.core.identity
import propCheck.pretty.showPretty
import propCheck.property.NoConfidenceTermination
import propCheck.property.PropertyConfig

class DocTest : PropertySpec({
    "layoutPretty should never render to a document that contains a fail"(PropertyConfig(NoConfidenceTermination(limit = 100_000))) {
        val layoutOptions = forAll(genPageWidth).bind()
        val doc = forAllWith({ it.diag().showPretty() }) { genDoc(latin1().string(0..100)) }.bind()

        val sDoc = doc.layoutPretty(layoutOptions)

        if (sDoc.hasFail()) failWith("Layed out doc had fail!").bind()
    }
    "layoutSmart should never render to a document that contains a fail"(PropertyConfig(NoConfidenceTermination(limit = 100_000))) {
        val layoutOptions = forAll(genPageWidth).bind()
        val doc = forAllWith({ it.diag().showPretty() }) { genDoc(latin1().string(0..100)) }.bind()

        val sDoc = doc.layoutSmart(layoutOptions)

        if (sDoc.hasFail()) failWith("Layed out doc had fail!").bind()
    }
    "layoutCompact should never render to a document that contains a fail"(PropertyConfig(NoConfidenceTermination(limit = 100_000))) {
        val doc = forAllWith({ it.diag().showPretty() }) { genDoc(latin1().string(0..100)) }.bind()

        val sDoc = doc.layoutCompact()

        if (sDoc.hasFail()) failWith("Layed out doc had fail!").bind()
    }
    "fuse should never change how a doc is rendered"(PropertyConfig(NoConfidenceTermination(limit = 100_000))) {
        val layoutOptions = forAll(genPageWidth).bind()
        val deep = forAll { boolean() }.bind()
        val doc = forAllWith({ it.diag().showPretty() }) { genDoc(latin1().string(0..100)) }.bind()

        val fused = doc.fuse(deep)

        annotate { "Fused:".text() spaced fused.diag().showPretty() }.bind()

        doc.layoutPretty(layoutOptions).renderStringAnn()
            .eqv(fused.layoutPretty(layoutOptions).renderStringAnn(), String.eq(), String.show()).bind()
    }
})

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
