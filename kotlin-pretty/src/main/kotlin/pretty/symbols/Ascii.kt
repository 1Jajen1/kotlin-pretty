package pretty.symbols

import pretty.Doc
import pretty.enclose
import pretty.text

fun <A> Doc<A>.sQuotes(): Doc<A> = enclose(
    sQuote(),
    sQuote()
)
fun <A> Doc<A>.dQuotes(): Doc<A> = enclose(
    dQuote(),
    dQuote()
)
fun <A> Doc<A>.braces(): Doc<A> = enclose(
    lBrace(),
    rBrace()
)
fun <A> Doc<A>.parens(): Doc<A> = enclose(
    lParen(),
    rParen()
)
fun <A> Doc<A>.brackets(): Doc<A> = enclose(
    lBracket(),
    rBracket()
)
fun <A> Doc<A>.angles(): Doc<A> = enclose(
    lAngle(),
    rAngle()
)

fun lBracket(): Doc<Nothing> = "[".text()
fun rBracket(): Doc<Nothing> = "]".text()
fun lParen(): Doc<Nothing> = "(".text()
fun rParen(): Doc<Nothing> = ")".text()
fun lBrace(): Doc<Nothing> = "{".text()
fun rBrace(): Doc<Nothing> = "}".text()
fun lAngle(): Doc<Nothing> = "<".text()
fun rAngle(): Doc<Nothing> = ">".text()

fun comma(): Doc<Nothing> = ",".text()
fun space(): Doc<Nothing> = " ".text()
fun sQuote(): Doc<Nothing> = "\'".text()
fun dQuote(): Doc<Nothing> = "\"".text()
fun semiColon(): Doc<Nothing> = ";".text()
fun colon(): Doc<Nothing> = ":".text()
fun dot(): Doc<Nothing> = ".".text()
fun slash(): Doc<Nothing> = "/".text()
fun backslash(): Doc<Nothing> = "\\".text()
fun equals(): Doc<Nothing> = "=".text()
fun pipe(): Doc<Nothing> = "|".text()