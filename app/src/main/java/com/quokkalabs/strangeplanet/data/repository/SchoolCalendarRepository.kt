package com.quokkalabs.strangeplanet.data.repository

import com.quokkalabs.strangeplanet.data.model.AcademicYear
import com.quokkalabs.strangeplanet.data.model.DayType
import com.quokkalabs.strangeplanet.data.model.TermDates
import java.time.DayOfWeek
import java.time.LocalDate

class SchoolCalendarRepository {

    private val academicYears = listOf(year2025_2026, year2026_2027)

    fun getDayType(date: LocalDate): DayType {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.WEEKEND
        }

        for (year in academicYears) {
            for (term in year.allTerms) {
                if (isInHalfTerm(date, term)) return DayType.HALF_TERM
                if (isInTerm(date, term)) return DayType.SCHOOL_DAY
            }
        }

        return DayType.HOLIDAY
    }

    private fun isInTerm(date: LocalDate, term: TermDates): Boolean =
        !date.isBefore(term.termStart) && !date.isAfter(term.termEnd)

    private fun isInHalfTerm(date: LocalDate, term: TermDates): Boolean =
        !date.isBefore(term.halfTermStart) && !date.isAfter(term.halfTermEnd)

    companion object {
        // Wychwood School term dates (day pupils start dates used)
        private val year2025_2026 = AcademicYear(
            michaelmas = TermDates(
                termStart = LocalDate.of(2025, 9, 3),
                halfTermStart = LocalDate.of(2025, 10, 17),
                halfTermEnd = LocalDate.of(2025, 11, 2),
                termEnd = LocalDate.of(2025, 12, 11),
            ),
            hilary = TermDates(
                termStart = LocalDate.of(2026, 1, 6),
                halfTermStart = LocalDate.of(2026, 2, 13),
                halfTermEnd = LocalDate.of(2026, 2, 22),
                termEnd = LocalDate.of(2026, 3, 27),
            ),
            trinity = TermDates(
                termStart = LocalDate.of(2026, 4, 20),
                halfTermStart = LocalDate.of(2026, 5, 22),
                halfTermEnd = LocalDate.of(2026, 5, 31),
                termEnd = LocalDate.of(2026, 7, 3),
            ),
        )

        private val year2026_2027 = AcademicYear(
            michaelmas = TermDates(
                termStart = LocalDate.of(2026, 9, 3),
                halfTermStart = LocalDate.of(2026, 10, 16),
                halfTermEnd = LocalDate.of(2026, 11, 1),
                termEnd = LocalDate.of(2026, 12, 10),
            ),
            hilary = TermDates(
                termStart = LocalDate.of(2027, 1, 6),
                halfTermStart = LocalDate.of(2027, 2, 12),
                halfTermEnd = LocalDate.of(2027, 2, 21),
                termEnd = LocalDate.of(2027, 3, 25),
            ),
            trinity = TermDates(
                termStart = LocalDate.of(2027, 4, 20),
                halfTermStart = LocalDate.of(2027, 5, 28),
                halfTermEnd = LocalDate.of(2027, 6, 6),
                termEnd = LocalDate.of(2027, 7, 9),
            ),
        )
    }
}
