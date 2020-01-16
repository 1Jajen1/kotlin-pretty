package pretty

import arrow.typeclasses.Show
import pretty.doc.arbitrary.arbitrary
import pretty.pagewidth.arbitrary.arbitrary
import propCheck.Args
import propCheck.forAll
import propCheck.instances.arbitrary

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
})

tailrec fun <A> SimpleDoc<A>.hasFail(): Boolean = when (val dF = unDoc.value()) {
    is SimpleDocF.Fail -> true
    is SimpleDocF.Nil -> false
    is SimpleDocF.RemoveAnnotation -> dF.doc.hasFail()
    is SimpleDocF.AddAnnotation -> dF.doc.hasFail()
    is SimpleDocF.Line -> dF.doc.hasFail()
    is SimpleDocF.Text -> dF.doc.hasFail()
}