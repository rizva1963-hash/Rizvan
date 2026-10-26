package ru.riz.dohod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private val version = mutableStateOf(0)
    private val openTab = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTab.value = tabFrom(intent)
        setContent {
            AppTheme {
                val p = LocalPal.current
                SideEffect {
                    window.statusBarColor = p.bg.toArgb()
                    window.navigationBarColor = p.bg.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                }
                AppShell(
                    version = version.value,
                    requestedTab = openTab.value,
                    onTabConsumed = { openTab.value = -1 },
                    onVoice = { startActivity(Intent(this, VoiceActivity::class.java)) },
                    onChanged = {
                        version.value++
                        Widgets.refreshAll(this)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTab.value = tabFrom(intent)
    }

    private fun tabFrom(i: Intent?): Int {
        if (i == null) return -1
        val n = i.getIntExtra("tab", -1)
        return if (n >= 0) n else i.getStringExtra("tab")?.toIntOrNull() ?: -1
    }

    override fun onResume() {
        super.onResume()
        version.value++ // подтягиваем записи с виджетов и из банков
    }
}

/** Поделиться файлом (CSV для Excel или резервная копия). */
fun shareFile(ctx: Context, name: String, content: String, mime: String, title: String) {
    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, name)
    f.writeText(content)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, title))
}

fun buildCsv(list: List<Tx>): String {
    val sb = StringBuilder("﻿")
    sb.append("Дата;Время;Тип;Категория;Сумма;Название;Человек;Источник\n")
    list.sortedBy { it.ts }.forEach { t ->
        sb.append(Stats.date(t.ts)).append(';')
            .append(Stats.time(t.ts)).append(';')
            .append(if (t.income) "Доход" else "Расход").append(';')
            .append(t.category).append(';')
            .append(if (t.income) t.amount else -t.amount).append(';')
            .append(t.text.replace(";", ",").replace("\n", " ")).append(';')
            .append(t.person).append(';')
            .append(Source.label(t.source)).append('\n')
    }
    return sb.toString()
}

private data class NavItem(val label: String, val icon: Int)

private val nav = listOf(
    NavItem("Главная", R.drawable.ic_home),
    NavItem("Обзор", R.drawable.ic_chart),
    NavItem("История", R.drawable.ic_list),
    NavItem("Автоучёт", R.drawable.ic_auto)
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppShell(version: Int, requestedTab: Int, onTabConsumed: () -> Unit, onVoice: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val all = remember(version) { Repo.all(ctx) }
    val pendingCount = remember(version) { Repo.pending(ctx).size }

    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(requestedTab) {
        if (requestedTab in 0..3) { tab = requestedTab; onTabConsumed() }
    }
    var historyQuery by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var manual by remember { mutableStateOf<Boolean?>(null) }
    var personOpen by remember { mutableStateOf<String?>(null) }
    var balanceOpen by remember { mutableStateOf(false) }
    var budgetOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val json = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val n = Repo.importJson(ctx, json)
                Toast.makeText(ctx, "Восстановлено записей: $n", Toast.LENGTH_LONG).show()
                onChanged()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Не получилось прочитать файл копии", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> HomeScreen(
                            all, pendingCount,
                            onVoice = onVoice,
                            onManual = { manual = it },
                            onEdit = { editing = it },
                            onSettings = { settingsOpen = true },
                            onBalance = { balanceOpen = true },
                            onBudget = { budgetOpen = true },
                            onPerson = { personOpen = it },
                            onHistory = { historyQuery = ""; tab = 2 },
                            onAuto = { tab = 3 }
                        )
                        1 -> OverviewScreen(all, onCategory = { historyQuery = it; tab = 2 })
                        2 -> HistoryScreen(all, historyQuery, onEdit = { editing = it })
                        else -> AutoScreen(all, version, onChanged = onChanged, onEdit = { editing = it })
                    }
                }
            }
            BottomNav(tab) { if (it == 2) historyQuery = ""; tab = it }
        }

        EditSheet(editing, onDismiss = { editing = null }, onChanged = onChanged)
        ManualSheet(manual, onDismiss = { manual = null }, onChanged = onChanged)
        PersonSheet(personOpen, all, onDismiss = { personOpen = null }, onEdit = { personOpen = null; editing = it })
        BalanceSheet(balanceOpen, all, onDismiss = { balanceOpen = false }, onChanged = onChanged)
        BudgetSheet(budgetOpen, onDismiss = { budgetOpen = false }, onChanged = onChanged)
        SettingsSheet(
            settingsOpen, all.size,
            onDismiss = { settingsOpen = false },
            onBalance = { balanceOpen = true },
            onBudget = { budgetOpen = true },
            onCsv = {
                if (all.isEmpty()) Toast.makeText(ctx, "Пока нечего выгружать", Toast.LENGTH_SHORT).show()
                else shareFile(ctx, "dohod-rashod.csv", buildCsv(all), "text/csv", "Экспорт в Excel")
            },
            onBackup = { shareFile(ctx, "dohod-backup.json", Repo.exportJson(ctx), "application/json", "Сохранить копию") },
            onRestore = { importer.launch("*/*") },
            onClear = { clearAsk = true }
        )
        ConfirmSheet(
            visible = clearAsk,
            title = "Удалить все записи?",
            text = "Это действие нельзя отменить. Сначала сохраните резервную копию.",
            action = "Удалить всё",
            onDismiss = { clearAsk = false },
            onConfirm = { Repo.clear(ctx); clearAsk = false; onChanged() }
        )
    }
}

@Composable
private fun BottomNav(tab: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.bg)
            .drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        nav.forEachIndexed { i, item ->
            val active = i == tab
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Ic(item.icon, if (active) p.text else p.muted, 22.dp)
                Text(
                    item.label, fontSize = 12.sp,
                    color = if (active) p.text else p.muted,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}
