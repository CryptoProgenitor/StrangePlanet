package com.quokkalabs.strangeplanet.data.model

enum class TimeOfDay {
    MORNING,    // 06:00 – 11:59
    AFTERNOON,  // 12:00 – 16:59
    EVENING,    // 17:00 – 20:59
    NIGHT;      // 21:00 – 05:59

    companion object {
        fun fromHour(hour: Int): TimeOfDay = when (hour) {
            in 6..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

enum class DayType {
    SCHOOL_DAY,
    WEEKEND,
    HALF_TERM,
    HOLIDAY;

    val isSchoolFree: Boolean get() = this != SCHOOL_DAY
}

data class Saying(
    val text: String,
    val creatureType: CreatureType,
    val dayTypes: Set<DayType> = DayType.entries.toSet(),
    val timesOfDay: Set<TimeOfDay> = TimeOfDay.entries.toSet(),
)
