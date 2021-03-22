package pretty

sealed class Color {
    object Black : Color()
    object Red : Color()
    object Green : Color()
    object Yellow : Color()
    object Blue : Color()
    object Magenta : Color()
    object Cyan : Color()
    object White : Color()
}

sealed class Intensity {
    object Dull : Intensity()
    object Vivid : Intensity()
}

object Bold
object Underlined
object Italicized

// TODO maybe offer a more complete sgr rep in the future?
data class AnsiStyle(
    val foreground: Pair<Intensity, Color>?,
    val background: Pair<Intensity, Color>?,
    val bold: Bold?,
    val underlined: Underlined?,
    val italics: Italicized?
) {
    operator fun plus(other: AnsiStyle): AnsiStyle = AnsiStyle(
        foreground = foreground ?: other.foreground,
        background = background ?: other.background,
        bold = bold ?: other.bold,
        underlined = underlined ?: other.underlined,
        italics = italics ?: other.italics
    )

    companion object {
        fun empty() = AnsiStyle(null, null, null, null, null)
    }
}

fun color(c: Color): AnsiStyle = AnsiStyle.empty().copy(foreground = (Intensity.Vivid to c))
fun bgColor(c: Color): AnsiStyle = AnsiStyle.empty().copy(background = (Intensity.Vivid to c))
fun colorDull(c: Color): AnsiStyle = AnsiStyle.empty().copy(foreground = (Intensity.Dull to c))
fun bgColorDull(c: Color): AnsiStyle = AnsiStyle.empty().copy(background = (Intensity.Dull to c))
fun bold(): AnsiStyle = AnsiStyle.empty().copy(bold = Bold)
fun italicized(): AnsiStyle = AnsiStyle.empty().copy(italics = Italicized)
fun underlined(): AnsiStyle = AnsiStyle.empty().copy(underlined = Underlined)

fun AnsiStyle.toRawString(): String =
    (listOf(0) + // reset
            listOf(
                foreground?.let { (int, c) ->
                    when (int) {
                        Intensity.Dull -> 30 + c.toCode()
                        Intensity.Vivid -> 90 + c.toCode()
                    }
                },
                background?.let { (int, c) ->
                    when (int) {
                        Intensity.Dull -> 40 + c.toCode()
                        Intensity.Vivid -> 100 + c.toCode()
                    }
                },
                bold?.let { 1 },
                underlined?.let { 4 },
                italics?.let { 3 }
            ).filterNotNull()).csi("m")

fun Color.toCode(): Int = when (this) {
    is Color.Black -> 0
    is Color.Red -> 1
    is Color.Green -> 2
    is Color.Yellow -> 3
    is Color.Blue -> 4
    is Color.Magenta -> 5
    is Color.Cyan -> 6
    is Color.White -> 7
}

private const val ESC = "\u001B"

private fun List<Int>.csi(code: String): String = "$ESC[" + map { it.toString() }
    .intersperse(";").joinToString("") + code

private fun <A> List<A>.intersperse(a: A): List<A> = when {
    isEmpty() -> this
    else -> (first() to drop(1)).let { (x, xs) ->
        listOf(x) + (repeat(a).zip(xs.asSequence()) { l, r -> listOf(l, r) }).toList().flatten()
    }
}

private fun <A> repeat(a: A): Sequence<A> = generateSequence { a }

fun SimpleDoc<AnsiStyle>.renderAnsiString(): String {
    // local mutability is fine ^-^
    val sb: StringBuilder = StringBuilder()
    val anns = ArrayDeque<AnsiStyle>()
    anns.add(AnsiStyle.empty())
    renderDecorated(
        Unit, { _, _ -> },
        { str -> sb.append(str); Unit },
        { anns.addLast(it); sb.append(it.toRawString()); Unit },
        { anns.removeLast(); sb.append(anns.last().toRawString()); Unit })
    return sb.toString()
}
