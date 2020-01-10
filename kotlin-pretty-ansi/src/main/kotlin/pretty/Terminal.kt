package pretty

import arrow.core.*
import arrow.core.extensions.list.foldable.fold
import arrow.core.extensions.list.functorFilter.flattenOption
import arrow.core.extensions.list.monad.flatMap
import arrow.core.extensions.list.monad.flatten
import arrow.core.extensions.listk.monoid.monoid
import arrow.core.extensions.sequence.zip.zipWith
import arrow.extension
import arrow.fx.IO
import arrow.typeclasses.Monoid
import arrow.typeclasses.Semigroup
import pretty.ansistyle.monoid.monoid

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

sealed class Layer {
    object Foreground : Layer()
    object Background : Layer()
}

object Bold
object Underlined
object Italicized

// TODO maybe offer a more complete sgr rep in the future?
data class AnsiStyle(
    val foreground: Option<Tuple2<Intensity, Color>>,
    val background: Option<Tuple2<Intensity, Color>>,
    val bold: Option<Bold>,
    val underlined: Option<Underlined>,
    val italics: Option<Italicized>
) {
    operator fun plus(other: AnsiStyle): AnsiStyle = AnsiStyle(
        foreground = foreground.orElse { other.foreground },
        background = background.orElse { other.background },
        bold = bold.orElse { other.bold },
        underlined = underlined.orElse { other.underlined },
        italics = italics.orElse { other.italics }
    )

    companion object
}

fun color(c: Color): AnsiStyle = AnsiStyle.monoid().empty().copy(foreground = (Intensity.Vivid toT c).some())
fun bgColor(c: Color): AnsiStyle = AnsiStyle.monoid().empty().copy(background = (Intensity.Vivid toT c).some())
fun colorDull(c: Color): AnsiStyle = AnsiStyle.monoid().empty().copy(foreground = (Intensity.Dull toT c).some())
fun bgColorDull(c: Color): AnsiStyle = AnsiStyle.monoid().empty().copy(background = (Intensity.Dull toT c).some())
fun bold(): AnsiStyle = AnsiStyle.monoid().empty().copy(bold = Bold.some())
fun italicized(): AnsiStyle = AnsiStyle.monoid().empty().copy(italics = Italicized.some())
fun underlined(): AnsiStyle = AnsiStyle.monoid().empty().copy(underlined = Underlined.some())

@extension
interface AnsiStyleSemigroup : Semigroup<AnsiStyle> {
    override fun AnsiStyle.combine(b: AnsiStyle): AnsiStyle = this + b
}

@extension
interface AnsiStyleMonoid : Monoid<AnsiStyle>, AnsiStyleSemigroup {
    override fun empty(): AnsiStyle =
        AnsiStyle(None, None, None, None, None)
}

fun AnsiStyle.toRawString(): String =
    (listOf(0) + // reset
            listOf(
                foreground.map { (int, c) ->
                    when (int) {
                        is Intensity.Dull -> 30 + c.toCode()
                        is Intensity.Vivid -> 90 + c.toCode()
                    }
                },
                background.map { (int, c) ->
                    when (int) {
                        is Intensity.Dull -> 40 + c.toCode()
                        is Intensity.Vivid -> 100 + c.toCode()
                    }
                },
                bold.map { 1 },
                underlined.map { 4 },
                italics.map { 3 }
            ).flattenOption()).csi("m")

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
    else -> (first() toT drop(1)).let { (x, xs) ->
        listOf(x) + (
                arrow.core.extensions.sequence.repeat.repeat(a).zipWith(xs.asSequence()) { l, r -> listOf(l, r).k() }
                ).toList().fold(ListK.monoid())
    }
}

fun SimpleDoc<AnsiStyle>.renderAnsiString(): String {
    // local mutability is fine ^-^
    val sb: StringBuilder = StringBuilder()
    tailrec fun SimpleDoc<AnsiStyle>.go(anns: Nel<AnsiStyle>): Unit = when (val dF = unDoc.value()) {
        is SimpleDocF.Nil -> Unit
        is SimpleDocF.Fail -> throw IllegalStateException("SimpleDoc.Fail in renderer. If you have used an inbuilt-layout function please report this.")
        is SimpleDocF.Text -> {
            sb.append(dF.str); dF.doc.go(anns)
        }
        is SimpleDocF.Line -> {
            sb.append("\n${spaces(dF.i)}"); dF.doc.go(anns)
        }
        is SimpleDocF.AddAnnotation -> {
            val curr = anns.head
            val newStyle = dF.ann + curr
            sb.append(newStyle.toRawString())
            dF.doc.go(Nel(newStyle, anns.all))
        }
        is SimpleDocF.RemoveAnnotation -> {
            val newAnns = Nel.fromList(anns.tail).fold({
                throw IllegalStateException("There is no empty empty style left after removing an annotation, but there should be. Please report this")
            }, ::identity)
            sb.append(newAnns.head.toRawString())
            dF.doc.go(newAnns)
        }
    }
    go(Nel(AnsiStyle.monoid().empty()))
    return sb.toString()
}

fun SimpleDoc<AnsiStyle>.renderStreamIO(write: (String) -> IO<Unit>): IO<Unit> {
    fun SimpleDoc<AnsiStyle>.go(anns: Nel<AnsiStyle>): IO<Unit> = when (val dF = unDoc.value()) {
        is SimpleDocF.Nil -> IO.unit
        is SimpleDocF.Fail -> throw IllegalStateException("SimpleDoc.Fail in renderer. If you have used an inbuilt-layout function please report this.")
        is SimpleDocF.Text -> write(dF.str).flatMap { dF.doc.go(anns) }
        is SimpleDocF.Line -> write("\n${spaces(dF.i)}").flatMap { dF.doc.go(anns) }
        is SimpleDocF.AddAnnotation -> {
            val curr = anns.head
            val newStyle = dF.ann + curr
            write(newStyle.toRawString()).flatMap { dF.doc.go(Nel(newStyle, anns.all)) }
        }
        is SimpleDocF.RemoveAnnotation -> {
            val newAnns = Nel.fromList(anns.tail).fold({
                throw IllegalStateException("There is no empty empty style left after removing an annotation, but there should be. Please report this")
            }, ::identity)
            write(newAnns.head.toRawString()).flatMap { dF.doc.go(newAnns) }
        }
    }
    return go(Nel(AnsiStyle.monoid().empty()))
}

fun Doc<AnsiStyle>.renderStream(write: (String) -> Unit): Unit =
    layoutPretty(PageWidth.default()).renderStreamIO { IO { write(it) } }.unsafeRunSync()

fun Doc<AnsiStyle>.putDocIO(): IO<Unit> =
    layoutPretty(PageWidth.default()).renderStreamIO { IO { print(it) } }

fun Doc<AnsiStyle>.putDoc(): Unit =
    renderStream { print(it) }
