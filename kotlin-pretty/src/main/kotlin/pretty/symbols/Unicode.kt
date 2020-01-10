package pretty.symbols

import pretty.Doc
import pretty.enclose
import pretty.text

fun <A> Doc<A>.d9966quotes(): Doc<A> = enclose(
    b99dquote(),
    t66dquote()
)
fun <A> Doc<A>.d6699quotes(): Doc<A> = enclose(
    t66dquote(),
    t99dquote()
)
fun <A> Doc<A>.s96quotes(): Doc<A> = enclose(
    b9quote(),
    t6quote()
)
fun <A> Doc<A>.s69quotes(): Doc<A> = enclose(
    t6quote(),
    t9quote()
)
fun <A> Doc<A>.dGuillemetsOut(): Doc<A> = enclose(
    ldGuillemet(),
    rdGuillemet()
)
fun <A> Doc<A>.dGuillemetsIn(): Doc<A> = enclose(
    rdGuillemet(),
    ldGuillemet()
)
fun <A> Doc<A>.sGuillemetsOut(): Doc<A> = enclose(
    lsGuillemet(),
    rsGuillemet()
)
fun <A> Doc<A>.sGuillemetsIn(): Doc<A> = enclose(
    rsGuillemet(),
    lsGuillemet()
)

fun b99dquote(): Doc<Nothing> = "„".text()
fun t66dquote(): Doc<Nothing> = "“".text()
fun t99dquote(): Doc<Nothing> = "”".text()
fun b9quote(): Doc<Nothing> = "‚".text()
fun t6quote(): Doc<Nothing> = "‘".text()
fun t9quote(): Doc<Nothing> = "’".text()
fun rdGuillemet(): Doc<Nothing> = "»".text()
fun ldGuillemet(): Doc<Nothing> = "«".text()
fun rsGuillemet(): Doc<Nothing> = "›".text()
fun lsGuillemet(): Doc<Nothing> = "‹".text()
fun bullet(): Doc<Nothing> = "•".text()
fun endash(): Doc<Nothing> = "–".text()
fun euro(): Doc<Nothing> = "€".text()
fun cent(): Doc<Nothing> = "¢".text()
fun yen(): Doc<Nothing> = "¥".text()
fun pound(): Doc<Nothing> = "£".text()
