package pretty

public sealed class PageWidth {
    public data class Available(val maxWidth: Int, val ribbonFract: Float): PageWidth()
    public object Unbounded: PageWidth()

    public companion object {
        public fun default(): PageWidth = Available(80, 0.4F)
    }
}

public fun String.words(): List<Doc<Nothing>> =
    split(' ').map { it.doc() }

public fun String.reflow(): Doc<Nothing> =
    words().fillSep()

internal fun <A> List<A>.tail(): List<A> = drop(1)

internal fun spaces(i: Int): String =
    if (i < 0) ""
    else (0 until i).joinToString("") { " " }

