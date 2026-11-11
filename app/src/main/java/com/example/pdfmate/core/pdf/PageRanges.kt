package com.example.pdfmate.core.pdf

/** Parses "1-3, 5, 8-10" into ranges, keeping each range's boundaries (used by split). */
fun parsePageRanges(specification: String, pageCount: Int? = null): List<List<Int>> {
    require(specification.isNotBlank()) { "Enter at least one page number." }
    val ranges = mutableListOf<List<Int>>()
    var selectedPageCount = 0L
    specification.split(',').forEach { item ->
        val token = item.trim()
        require(token.isNotEmpty()) { "Page selection contains an empty item." }
        val rangeParts = token.split('-')
        val pages = when (rangeParts.size) {
            1 -> listOf(
                rangeParts.single().trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number: $token")
            )
            2 -> {
                val first = rangeParts[0].trim().toIntOrNull()
                val last = rangeParts[1].trim().toIntOrNull()
                require(first != null && last != null && first <= last) { "Invalid page range: $token" }
                require(last.toLong() - first + 1 <= MAX_SELECTED_PAGES) {
                    "Select no more than $MAX_SELECTED_PAGES pages at a time."
                }
                (first..last).toList()
            }
            else -> throw IllegalArgumentException("Invalid page range: $token")
        }
        require(pages.all { it > 0 }) { "Page numbers must be greater than zero." }
        if (pageCount != null) {
            require(pages.all { it <= pageCount }) { "This PDF has only $pageCount pages." }
        }
        selectedPageCount += pages.size
        require(selectedPageCount <= MAX_SELECTED_PAGES) {
            "Select no more than $MAX_SELECTED_PAGES pages at a time."
        }
        ranges += pages
    }
    return ranges
}

fun parsePageList(specification: String, pageCount: Int? = null): List<Int> =
    parsePageRanges(specification, pageCount).flatten()

/** Groups pages 1..[pageCount] into consecutive chunks of [size] pages. */
fun chunkPages(pageCount: Int, size: Int): List<List<Int>> {
    require(size > 0) { "Pages per file must be at least 1." }
    return (1..pageCount).chunked(size)
}

private const val MAX_SELECTED_PAGES = 10_000
