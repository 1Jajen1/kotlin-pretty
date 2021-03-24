package pretty

import pretty.lazy.AndThen
import pretty.lazy.Eval
import pretty.lazy.flatMap
import pretty.lazy.map
import pretty.symbols.*

public fun <A> Doc<A>.renderPretty(): SimpleDoc<A> = layoutPretty(PageWidth.Available(80, 0.4F))

public fun <A> Doc<A>.pretty(maxWidth: Int = 80, ribbonWidth: Float = 0.4F): String =
    layoutPretty(PageWidth.Available(maxWidth, ribbonWidth)).renderString()

// primitives
public fun nil(): Doc<Nothing> = Doc(Eval.now(DocF.Nil))

public fun String.text(): Doc<Nothing> = Doc(Eval.now(DocF.Text(this)))

public fun line(): Doc<Nothing> = hardLine().flatAlt(" ".text())

public fun lineBreak(): Doc<Nothing> = hardLine().flatAlt(nil())

public fun softLine(): Doc<Nothing> = line().group()

public fun softLineBreak(): Doc<Nothing> = lineBreak().group()

public fun hardLine(): Doc<Nothing> = Doc(Eval.now(DocF.Line))

public fun <A> Doc<A>.nest(i: Int): Doc<A> = Doc(Eval.now(DocF.Nest(i, this)))

// TODO Rework and benchmark
public fun <A> Doc<A>.group(): Doc<A> = when (val dF = unDoc()) {
    is DocF.Union -> this
    else -> changesUponFlattening()?.let {
        Doc(Eval.now(DocF.Union(it, this@group)))
    } ?: this
}

internal fun <A> Doc<A>.changesUponFlattening(): Doc<A>? {
    fun Doc<A>.go(): Eval<Doc<A>?> =
        when (val dF = unDoc()) {
            is DocF.FlatAlt -> Eval.now(dF.r.flatten())
            is DocF.Line -> Eval.now(Doc<A>(Eval.now(DocF.Fail)))
            is DocF.Union -> Eval.now(dF.l)
            is DocF.Nest -> Eval.defer { dF.doc.go().map { it?.let { Doc(Eval.now(DocF.Nest(dF.i, it))) } } }
            is DocF.Annotated -> Eval.defer { dF.doc.go().map { it?.let { Doc(Eval.now(DocF.Annotated(dF.ann, it))) } } }

            is DocF.Column -> Eval.now(Doc(Eval.now(DocF.Column(dF.doc.andThen { it.flatten() }))))
            is DocF.Nesting -> Eval.now(Doc(Eval.now(DocF.Nesting(dF.doc.andThen { it.flatten() }))))
            is DocF.WithPageWidth -> Eval.now(Doc(Eval.now(DocF.WithPageWidth(dF.doc.andThen { it.flatten() }))))

            is DocF.Combined -> {
                val lEval = Eval.defer { dF.l.go() }
                val rEval = Eval.defer { dF.r.go() }
                lEval.flatMap { l ->
                    rEval.map { r ->
                        if (l != null) {
                            if (r != null) Doc(Eval.now(DocF.Combined(l, r)))
                            else Doc(Eval.now(DocF.Combined(l, dF.r)))
                        } else {
                            if (r != null) Doc(Eval.now(DocF.Combined(dF.l, r)))
                            else null
                        }
                    }
                }
            }

            else -> Eval.now(null)
        }

    return go().invoke()
}

internal fun <A> Doc<A>.flatten(): Doc<A> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.FlatAlt -> Eval.defer { it.r.flatten().unDoc }
        is DocF.Union -> Eval.defer { it.l.flatten().unDoc }
        is DocF.Nest -> Eval.later { it.copy(doc = it.doc.flatten()) }
        is DocF.Combined -> Eval.later { it.copy(l = it.l.flatten(), r = it.r.flatten()) }
        is DocF.Annotated -> Eval.later { it.copy(doc = it.doc.flatten()) }
        is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(it.doc.andThen { it.flatten() }))
        is DocF.Column -> Eval.now(DocF.Column(it.doc.andThen { it.flatten() }))
        is DocF.Nesting -> Eval.now(DocF.Nesting(it.doc.andThen { it.flatten() }))
        is DocF.Line -> Eval.now(DocF.Fail)
        else -> Eval.now(it)
    }
})

public fun <A> column(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Column(AndThen(f))))

public fun <A> nesting(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Nesting(AndThen(f))))

public fun <A> pageWidth(f: (PageWidth) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.WithPageWidth(AndThen(f))))

public fun <A> Doc<A>.flatAlt(other: Doc<A>): Doc<A> = Doc(Eval.now(DocF.FlatAlt(this, other)))

public fun <A> Doc<A>.annotate(ann: A): Doc<A> = Doc(Eval.now(DocF.Annotated(ann, this)))

public fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B> = map(f)

// TODO Is this recursive bit safe?
public fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.Annotated -> Eval.defer {
            f(it.ann).fold(it.doc.alterAnnotations(f)) { doc, ann ->
                Doc(Eval.now(DocF.Annotated(ann, doc)))
            }.unDoc
        }
        is DocF.Text -> Eval.now(DocF.Text(it.str))
        is DocF.Union -> Eval.later { DocF.Union(it.l.alterAnnotations(f), it.r.alterAnnotations(f)) }
        is DocF.Combined -> Eval.later { DocF.Combined(it.l.alterAnnotations(f), it.r.alterAnnotations(f)) }
        is DocF.WithPageWidth -> Eval.later { DocF.WithPageWidth(it.doc.andThen { it.alterAnnotations(f) }) }
        is DocF.Column -> Eval.later { DocF.Column(it.doc.andThen { it.alterAnnotations(f) }) }
        is DocF.Nesting -> Eval.later { DocF.Nesting(it.doc.andThen { it.alterAnnotations(f) }) }
        is DocF.Nest -> Eval.later { DocF.Nest(it.i, it.doc.alterAnnotations(f)) }
        is DocF.FlatAlt -> Eval.later { DocF.FlatAlt(it.l.alterAnnotations(f), it.r.alterAnnotations(f)) }
        is DocF.Line -> Eval.now(DocF.Line)
        is DocF.Nil -> Eval.now(DocF.Nil)
        is DocF.Fail -> Eval.now(DocF.Fail)
    }
})

public fun <A> Doc<A>.unAnnotate(): Doc<Nothing> = alterAnnotations { emptyList<Nothing>() }

// combinators
public fun <A> Doc<A>.fillBreak(i: Int): Doc<A> = width { w ->
    if (w > i) lineBreak().nest(i) else spaces(i - w).text()
}

public fun <A> Doc<A>.fill(i: Int): Doc<A> = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

public fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A> = column { k1 -> this + column { k2 -> f(k2 - k1) } }

public fun <A> Doc<A>.indent(i: Int): Doc<A> = (spaces(i).text() + this).hang(i)

public fun <A> Doc<A>.hang(i: Int): Doc<A> = nest(i).align()

public fun <A> Doc<A>.align(): Doc<A> = column { k ->
    nesting { i -> this.nest(k - i) }
}

public infix fun <A> Doc<A>.spaced(d: Doc<A>): Doc<A> = this + " ".text() + d

public infix fun <A> Doc<A>.line(d: Doc<A>): Doc<A> = this + line() + d
public infix fun <A> Doc<A>.softLine(d: Doc<A>): Doc<A> = this + softLine() + d
public infix fun <A> Doc<A>.lineBreak(d: Doc<A>): Doc<A> = this + lineBreak() + d
public infix fun <A> Doc<A>.softLineBreak(d: Doc<A>): Doc<A> = this + softLineBreak() + d

public fun <A> List<Doc<A>>.list(): Doc<A> = encloseSep(
    (lBracket() + space()).flatAlt(
        lBracket()
    ),
    (hardLine() + rBracket()).flatAlt(
        rBracket()
    ),
    comma() + space()
).group()

public fun <A> List<Doc<A>>.tupled(): Doc<A> = encloseSep(
    (lParen() + space()).flatAlt(lParen()),
    (hardLine() + rParen()).flatAlt(rParen()),
    comma() + space()
).group()

public fun <A> List<Doc<A>>.semiBraces(): Doc<A> = encloseSep(
    (lBrace() + space()).flatAlt(lBrace()),
    (hardLine() + rBrace()).flatAlt(rBrace()),
    comma() + space()
).group()

public fun <A> List<Doc<A>>.encloseSep(l: Doc<A>, r: Doc<A>, sep: Doc<A>): Doc<A> = when {
    isEmpty() -> l + r
    size == 1 -> l + first() + r
    else -> (sequenceOf(l) + repeat(sep))
        .zip(this.asSequence()) { a, b -> a + b }.toList().cat() + r
}

private fun <A> repeat(a: A): Sequence<A> = generateSequence { a }

public fun <A> List<Doc<A>>.punctuate(p: Doc<A>): List<Doc<A>> = when {
    isEmpty() -> emptyList()
    size == 1 -> listOf(first())
    else -> (first() to tail()).let { (x, xs) ->
        listOf(x + p) + xs.punctuate(p)
    }
}

public fun <A> List<Doc<A>>.foldDoc(f: (Doc<A>, Doc<A>) -> Doc<A>): Doc<A> = when {
    isEmpty() -> nil()
    else -> reduce(f)
}

public fun <A> List<Doc<A>>.cat(): Doc<A> = vCat().group()
public fun <A> List<Doc<A>>.fillCat(): Doc<A> = foldDoc { a, b -> a softLineBreak b }
public fun <A> List<Doc<A>>.hCat(): Doc<A> = foldDoc { a, b -> a + b }
public fun <A> List<Doc<A>>.vCat(): Doc<A> = foldDoc { a, b -> a lineBreak b }

public fun <A> List<Doc<A>>.sep(): Doc<A> = vSep().group()
public fun <A> List<Doc<A>>.fillSep(): Doc<A> = foldDoc { a, b -> a softLine b }
public fun <A> List<Doc<A>>.hSep(): Doc<A> = foldDoc { a, b -> a spaced b }
public fun <A> List<Doc<A>>.vSep(): Doc<A> = foldDoc { a, b -> a line b }

public fun <A> Doc<A>.enclose(l: Doc<A>, r: Doc<A>): Doc<A> = l + this + r

public fun String.doc(): Doc<Nothing> = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        if (fst.length >= length) fst.text()
        else fst.text() + hardLine() + substring(fst.length + 1).doc()
    }
}

public fun Boolean.doc(): Doc<Nothing> = toString().text()
public fun Byte.doc(): Doc<Nothing> = toString().text()
public fun Short.doc(): Doc<Nothing> = toString().text()
public fun Int.doc(): Doc<Nothing> = toString().text()
public fun Long.doc(): Doc<Nothing> = toString().text()
public fun Float.doc(): Doc<Nothing> = toString().text()
public fun Double.doc(): Doc<Nothing> = toString().text()
