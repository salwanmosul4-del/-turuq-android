package iq.turuq.core.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import iq.turuq.core.model.ReportType
import iq.turuq.core.util.Fmt
import java.util.Locale

/**
 * التوجيه الصوتي بالعربية. يعتمد على محرك TTS في الجهاز (يجب أن تتوفر حزمة صوت عربية).
 * يُنطق كل تنبيه مرة واحدة: المناورة عند ٥٠٠ م و١٥٠ م و٣٠ م، والتحذيرات عند ظهورها.
 */
class Voice(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context.applicationContext) { st ->
            val r = if (st == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED else TextToSpeech.LANG_NOT_SUPPORTED
            ready = r >= TextToSpeech.LANG_AVAILABLE
        }
    }

    var enabled = true
    private var lastStep = -1
    private val said = HashSet<String>()

    fun onState(st: NavState) {
        if (!enabled || !ready) return
        if (st.stepIndex != lastStep) { lastStep = st.stepIndex; said.removeAll { it.startsWith("m") } }
        for (th in intArrayOf(500, 150, 30)) {
            val key = "m$lastStep-$th"
            if (st.distToManeuverM <= th && st.distToManeuverM > 0 && said.add(key)) {
                // نتجاوز العتبات الأبعد إذا كنا أقرب منها أصلاً
                say(if (th == 30) st.step.instruction else "بعد " + Fmt.dist(st.distToManeuverM) + "، " + st.step.instruction)
                break
            }
        }
        st.nextWarning?.let { w ->
            if (said.add("w" + w.reportId)) {
                val t = ReportType.fromKey(w.type)?.labelAr ?: return@let
                say("انتبه، $t بعد " + Fmt.dist(st.warningDistM))
            }
        }
        if (st.arrived && said.add("arrived")) say("وصلت إلى وجهتك")
    }

    fun reset() { said.clear(); lastStep = -1 }
    fun say(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString()) }
    fun shutdown() { tts?.shutdown() }
}
