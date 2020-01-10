package pretty

sealed class PageWidth {
    data class Available(val maxWidth: Int, val ribbonFract: Float): PageWidth()
    object Unbounded: PageWidth()
    companion object
}

fun String.words(): List<Doc<Nothing>> =
    split(' ').map { it.doc() }

fun String.reflow(): Doc<Nothing> =
    words().fillSep()

fun <A> Doc<A>.testDoc(maxW: Int): Unit =
    println(
        layoutPretty(PageWidth.Available(maxW, 1F))
            .renderString()
    )