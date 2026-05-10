package com.quokkalabs.strangeplanet.data.model

import java.time.LocalDate

data class TermDates(
    val termStart: LocalDate,
    val halfTermStart: LocalDate,
    val halfTermEnd: LocalDate,
    val termEnd: LocalDate,
)

data class AcademicYear(
    val michaelmas: TermDates,
    val hilary: TermDates,
    val trinity: TermDates,
) {
    val allTerms: List<TermDates> get() = listOf(michaelmas, hilary, trinity)
}
