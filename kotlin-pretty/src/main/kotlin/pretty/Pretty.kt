package pretty

import pretty.symbols.*

fun <A> Doc<A>.renderPretty(): SimpleDoc<A> = layoutPretty(PageWidth.Available(80, 0.4F))

fun <A> Doc<A>.pretty(maxWidth: Int = 80, ribbonWidth: Float = 0.4F): String =
    layoutPretty(PageWidth.Available(maxWidth, ribbonWidth)).renderString()

// primitives
fun nil(): Doc<Nothing> = Doc(Eval.now(DocF.Nil))

fun String.text(): Doc<Nothing> = Doc(Eval.now(DocF.Text(this)))

fun line(): Doc<Nothing> = hardLine().flatAlt(" ".text())

fun lineBreak(): Doc<Nothing> = hardLine().flatAlt(nil())

fun softLine(): Doc<Nothing> = line().group()

fun softLineBreak(): Doc<Nothing> = lineBreak().group()

fun hardLine(): Doc<Nothing> = Doc(Eval.now(DocF.Line))

fun <A> Doc<A>.nest(i: Int): Doc<A> = Doc(Eval.now(DocF.Nest(i, this)))

fun <A> Doc<A>.group(): Doc<A> = changesUponFlattening()?.let {
    Doc(Eval.now(DocF.Union(it, this@group)))
} ?: this

fun <A> Doc<A>.changesUponFlattening(): Doc<A>? {
    fun Doc<A>.go(): Eval<Doc<A>?> =
        when (val dF = unDoc()) {
            is DocF.FlatAlt -> Eval.now(dF.r.flatten())
            is DocF.Line -> Eval.now(Doc<A>(Eval.now(DocF.Fail)))
            is DocF.Union -> Eval.now(dF.l)
            is DocF.Nest -> dF.doc.go().andThen { it?.let { Doc(Eval.now(DocF.Nest(dF.i, it))) } }
            is DocF.Annotated -> dF.doc.go().andThen { it?.let { Doc(Eval.now(DocF.Annotated(dF.ann, it))) } }

            is DocF.Column -> Eval.now(Doc(Eval.now(DocF.Column(AndThen(dF.doc).andThen { it.flatten() }))))
            is DocF.Nesting -> Eval.now(Doc(Eval.now(DocF.Nesting(AndThen(dF.doc).andThen { it.flatten() }))))
            is DocF.WithPageWidth -> Eval.now(Doc(Eval.now(DocF.WithPageWidth(AndThen(dF.doc).andThen { it.flatten() }))))

            is DocF.Combined -> {
                val lEval = Eval.defer { dF.l.go() }
                val rEval = Eval.defer { dF.r.go() }
                lEval.flatMap { l ->
                    l?.let { lD ->
                        rEval.andThen { r ->
                            r?.let { rD ->
                                Doc(Eval.now(DocF.Combined(lD, rD)))
                            } ?: Doc(Eval.now(DocF.Combined(lD, dF.r)))
                        }
                    } ?: rEval.andThen { r ->
                        r?.let { rD ->
                            Doc(Eval.now(DocF.Combined(dF.l, rD)))
                        } ?: null
                    }
                }
            }

            else -> Eval.now(null)
        }

    return go().invoke()
}

fun <A> Doc<A>.flatten(): Doc<A> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.FlatAlt -> it.r.flatten().unDoc
        is DocF.Union -> it.l.flatten().unDoc
        is DocF.Nest -> Eval.now(it.copy(doc = it.doc.flatten()))
        is DocF.Combined -> Eval.now(it.copy(l = it.l.flatten(), r = it.r.flatten()))
        is DocF.Annotated -> Eval.now(it.copy(doc = it.doc.flatten()))
        is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(AndThen(it.doc).andThen { it.flatten() }))
        is DocF.Column -> Eval.now(DocF.Column(AndThen(it.doc).andThen { it.flatten() }))
        is DocF.Nesting -> Eval.now(DocF.Nesting(AndThen(it.doc).andThen { it.flatten() }))
        is DocF.Line -> Eval.now(DocF.Fail)
        else -> Eval.now(it)
    }
})

fun <A> column(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Column(f)))

fun <A> nesting(f: (Int) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.Nesting(f)))

fun <A> pageWidth(f: (PageWidth) -> Doc<A>): Doc<A> = Doc(Eval.now(DocF.WithPageWidth(f)))

fun <A> Doc<A>.flatAlt(other: Doc<A>): Doc<A> = Doc(Eval.now(DocF.FlatAlt(this, other)))

fun <A> Doc<A>.annotate(ann: A): Doc<A> = Doc(Eval.now(DocF.Annotated(ann, this)))

fun <A, B> Doc<A>.reAnnotate(f: (A) -> B): Doc<B> = map(f)

// TODO Is this recursive bit safe?
fun <A, B> Doc<A>.alterAnnotations(f: (A) -> List<B>): Doc<B> = Doc(unDoc.flatMap {
    when (it) {
        is DocF.Annotated -> f(it.ann).fold(it.doc.alterAnnotations(f)) { doc, ann ->
            Doc(Eval.now(DocF.Annotated(ann, doc)))
        }.unDoc
        is DocF.Text -> Eval.now(DocF.Text(it.str))
        is DocF.Union -> Eval.now(DocF.Union(it.l.alterAnnotations(f), it.r.alterAnnotations(f)))
        is DocF.Combined -> Eval.now(DocF.Combined(it.l.alterAnnotations(f), it.r.alterAnnotations(f)))
        is DocF.WithPageWidth -> Eval.now(DocF.WithPageWidth(AndThen(it.doc).andThen { it.alterAnnotations(f) }))
        is DocF.Column -> Eval.now(DocF.Column(AndThen(it.doc).andThen { it.alterAnnotations(f) }))
        is DocF.Nesting -> Eval.now(DocF.Nesting(AndThen(it.doc).andThen { it.alterAnnotations(f) }))
        is DocF.Nest -> Eval.now(DocF.Nest(it.i, it.doc.alterAnnotations(f)))
        is DocF.FlatAlt -> Eval.now(DocF.FlatAlt(it.l.alterAnnotations(f), it.r.alterAnnotations(f)))
        is DocF.Line -> Eval.now(DocF.Line)
        is DocF.Nil -> Eval.now(DocF.Nil)
        is DocF.Fail -> Eval.now(DocF.Fail)
    }
})

fun <A> Doc<A>.unAnnotate(): Doc<Nothing> = alterAnnotations { emptyList<Nothing>() }

// combinators
fun <A> Doc<A>.fillBreak(i: Int): Doc<A> = width { w ->
    if (w > i) lineBreak().nest(i) else spaces(i - w).text()
}

fun <A> Doc<A>.fill(i: Int): Doc<A> = width { w ->
    if (w > i) nil()
    else spaces(i - w).text()
}

fun <A> Doc<A>.width(f: (Int) -> Doc<A>): Doc<A> = column { k1 -> this + column { k2 -> f(k2 - k1) } }

fun <A> Doc<A>.indent(i: Int): Doc<A> = (spaces(i).text() + this).hang(i)

fun <A> Doc<A>.hang(i: Int): Doc<A> = nest(i).align()

fun <A> Doc<A>.align(): Doc<A> = column { k ->
    nesting { i -> this.nest(k - i) }
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
    (lBracket() + space()).flatAlt(
        lBracket()
    ),
    (rBracket() + space()).flatAlt(
        rBracket()
    ),
    comma() + space()
).group()

fun <A> List<Doc<A>>.tupled(): Doc<A> = encloseSep(
    (lParen() + space()).flatAlt(lParen()),
    (rParen() + space()).flatAlt(rParen()),
    comma() + space()
).group()

fun <A> List<Doc<A>>.semiBraces(): Doc<A> = encloseSep(
    (lBrace() + space()).flatAlt(lBrace()),
    (rBrace() + space()).flatAlt(rBrace()),
    comma() + space()
).group()

fun <A> List<Doc<A>>.encloseSep(l: Doc<A>, r: Doc<A>, sep: Doc<A>): Doc<A> = when {
    isEmpty() -> l + r
    size == 1 -> l + first() + r
    else -> (sequenceOf(l) + repeat(sep))
        .zip(this.asSequence()) { a, b -> a + b }.toList().cat() + r
}

private fun <A> repeat(a: A): Sequence<A> = generateSequence { a }

fun <A> List<Doc<A>>.punctuate(p: Doc<A>): List<Doc<A>> = when {
    isEmpty() -> emptyList()
    size == 1 -> listOf(first())
    else -> (first() to tail()).let { (x, xs) ->
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

fun String.doc(): Doc<Nothing> = when {
    isEmpty() -> nil()
    else -> takeWhile { it != '\n' }.let { fst ->
        if (fst.length >= length) fst.text()
        else fst.text() + hardLine() + substring(fst.length + 1).doc()
    }
}

fun Boolean.doc(): Doc<Nothing> = toString().text()
fun Byte.doc(): Doc<Nothing> = toString().text()
fun Short.doc(): Doc<Nothing> = toString().text()
fun Int.doc(): Doc<Nothing> = toString().text()
fun Long.doc(): Doc<Nothing> = toString().text()
fun Float.doc(): Doc<Nothing> = toString().text()
fun Double.doc(): Doc<Nothing> = toString().text()
