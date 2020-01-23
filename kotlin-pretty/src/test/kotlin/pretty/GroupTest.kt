package pretty

import arrow.core.extensions.eq
import arrow.core.extensions.show
import propCheck.pretty.showPretty
import propCheck.property.NoConfidenceTermination
import propCheck.property.PropertyConfig

class GroupTest : PropertySpec({
    "group == group2"(PropertyConfig(NoConfidenceTermination(limit = 100_000))) {
        val doc = forAllWith({ it.diag().showPretty() }) { genDoc(latin1().string(0..100)) }.bind()
        val pw = forAll(genPageWidth).bind()

        val grouped = doc.group()
        val grouped2 = doc.group2()

        annotate { ("Grouped:".text() softLine grouped.diag().showPretty()).align() }.bind()
        annotate { ("Grouped (2):".text() softLine grouped2.diag().showPretty()).align() }.bind()

        val renderGrouped = grouped.layoutPretty(pw).renderStringAnn()
        val renderGrouped2 = grouped2.layoutPretty(pw).renderStringAnn()

        renderGrouped.eqv(renderGrouped2, String.eq(), String.show()).bind()
    }
})