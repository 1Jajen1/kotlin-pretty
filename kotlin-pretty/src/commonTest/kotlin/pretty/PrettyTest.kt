package pretty

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

// Mostly adapted from prettyprinter as this version should have the same problems
// TODO add proper timeouts
class PerformanceTests {

    // See https://github.com/quchen/prettyprinter/issues/22
    @Test
    fun groupPerf() {
        fun pathological(n: Int): Doc<Nothing> =
            generateSequence("foobar".doc()) { x -> listOf(x, emptyList<Doc<Nothing>>().sep()).hSep() }
                .drop(n).first()
        pathological(1000).pretty()
    }

    // See https://github.com/haskell/pretty/issues/32
    @Test
    fun fillSepPerf() {
        fun pathological(n: Int): Doc<Nothing> =
            generateSequence("foobar".doc()) { x -> listOf("a".doc(), x spaced "b".doc()).fillSep() }
                .drop(n).first()
        pathological(1000).pretty()
    }
}

class ExampleTests {
    @Test
    fun haskellLikeSourceTest() {
        val funName = "fooBar"
        val type = listOf("Int", "Int", "IO Int")

        val doc = funName.doc() spaced ("::".text() spaced type.map { it.doc() }
            .punctuate(softLine() + "-> ".text()).hCat()).align()

        val expectedWide = """
            fooBar :: Int -> Int -> IO Int
        """.trimIndent()
        val expectedFlat = """
            fooBar :: Int
                   -> Int
                   -> IO Int
        """.trimIndent()

        assertEquals(expectedWide, doc.pretty(maxWidth = 80, ribbonWidth = 0.4F))
        assertEquals(expectedFlat, doc.pretty(maxWidth = 20, ribbonWidth = 0.4F))
    }

    @Test
    fun lists() {
        val longList = listOf<Any?>("Hello World", 100, false, "Cool", -1000)
        val doc = longList.map { it.toString().doc() }.list()

        val expectedWide = """
            [Hello World, 100, false, Cool, -1000]
        """.trimIndent()
        val expectedFlat = """
            [ Hello World
            , 100
            , false
            , Cool
            , -1000
            ]
        """.trimIndent()

        assertEquals(expectedWide, doc.pretty(maxWidth = 80, ribbonWidth = 1F))
        assertEquals(expectedFlat, doc.pretty(maxWidth = 20, ribbonWidth = 1F))
    }
}

// Js has timeout of 2000 ms which is far to low for this...
@Ignore
class StackSafetyTests {
    fun pathological(n: Int): Doc<Nothing> =
        generateSequence("foobar".doc()) { x -> listOf("a".doc(), x spaced "b".doc()).fillSep() }
            .drop(n).first()

    val threshold = 10000

    @Test
    fun changesUponFlattening() {
        pathological(threshold).changesUponFlattening()
    }

    @Test
    fun flatten() {
        pathological(threshold).flatten()
    }

    @Test
    fun alterAnnotations() {
        pathological(threshold).alterAnnotations<Nothing, Nothing> { emptyList() }
    }

    @Test
    fun fuse() {
        pathological(threshold).fuse(shallow = false)
        pathological(threshold).fuse(shallow = true)
    }

    @Test
    fun layoutPretty() {
        pathological(threshold).pretty()
    }
}
