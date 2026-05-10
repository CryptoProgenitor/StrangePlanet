package com.quokkalabs.strangeplanet

import android.app.Application
import com.quokkalabs.strangeplanet.audio.SoundManager
import com.quokkalabs.strangeplanet.audio.TtsManager
import com.quokkalabs.strangeplanet.data.repository.SayingsRepository
import com.quokkalabs.strangeplanet.data.repository.SchoolCalendarRepository
import com.quokkalabs.strangeplanet.domain.DayContextResolver

class StrangePlanetApp : Application() {

    lateinit var soundManager: SoundManager
        private set

    lateinit var ttsManager: TtsManager
        private set

    val schoolCalendarRepository = SchoolCalendarRepository()
    val sayingsRepository = SayingsRepository()
    val dayContextResolver = DayContextResolver(schoolCalendarRepository)

    override fun onCreate() {
        super.onCreate()
        soundManager = SoundManager(this)
        ttsManager = TtsManager(this)
    }
}
