package com.boohs.booksummary.service

object TextNormalizer {
    private val splitWord = Regex("(?<=[A-Za-z])-\\h*\\n\\h*(?=[A-Za-z])")
    private val horizontalWhitespace = Regex("[\\p{Zs}\\t]+")
    private val repeatedNewlines = Regex("\\n{3,}")

    fun normalize(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
        val joined = splitWord.replace(lines, "")
        val spaced = horizontalWhitespace.replace(joined, " ").lines().joinToString("\n") { it.trim() }
        return repeatedNewlines.replace(spaced, "\n\n").trim()
    }
}
