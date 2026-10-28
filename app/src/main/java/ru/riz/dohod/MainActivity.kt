package ru.riz.dohod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {
    private val version = mutableStateOf(0)
    private val openTab = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под системными панелями, но сами отступаем от часов, батареи и жестовой полосы.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        openTab.value = tabFrom(intent)
        setContent {
            AppTheme {
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

private val navLeft = listOf(NavItem("Главная", R.drawable.ic_home), NavItem("Обзор", R.drawable.ic_chart))
private val navRight = listOf(NavItem("История", R.drawable.ic_list), NavItem("Ещё", R.drawable.ic_sliders))

private const val BAR = 64
private const val OVERHANG = 26

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppShell(version: Int, requestedTab: Int, onTabConsumed: () -> Unit, onVoice: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val all = remember(version) { Repo.all(ctx) }
    val pendingCount = remember(version) { Repo.pending(ctx).size }

    var tab by remember { mutableStateOf(0) }
    var morePage by remember { mutableStateOf(0) } // 0 меню «Ещё», 1 экран автоучёта
    LaunchedEffect(requestedTab) {
        if (requestedTab in 0..4) {
            if (requestedTab == 4) { tab = 3; morePage = 1 } else { tab = requestedTab; if (requestedTab == 3) morePage = 0 }
            onTabConsumed()
        }
    }
    var historyQuery by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var manual by remember { mutableStateOf<Boolean?>(null) }
    var personOpen by remember { mutableStateOf<String?>(null) }
    var balanceOpen by remember { mutableStateOf(false) }
    var budgetOpen by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }
    var catsOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = tab != 0) { tab = 0 }

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

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalDockPad provides (BAR.dp + navBottom + 24.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .drawBehind {
                    if (p.skin == Skin.LEDGER) {
                        var y = 32.dp.toPx()
                        while (y < size.height) {
                            drawRect(p.line.copy(alpha = 0.35f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                            y += 32.dp.toPx()
                        }
                    }
                }
        ) {
            // Экран. Сверху отступ под часы и батарею, снизу его закрывает док.
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> HomeScreen(
                            all, pendingCount,
                            onManual = { manual = it },
                            onEdit = { editing = it },
                            onBalance = { balanceOpen = true },
                            onBudget = { budgetOpen = true },
                            onPerson = { personOpen = it },
                            onHistory = { historyQuery = ""; tab = 2 },
                            onAuto = { morePage = 1; tab = 3 }
                        )
                        1 -> OverviewScreen(all, onCategory = { historyQuery = it; tab = 2 })
                        2 -> HistoryScreen(all, historyQuery, onEdit = { editing = it })
                        else -> MoreScreen(
                            page = morePage, onPage = { morePage = it },
                            all = all, version = version, pendingCount = pendingCount,
                            onChanged = onChanged, onEdit = { editing = it },
                            onBalance = { balanceOpen = true },
                            onBudget = { budgetOpen = true },
                            onCats = { catsOpen = true },
                            onCsv = {
                                if (all.isEmpty()) Toast.makeText(ctx, "Пока нечего выгружать", Toast.LENGTH_SHORT).show()
                                else shareFile(ctx, "dohod-rashod.csv", buildCsv(all), "text/csv", "Экспорт в Excel")
                            },
                            onBackup = { shareFile(ctx, "dohod-backup.json", Repo.exportJson(ctx), "application/json", "Сохранить копию") },
                            onRestore = { importer.launch("*/*") },
                            onClear = { clearAsk = true }
                        )
                    }
                }
            }

            Dock(
                tab = tab, pending = pendingCount,
                onSelect = {
                    if (it == 2) historyQuery = ""
                    if (it == 3 && tab == 3) morePage = 0
                    tab = it
                },
                onVoice = onVoice,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            EditSheet(editing, onDismiss = { editing = null }, onChanged = onChanged)
            ManualSheet(manual, onDismiss = { manual = null }, onChanged = onChanged)
            PersonSheet(personOpen, all, onDismiss = { personOpen = null }, onEdit = { personOpen = null; editing = it })
            BalanceSheet(balanceOpen, all, onDismiss = { balanceOpen = false }, onChanged = onChanged)
            BudgetSheet(budgetOpen, onDismiss = { budgetOpen = false }, onChanged = onChanged)
            CategoriesSheet(catsOpen, onDismiss = { catsOpen = false })
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
}

/**
 * Нижняя панель в зоне большого пальца. Четыре раздела и крупный микрофон по центру:
 * главное действие приложения (сказать вслух) всегда под рукой одной рукой.
 */
@Composable
private fun Dock(tab: Int, pending: Int, onSelect: (Int) -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height((BAR + OVERHANG).dp)) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BAR.dp)
                    .background(p.bg)
                    .drawBehind {
                        if (p.skin == Skin.LEDGER) {
                            drawRect(p.text, size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
                            drawRect(p.text, topLeft = Offset(0f, 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 0.8.dp.toPx()))
                        } else drawRect(p.line, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                    }
            ) {
                navLeft.forEachIndexed { i, item -> DockItem(item, tab == i, false, Modifier.weight(1f)) { onSelect(i) } }
                Spacer(Modifier.weight(1.15f))
                navRight.forEachIndexed { j, item -> DockItem(item, tab == j + 2, j == 1 && pending > 0, Modifier.weight(1f)) { onSelect(j + 2) } }
            }
            // Ключ: заводная головка или печать, зависит от скина
            DockMic(p, Modifier.align(Alignment.TopCenter).pressable(onVoice))
        }
        Spacer(Modifier.fillMaxWidth().height(navBottom).background(p.bg))
    }
}

@Composable
private fun DockItem(item: NavItem, active: Boolean, badge: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Ic(item.icon, if (active) p.gold else p.muted, 24.dp)
            if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)) { Dot(p.gold, 8.dp) }
        }
        Text(
            item.label, fontSize = 12.sp,
            color = if (active) p.text else p.muted,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
