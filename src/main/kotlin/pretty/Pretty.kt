package pretty

import arrow.core.Eval
import arrow.core.extensions.list.foldable.foldRight
import arrow.core.extensions.list.functor.tupleLeft
import arrow.core.toT
import arrow.syntax.collections.tail
import pretty.doc.functor.functor
import pretty.doc.monoid.monoid

fun <A> Doc<A>.renderPretty(): SimpleDoc<A> = layoutPretty(PageWidth.Available(80, 0.4F))

fun <A> Doc<A>.pretty(maxWidth: Int, ribbonWidth: Float): String =
    layoutPretty(PageWidth.Available(maxWidth, ribbonWidth)).renderString()

// primitives
fun <A> nil(): Doc<A> = Doc.monoid<A>().empty()

fun <A> String.text(): Doc<A> = Doc<A>(DocF.Text(this))

fun <A> line(): Doc<A> = hardLine<A>().flatAlt(" ".text())

fun <A> lineBreak(): Doc<A> = hardLine<A>().flatAlt(nil())

fun <A> softLine(): Doc<A> = line<A>().group()

fun <A> softLineBreak(): Doc<A> = lineBreak<A>().group()

fun <A> hardLine(): Doc<A> = Doc(DocF.Line())

fun <A> Doc<A>.nest(i: Int): Doc<A> = Doc<A>(DocF.Nest(i, this))

fun <A> Doc<A>.group(): Doc<A> = Doc<A>(DocF.Union(this.flatten(), this))

fun <A> column(f: (Int) -> Doc<A>): Doc<A> = Doc<A>(DocF.Column(f))

fun <A> nesting(f: (Int) -> Doc<A>): Doc<A> = Doc<A>(DocF.Nesting(f))

fun <A> Doc<A>.flatAlt(other: Doc<A>): Doc<A> = Doc<A>(DocF.FlatAlt(this, other))

fun <A> Doc<A>.annotate(ann: A): Doc<A> = Doc(DocF.Annotated(ann, this))

fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B> = Doc.functor().run {
    map(f).fix()
}

fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B> = cata {
    when (it) {
        is DocF.Annotated -> f(it.ann).foldRight(Eval.now(it.doc)) { a, acc ->
            acc.map { Doc(DocF.Annotated(a, it)) }
        }.value()
        else -> Doc(it as DocF<B, Doc<B>>)
        // should be a safe cast because cata already transformed everything but annotations up to this level
        //  and everything but annotations has A as a phantom generic
    }
}

fun <A> Doc<A>.unAnnotate(): Doc<Nothing> = alterAnnotations { emptyList<Nothing>() }

// combinators
fun <A> Doc<A>.fillBreak(i: Int): Doc<A> = width { w ->
    if (w > i) lineBreak<A>().nest(i) else spaces(i - w).text()
}

fun <A> Doc<A>.fill(i: Int): Doc<A> = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A> = column { k1 -> this + column { k2 -> f(k2 - k1) } }

fun <A> Doc<A>.indent(i: Int): Doc<A> = (spaces(i).text<A>() + this).hang(i)

fun <A> Doc<A>.hang(i: Int): Doc<A> = nest(i).align()

fun <A> Doc<A>.align(): Doc<A> = column { k ->
    nesting { i -> this.nest(k - i) }
}

fun <A> Doc<A>.flatten(): Doc<A> = para {
    // This is why I love recursion schemes!
    when (it) {
        is DocF.Union -> it.l.b // left side of union is already flattened
        is DocF.FlatAlt -> it.r.a // alternative, using the extra argument from para which is not yet folded
        else -> Doc(it.map { it.b }) // Ignore all other cases and just take the folded result
    }
}

fun spaces(i: Int): String =
    if (i < 0) ""
    else (0 until i).joinToString("") { " " }

infix fun <A> Doc<A>.spaced(d: Doc<A>): Doc<A> = this + " ".text() + d

infix fun <A> Doc<A>.line(d: Doc<A>): Doc<A> = this + line() + d
infix fun <A> Doc<A>.softLine(d: Doc<A>): Doc<A> = this + softLine() + d
infix fun <A> Doc<A>.lineBreak(d: Doc<A>): Doc<A> = this + lineBreak() + d
infix fun <A> Doc<A>.softLineBreak(d: Doc<A>): Doc<A> = this + softLineBreak() + d

fun <A> List<Doc<A>>.list(): Doc<A> = encloseSep(
    (lBracket<A>() + space()).flatAlt(lBracket()),
    (rBracket<A>() + space()).flatAlt(rBracket()),
    comma<A>() + space()
).group()

fun <A> List<Doc<A>>.tupled(): Doc<A> = encloseSep(
    (lParen<A>() + space()).flatAlt(lParen()),
    (rParen<A>() + space()).flatAlt(rParen()),
    comma<A>() + space()
).group()

fun <A> List<Doc<A>>.semiBraces(): Doc<A> = encloseSep(
    (lBrace<A>() + space()).flatAlt(lBrace()),
    (rBrace<A>() + space()).flatAlt(rBrace()),
    comma<A>() + space()
).group()

fun <A> List<Doc<A>>.encloseSep(l: Doc<A>, r: Doc<A>, sep: Doc<A>): Doc<A> = when {
    isEmpty() -> l + r
    size == 1 -> l + first() + r
    else -> ((listOf(l toT this.first()) + this.tail().tupleLeft(sep)).map { (a, b) -> a + b }
        .cat() + r).align()
}

fun <A> List<Doc<A>>.punctuate(p: Doc<A>): List<Doc<A>> = when {
    isEmpty() -> emptyList()
    size == 1 -> listOf(first())
    else -> (first() toT tail()).let { (x, xs) ->
        listOf(x + p) + xs.punctuate(p)
    }
}

fun <A> List<Doc<A>>.foldDoc(f: (Doc<A>, Doc<A>) -> Doc<A>): Doc<A> = when {
    isEmpty() -> nil()
    else -> reduce(f)
}

fun <A> List<Doc<A>>.cat(): Doc<A> = vCat().group()
fun <A> List<Doc<A>>.fillCat(): Doc<A> = foldDoc { a, b -> a softLineBreak b }
fun <A> List<Doc<A>>.hCat(): Doc<A> = foldDoc { a, b -> a + b }
fun <A> List<Doc<A>>.vCat(): Doc<A> = foldDoc { a, b -> a lineBreak b }

fun <A> List<Doc<A>>.sep(): Doc<A> = vSep().group()
fun <A> List<Doc<A>>.fillSep(): Doc<A> = foldDoc { a, b -> a softLine b }
fun <A> List<Doc<A>>.hSep(): Doc<A> = foldDoc { a, b -> a spaced b }
fun <A> List<Doc<A>>.vSep(): Doc<A> = foldDoc { a, b -> a line b }

fun <A> Doc<A>.enclose(l: Doc<A>, r: Doc<A>): Doc<A> = l + this + r

fun <A> Doc<A>.sQuotes(): Doc<A> = enclose(sQuote(), sQuote())
fun <A> Doc<A>.dQuotes(): Doc<A> = enclose(dQuote(), dQuote())
fun <A> Doc<A>.braces(): Doc<A> = enclose(lBrace(), rBrace())
fun <A> Doc<A>.parens(): Doc<A> = enclose(lParen(), rParen())
fun <A> Doc<A>.brackets(): Doc<A> = enclose(lBracket(), rBracket())
fun <A> Doc<A>.angles(): Doc<A> = enclose(lAngle(), rAngle())

fun <A> lBracket(): Doc<A> = "[".text()
fun <A> rBracket(): Doc<A> = "]".text()
fun <A> lParen(): Doc<A> = "(".text()
fun <A> rParen(): Doc<A> = ")".text()
fun <A> lBrace(): Doc<A> = "{".text()
fun <A> rBrace(): Doc<A> = "}".text()
fun <A> lAngle(): Doc<A> = "<".text()
fun <A> rAngle(): Doc<A> = ">".text()

fun <A> comma(): Doc<A> = ",".text()
fun <A> space(): Doc<A> = " ".text()
fun <A> sQuote(): Doc<A> = "\'".text()
fun <A> dQuote(): Doc<A> = "\"".text()
fun <A> semiColon(): Doc<A> = ";".text()
fun <A> colon(): Doc<A> = ":".text()
fun <A> dot(): Doc<A> = ".".text()
fun <A> backslash(): Doc<A> = "\\".text()
fun <A> equals(): Doc<A> = "=".text()
fun <A> pipe(): Doc<A> = "|".text()

fun <A> String.doc(): Doc<A> = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        if (fst.length >= length) fst.text()
        else fst.text<A>() + hardLine() + substring(fst.length + 1).doc()
    }
}

fun <A> Boolean.doc(): Doc<A> = toString().text()
fun <A> Byte.doc(): Doc<A> = toString().text()
fun <A> Short.doc(): Doc<A> = toString().text()
fun <A> Int.doc(): Doc<A> = toString().text()
fun <A> Long.doc(): Doc<A> = toString().text()
fun <A> Float.doc(): Doc<A> = toString().text()
fun <A> Double.doc(): Doc<A> = toString().text()
