package com.boohs.booksummary.service

object SummarySizing {
    fun keyPointCount(charCount: Int): Int =
        when {
            charCount <= 1500 -> 3
            charCount <= 4000 -> 4
            charCount <= 7000 -> 5
            else -> 6
        }
}
