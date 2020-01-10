package pretty

sealed class PageWidth {
    data class Available(val maxWidth: Int, val ribbonFract: Float): PageWidth()
    object Unbounded: PageWidth()
    companion object
}