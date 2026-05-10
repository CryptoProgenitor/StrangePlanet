package com.quokkalabs.strangeplanet.domain

import com.quokkalabs.strangeplanet.data.model.DayType
import com.quokkalabs.strangeplanet.data.model.TimeOfDay
import com.quokkalabs.strangeplanet.data.repository.SchoolCalendarRepository
import java.time.LocalDateTime

data class DayContext(
    val timeOfDay: TimeOfDay,
    val dayType: DayType,
)

class DayContextResolver(private val calendarRepo: SchoolCalendarRepository) {

    fun resolve(dateTime: LocalDateTime = LocalDateTime.now()): DayContext =
        DayContext(
            timeOfDay = TimeOfDay.fromHour(dateTime.hour),
            dayType = calendarRepo.getDayType(dateTime.toLocalDate()),
        )
}
