package pretty.symbols

import pretty.Doc
import pretty.enclose
import pretty.text

public fun <A> Doc<A>.d9966quotes(): Doc<A> = enclose(
    b99dquote(),
    t66dquote()
)
public fun <A> Doc<A>.d6699quotes(): Doc<A> = enclose(
    t66dquote(),
    t99dquote()
)
public fun <A> Doc<A>.s96quotes(): Doc<A> = enclose(
    b9quote(),
    t6quote()
)
public fun <A> Doc<A>.s69quotes(): Doc<A> = enclose(
    t6quote(),
    t9quote()
)
public fun <A> Doc<A>.dGuillemetsOut(): Doc<A> = enclose(
    ldGuillemet(),
    rdGuillemet()
)
public fun <A> Doc<A>.dGuillemetsIn(): Doc<A> = enclose(
    rdGuillemet(),
    ldGuillemet()
)
public fun <A> Doc<A>.sGuillemetsOut(): Doc<A> = enclose(
    lsGuillemet(),
    rsGuillemet()
)
public fun <A> Doc<A>.sGuillemetsIn(): Doc<A> = enclose(
    rsGuillemet(),
    lsGuillemet()
)

public fun b99dquote(): Doc<Nothing> = "„".text()
public fun t66dquote(): Doc<Nothing> = "“".text()
public fun t99dquote(): Doc<Nothing> = "”".text()
public fun b9quote(): Doc<Nothing> = "‚".text()
public fun t6quote(): Doc<Nothing> = "‘".text()
public fun t9quote(): Doc<Nothing> = "’".text()
public fun rdGuillemet(): Doc<Nothing> = "»".text()
public fun ldGuillemet(): Doc<Nothing> = "«".text()
public fun rsGuillemet(): Doc<Nothing> = "›".text()
public fun lsGuillemet(): Doc<Nothing> = "‹".text()
public fun bullet(): Doc<Nothing> = "•".text()
public fun endash(): Doc<Nothing> = "–".text()
public fun euro(): Doc<Nothing> = "€".text()
public fun cent(): Doc<Nothing> = "¢".text()
public fun yen(): Doc<Nothing> = "¥".text()
public fun pound(): Doc<Nothing> = "£".text()
