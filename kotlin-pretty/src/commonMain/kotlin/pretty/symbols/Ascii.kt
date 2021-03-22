package pretty.symbols

import pretty.Doc
import pretty.enclose
import pretty.text

public fun <A> Doc<A>.sQuotes(): Doc<A> = enclose(
    sQuote(),
    sQuote()
)
public fun <A> Doc<A>.dQuotes(): Doc<A> = enclose(
    dQuote(),
    dQuote()
)
public fun <A> Doc<A>.braces(): Doc<A> = enclose(
    lBrace(),
    rBrace()
)
public fun <A> Doc<A>.parens(): Doc<A> = enclose(
    lParen(),
    rParen()
)
public fun <A> Doc<A>.brackets(): Doc<A> = enclose(
    lBracket(),
    rBracket()
)
public fun <A> Doc<A>.angles(): Doc<A> = enclose(
    lAngle(),
    rAngle()
)

public fun lBracket(): Doc<Nothing> = "[".text()
public fun rBracket(): Doc<Nothing> = "]".text()
public fun lParen(): Doc<Nothing> = "(".text()
public fun rParen(): Doc<Nothing> = ")".text()
public fun lBrace(): Doc<Nothing> = "{".text()
public fun rBrace(): Doc<Nothing> = "}".text()
public fun lAngle(): Doc<Nothing> = "<".text()
public fun rAngle(): Doc<Nothing> = ">".text()

public fun comma(): Doc<Nothing> = ",".text()
public fun space(): Doc<Nothing> = " ".text()
public fun sQuote(): Doc<Nothing> = "\'".text()
public fun dQuote(): Doc<Nothing> = "\"".text()
public fun semiColon(): Doc<Nothing> = ";".text()
public fun colon(): Doc<Nothing> = ":".text()
public fun dot(): Doc<Nothing> = ".".text()
public fun slash(): Doc<Nothing> = "/".text()
public fun backslash(): Doc<Nothing> = "\\".text()
public fun equals(): Doc<Nothing> = "=".text()
public fun pipe(): Doc<Nothing> = "|".text()