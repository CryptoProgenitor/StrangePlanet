package com.quokkalabs.strangeplanet.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.quokkalabs.strangeplanet.data.model.CreatureType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsProgress(
    val creatureType: CreatureType,
    val start: Int,
    val end: Int,
)

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var initialized = false
    private var currentCreature: CreatureType? = null

    private val _progress = MutableStateFlow<TtsProgress?>(null)
    val progress: StateFlow<TtsProgress?> = _progress.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.UK
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    _progress.value = null
                    currentCreature = null
                }
                override fun onError(utteranceId: String?) {
                    _progress.value = null
                    currentCreature = null
                }
                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    val creature = currentCreature ?: return
                    _progress.value = TtsProgress(creature, start, end)
                }
            })
            initialized = true
        }
    }

    fun speak(text: String, creatureType: CreatureType) {
        if (!initialized) return
        val engine = tts ?: return

        currentCreature = creatureType
        _progress.value = null

        val params = getVoiceParams(creatureType)
        engine.setPitch(params.pitch)
        engine.setSpeechRate(params.rate)

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, "sp_${System.nanoTime()}")
    }

    private fun getVoiceParams(type: CreatureType): VoiceParams = when (type) {
        CreatureType.ALIEN_MUM -> VoiceParams(pitch = 1.2f, rate = 0.9f)
        CreatureType.ALIEN_DAD -> VoiceParams(pitch = 0.7f, rate = 0.85f)
        CreatureType.ALIEN_KID -> VoiceParams(pitch = 1.6f, rate = 1.2f)
        CreatureType.CAT -> VoiceParams(pitch = 1.8f, rate = 1.1f)
        CreatureType.DOG -> VoiceParams(pitch = 0.9f, rate = 1.3f)
        CreatureType.SOCKS -> VoiceParams(pitch = 0.5f, rate = 0.7f)
        CreatureType.UNICORN -> VoiceParams(pitch = 1.4f, rate = 0.8f)
        CreatureType.ROLLSUCK -> VoiceParams(pitch = 0.6f, rate = 0.75f)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }

    private data class VoiceParams(val pitch: Float, val rate: Float)
}
