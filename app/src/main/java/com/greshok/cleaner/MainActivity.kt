package com.greshok.cleaner

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel

private val Green = Color(0xFF1E8E5A)
private val Blue = Color(0xFF1565C0)
private val Orange = Color(0xFFE65100)
private val Purple = Color(0xFF6A1B9A)
private val Red = Color(0xFFC62828)
private val StepsBg = Color(0xFFFFF3E0)
private val Bg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF111111)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Всегда светлая тема, без «умных» цветов — чтобы везде выглядело одинаково
            MaterialTheme(colorScheme = lightColorScheme(background = Bg, onBackground = Ink)) {
                MomScreen(viewModel())
            }
        }
    }
}

@Composable
fun MomScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var hasAccess by remember { mutableStateOf(Perms.hasFileAccess(ctx)) }
    val browsers = remember { Perms.installedBrowsers(ctx) }

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasAccess = it }

    LifecycleResumeEffect(Unit) {
        hasAccess = Perms.hasFileAccess(ctx)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (!hasAccess) {
            BigText("Мама, привет! 👋\n\nСначала нужно один раз дать разрешение.")
            SmallText("Откроется окно. Там нажми на переключатель, чтобы он стал включённым, и потом нажми «Назад».")
            BigButton("МАМА, НАЖМИ СЮДА,\nчтобы дать разрешение", Blue) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Perms.openFileAccessSettings(ctx)
                } else {
                    legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            return@Column
        }

        when (vm.stage) {
            Stage.Ready -> {
                BigText("Мама, привет! 👋")
                BigButton("МАМА, НАЖМИ СЮДА,\nчтобы проверить телефон на мусор", Green) { vm.scan() }
                SmallText("Фото, видео и переписки не удалятся. Только ненужный мусор.")
            }

            Stage.Scanning -> Waiting("Проверяю телефон…\nПодожди немного")

            Stage.Found -> {
                if (vm.foundCount == 0) {
                    BigText("Мусора нет 👍\nТелефон чистый!")
                    BigButton("Хорошо", Green) { vm.reset() }
                } else {
                    BigText("Нашла мусор:\n${size(vm.foundBytes)}")
                    BigButton("МАМА, НАЖМИ СЮДА,\nчтобы удалить мусор", Green) { vm.clean() }
                    SmallText("Фото, видео и переписки останутся.")
                }
            }

            Stage.Cleaning -> Waiting("Удаляю мусор…\nПодожди немного")

            Stage.Done -> {
                BigText("Готово! ✅\nОсвободила ${size(vm.freed)}")
                BigButton("Хорошо", Green) { vm.reset() }
            }
        }

        // Кеш мессенджеров чистится только через системный экран — даём прямую кнопку
        if (vm.stage == Stage.Ready || vm.stage == Stage.Done) {
            if (browsers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BigText("Выскакивает реклама? 📢")
                StepsCard(
                    "1. Нажми красную кнопку ниже.",
                    "2. Откроется экран с переключателями.",
                    "3. Нажми на САМЫЙ ВЕРХНИЙ переключатель, чтобы он стал серым.",
                    "4. Нажми «Назад».",
                    "5. Сделай так же со всеми красными кнопками.",
                )
                browsers.forEach { (pkg, label) ->
                    BigButton("МАМА, НАЖМИ СЮДА,\nчтобы убрать рекламу из «$label»", Red) {
                        Perms.openNotificationSettings(ctx, pkg)
                    }
                }
                SmallText("Браузер будет работать как раньше, просто перестанет присылать рекламу.")
            }

            Spacer(Modifier.height(12.dp))
            BigText("Проверка на вирусы:")
            SmallText("Откроется проверка от Google. Там нажми «Сканировать». Если найдёт вирус — нажми «Удалить».")
            BigButton("МАМА, НАЖМИ СЮДА,\nчтобы проверить телефон на вирусы", Purple) {
                Perms.openVirusCheck(ctx)
            }

            Spacer(Modifier.height(12.dp))
            BigText("Ещё можно почистить мессенджеры:")
            SmallText("Нажми кнопку. Откроется экран. Там нажми «Хранилище», потом «Очистить кеш». Потом нажми «Назад».")
            BigButton("МАМА, НАЖМИ СЮДА,\nчтобы почистить WhatsApp", Orange) {
                Perms.openFirstInstalled(ctx, AppCaches.WHATSAPP)
            }
            BigButton("МАМА, НАЖМИ СЮДА,\nчтобы почистить Telegram", Orange) {
                Perms.openFirstInstalled(ctx, AppCaches.TELEGRAM)
            }
            SmallText("«Очистить кеш» ничего важного не удаляет. Только НЕ нажимай «Очистить данные» или «Стереть данные»!")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
private fun BigText(text: String) {
    Text(
        text,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SmallText(text: String) {
    Text(
        text,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
    ) {
        Text(
            text,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun StepsCard(vararg steps: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(StepsBg, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        steps.forEach {
            Text(it, fontSize = 22.sp, lineHeight = 30.sp, color = Ink)
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator(color = Green, strokeWidth = 8.dp, modifier = Modifier.size(80.dp))
    BigText(text)
}
