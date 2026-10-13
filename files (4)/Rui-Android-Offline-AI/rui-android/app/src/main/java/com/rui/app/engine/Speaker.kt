package com.rui.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** On-device text-to-speech. Works offline when the system voice data is installed. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.getDefault()
    }

    fun speak(markdown: String) {
        if (!ready) return
        val plain = markdown
            .replace(Regex("```[\\s\\S]*?```"), " code omitted. ")
            .replace(Regex("[*_`#>]"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "rui")
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}
