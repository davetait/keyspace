package ai.stxt.keyspace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Wraps Android's [SpeechRecognizer]. Must be created and used on the main thread. */
class SpeechInput(
    context: Context,
    private val onListeningChange: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val recognizer: SpeechRecognizer? =
        if (isAvailable) SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener()) } else null

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recognizer?.startListening(intent) ?: onError("Speech recognition isn't available on this device")
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
    }

    private fun listener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListeningChange(true)
        override fun onEndOfSpeech() = onListeningChange(false)
        override fun onResults(results: Bundle?) {
            onListeningChange(false)
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onResult)
        }
        override fun onError(error: Int) {
            onListeningChange(false)
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No connection for speech"
                    else -> "Speech error ($error)"
                }
            )
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
