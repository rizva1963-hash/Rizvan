package ru.riz.dohod

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * Экран «Запись голосом» по макету: слушаем прямо в приложении (без окна Google),
 * показываем расшифровку на лету, разбор фразы и кнопку «Записать».
 */
class VoiceActivity : ComponentActivity() {

    private enum class Phase { LISTENING, RESULT, ERROR }

    private var phase by mutableStateOf(Phase.LISTENING)
    private var partial by mutableStateOf("")
    private var finalText by mutableStateOf("")
    private var level by mutableStateOf(0f)
    private var seconds by mutableStateOf(0)
    private var errorText by mutableStateOf("")
    private var draft by mutableStateOf<Tx?>(null)
    private var autoCancelled by mutableStateOf(false)

    private var recognizer: SpeechRecognizer? = null
    private var preset: String? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else fallback()
    }

    private val systemDialog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val texts = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (res.resultCode != RESULT_OK || texts.isNullOrEmpty()) { finish(); return@registerForActivityResult }
        onTexts(texts)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        preset = intent?.getStringExtra("preset_category")
        setContent { AppTheme { VoiceScreen() } }
        if (savedInstanceState == null) {
            // Только для отладочной сборки: показать экран без микрофона (снимки экранов в CI).
            val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val demoText = if (debuggable) intent?.getStringExtra("demo_text") else null
            val demoPartial = if (debuggable) intent?.getStringExtra("demo_partial") else null
            when {
                demoText != null -> {
                    onTexts(listOf(demoText))
                    if (intent?.getBooleanExtra("demo_auto", false) != true) autoCancelled = true
                }
                demoPartial != null -> { partial = demoPartial; level = 0.7f; phase = Phase.LISTENING }
                else -> listen()
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }

    private fun listen() {
        partial = ""; finalText = ""; draft = null; seconds = 0; level = 0f; autoCancelled = false
        phase = Phase.LISTENING
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { fallback(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { level = 0f }
                override fun onError(error: Int) {
                    level = 0f
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            errorText = "Не расслышал. Скажите, например: «кофе 500»"
                            phase = Phase.ERROR
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fallback()
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                            errorText = "Нет интернета для распознавания речи"
                            phase = Phase.ERROR
                        }
                        else -> {
                            if (partial.isNotBlank()) onTexts(arrayListOf(partial)) else fallback()
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (list.isNullOrEmpty()) {
                        errorText = "Не расслышал. Попробуйте ещё раз"
                        phase = Phase.ERROR
                    } else onTexts(list)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { if (it.isNotBlank()) partial = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            recognizer?.startListening(i)
        } catch (_: Exception) {
            fallback()
        }
    }

    /** Если встроенное распознавание недоступно — системное окно Google. */
    private fun fallback() {
        recognizer?.destroy(); recognizer = null
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Например: «кофе 500»")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            systemDialog.launch(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Нет голосового ввода. Установите или включите приложение Google", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun onTexts(texts: List<String>) {
        val hit = texts.firstNotNullOfOrNull { t -> Parser.parse(t)?.let { p -> t to p } }
        finalText = hit?.first ?: texts.first()
        partial = finalText
        if (hit == null) {
            draft = null
            errorText = "Не нашёл сумму в «$finalText». Скажите, например: «такси 350»"
            phase = Phase.ERROR
            return
        }
        var tx = Tx.create(hit.second, hit.first)
        val pre = preset
        if (pre != null && !tx.income && tx.kind == Kind.NORMAL && tx.person.isBlank()) tx = tx.copy(category = pre)
        draft = tx
        phase = Phase.RESULT
    }

    private fun save() {
        val tx = draft ?: return
        Repo.add(this, tx.copy(ts = System.currentTimeMillis()))
        Widgets.refreshAll(this)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        Toast.makeText(this, "Записано: " + signedRub(tx), Toast.LENGTH_SHORT).show()
        finish()
    }

    @Composable
    private fun VoiceScreen() {
        val p = LocalPal.current
        var catOpen by remember { mutableStateOf(false) }
        // Автозапись: через 1,5 с после распознавания запись сохраняется сама, пока не нажали «Исправить»
        val autoOn = remember { Prefs.flag(this@VoiceActivity, Prefs.VOICE_AUTOSAVE, true) }
        val prog = remember { Animatable(1f) }
        val counting = autoOn && phase == Phase.RESULT && draft != null && !autoCancelled
        LaunchedEffect(phase, draft, autoCancelled) {
            if (counting) {
                prog.snapTo(1f)
                prog.animateTo(0f, tween(1500, easing = LinearEasing))
                if (phase == Phase.RESULT && !autoCancelled) save()
            }
        }

        LaunchedEffect(phase) {
            if (phase == Phase.LISTENING) {
                seconds = 0
                while (phase == Phase.LISTENING) { delay(1000); seconds++ }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                // Верхняя строка
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Отмена", color = p.muted, fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { finish() }
                            .padding(vertical = 10.dp)
                    )
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val t = rememberInfiniteTransition(label = "dot")
                        val a by t.animateFloat(1f, 0.4f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
                        Box(Modifier.graphicsLayer { alpha = if (phase == Phase.LISTENING) a else 1f }) {
                            Dot(if (phase == Phase.ERROR) p.red else p.gold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when (phase) {
                                Phase.LISTENING -> "Слушаю"
                                Phase.RESULT -> "Проверьте"
                                Phase.ERROR -> "Не получилось"
                            },
                            color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "0:%02d".format(seconds.coerceAtMost(59)), color = p.muted, fontSize = 16.sp,
                        modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.End,
                        style = TextStyle(fontFeatureSettings = TNUM)
                    )
                }

                // Расшифровка
                Spacer(Modifier.height(26.dp))
                Transcript(if (partial.isBlank() && phase == Phase.LISTENING) "" else partial, phase == Phase.LISTENING)

                // Волна
                Spacer(Modifier.height(26.dp))
                Wave(if (phase == Phase.LISTENING) level else 0f, phase == Phase.LISTENING)

                // Разбор
                Spacer(Modifier.height(26.dp))
                val d = draft
                if (phase == Phase.RESULT && d != null) {
                    CardBox(Modifier.rise(0), padding = 18.dp) {
                        KvRow("Тип", if (d.income) "Доход" else "Расход", showSep = false, onClick = {
                            autoCancelled = true
                            draft = d.copy(income = !d.income, category = if (!d.income) "Доход" else "Прочее", kind = Kind.NORMAL)
                        })
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Сумма", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(signedRub(d), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = TNUM))
                        }
                        KvRow("Категория", d.category, chevron = true, onClick = { autoCancelled = true; catOpen = true })
                        if (d.person.isNotBlank()) KvRow("Человек", d.person)
                        KvRow("Дата", Stats.shortDate(System.currentTimeMillis()))
                    }
                } else if (phase == Phase.ERROR) {
                    Text(errorText, color = p.muted, fontSize = 16.sp, lineHeight = 22.sp)
                } else {
                    Text(
                        if (preset != null) "Категория: $preset. Назовите сумму, например «500»"
                        else "Скажите, что и сколько: «кофе 500», «зарплата 600 тысяч», «дал в долг Марату 5000»",
                        color = p.muted, fontSize = 15.sp, lineHeight = 21.sp
                    )
                }

                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (counting) {
                        Text("Записываю через секунду. Если распознано не то, нажмите «Исправить»", color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(p.line)
                        ) {
                            Box(Modifier.fillMaxWidth(prog.value).height(4.dp).background(p.gold))
                        }
                        GoldButton("Записать сейчас", Modifier.fillMaxWidth()) { save() }
                        GhostButton("Исправить", Modifier.fillMaxWidth()) { autoCancelled = true }
                    } else {
                        GoldButton("Записать", Modifier.fillMaxWidth(), enabled = phase == Phase.RESULT) { save() }
                        GhostButton(if (phase == Phase.LISTENING) "Готово, я сказал" else "Сказать заново", Modifier.fillMaxWidth(), icon = R.drawable.ic_mic) {
                            if (phase == Phase.LISTENING) recognizer?.stopListening() else listen()
                        }
                    }
                }
            }

            val d = draft
            Sheet(visible = catOpen && d != null, onDismiss = { catOpen = false }) {
                if (d == null) return@Sheet
                SheetHeader("Категория", "Закрыть", null, onLeft = { catOpen = false })
                ChipGrid(if (d.income) Categories.incomeNames else Categories.expenseNames, d.category) {
                    draft = d.copy(category = it); catOpen = false
                }
            }
        }
    }

    /** Текст фразы крупно; сумма и ключевые слова — шампань. Мигающий курсор при записи. */
    @Composable
    private fun Transcript(text: String, listening: Boolean) {
        val p = LocalPal.current
        val t = rememberInfiniteTransition(label = "caret")
        val caret by t.animateFloat(1f, 0f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "c")
        val words = text.split(' ')
        val keyWords = Regex("""^\d|рубл|тыс|^кофе|^такси|^зарплат|^продукт|^бензин|^кафе|^долг|^вернул""")
        val str = buildAnnotatedString {
            words.forEachIndexed { i, w ->
                if (i > 0) append(' ')
                if (keyWords.containsMatchIn(w.lowercase())) withStyle(SpanStyle(color = p.gold)) { append(w) } else append(w)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (text.isEmpty()) {
                Text("Говорите…", style = serif(34.sp, p.muted, (-0.6).sp))
            } else {
                Text(str, style = serif(34.sp, p.text, (-0.6).sp).copy(lineHeight = 42.sp), modifier = Modifier.weight(1f, fill = false))
            }
            if (listening) {
                Box(
                    Modifier
                        .padding(start = 4.dp, bottom = 6.dp)
                        .width(2.dp)
                        .height(32.dp)
                        .graphicsLayer { alpha = if (caret > 0.5f) 1f else 0f }
                        .background(p.text)
                )
            }
        }
    }

    /** 48 полосок, высота зависит от громкости голоса. */
    @Composable
    private fun Wave(level: Float, active: Boolean) {
        val p = LocalPal.current
        val t = rememberInfiniteTransition(label = "wave")
        val time by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val n = 48
            val bw = 3.dp.toPx()
            val step = (size.width - bw) / (n - 1)
            for (i in 0 until n) {
                val phase = i * 0.55f + (i % 3) * 1.3f
                val speed = 1f + (i % 5) * 0.35f
                val wave = abs(sin(time * speed + phase))
                val base = 0.16f + 0.1f * ((i * 7) % 5) / 5f
                val k = if (active) (base + (0.25f + level * 0.75f) * wave).coerceAtMost(1f) else base
                val h = size.height * k
                drawRoundRect(
                    color = p.gold.copy(alpha = if (active) 1f else 0.45f),
                    topLeft = Offset(i * step, (size.height - h) / 2),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(bw / 2, bw / 2)
                )
            }
        }
    }
}
