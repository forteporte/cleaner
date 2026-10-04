package com.greshok.cleaner

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/** Одна категория найденного мусора. */
data class JunkCategory(
    val id: String,
    val group: String,
    val title: String,
    val hint: String,
    val files: List<File>,
    val bytes: Long,
    val defaultChecked: Boolean,
    /** Удаление может задеть нужное (фото, видео, документы) — подсвечиваем красным. */
    val danger: Boolean = false,
    /** Категория состоит из пустых папок, а не файлов. */
    val isDirs: Boolean = false,
)

object JunkScanner {

    private const val LARGE_FILE = 100L * 1024 * 1024 // 100 МБ

    private const val G_WHATSAPP = "WhatsApp"
    private const val G_TELEGRAM = "Telegram"
    private const val G_JUNK = "Мусор в памяти"

    private class Kind(
        val id: String,
        val title: String,
        val hint: String,
        val defaultChecked: Boolean,
        val danger: Boolean,
        val match: (String) -> Boolean,
    )

    // Порядок важен: первая подошедшая категория забирает папку
    private val waKinds = listOf(
        Kind("wa_statuses", "Просмотренные статусы", "Кеш чужих статусов, можно удалять смело", true, false) { it == ".Statuses" },
        Kind("wa_stickers", "Стикеры", "WhatsApp докачает их, когда понадобятся", true, false) { it.contains("Stickers") },
        Kind("wa_gifs", "GIF-анимации", "Гифки из чатов", true, false) { it.contains("Gif") },
        Kind("wa_voice", "Голосовые сообщения", "Старые голосовые и кружочки пропадут из чатов", false, false) { it.contains("Voice Notes") },
        Kind("wa_images", "Фото", "Все фото из чатов, включая отправленные тобой", false, true) { it.endsWith("Images") },
        Kind("wa_video", "Видео", "Все видео из чатов", false, true) { it.contains("Video") },
        Kind("wa_audio", "Аудио", "Музыка и аудиофайлы из чатов", false, true) { it.endsWith("Audio") },
        Kind("wa_docs", "Документы", "PDF, файлы и т.д. из чатов", false, true) { it.endsWith("Documents") },
        Kind("wa_other", "Прочее", "Обои, аватарки и другие мелочи", false, false) { true },
    )

    private fun waBases(root: File) = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        "WhatsApp/Media",            // старые версии / Android 10 и ниже
        "WhatsApp Business/Media",
    ).map { File(root, it) }

    // Обычные папки в корне памяти — их не трогаем, даже если пустые
    private val protectedTopDirs = listOf(
        "DCIM", "Download", "Pictures", "Music", "Movies", "Documents", "Alarms",
        "Notifications", "Ringtones", "Podcasts", "Audiobooks", "Recordings",
    )

    fun scan(progress: (String) -> Unit): List<JunkCategory> {
        val root = Environment.getExternalStorageDirectory()
        val result = mutableListOf<JunkCategory>()

        progress("Смотрю WhatsApp…")
        result += scanWhatsApp(root)

        progress("Смотрю Telegram…")
        scanTelegramLegacy(root)?.let { result += it }

        progress("Ищу мусор по всей памяти…")
        result += scanGeneric(root)

        return result.filter { it.files.isNotEmpty() }
    }

    private fun scanWhatsApp(root: File): List<JunkCategory> {
        val buckets = waKinds.associate { it.id to mutableListOf<File>() }
        for (base in waBases(root)) {
            val children = base.listFiles() ?: continue
            for (child in children) {
                val kind = waKinds.first { it.match(child.name) }
                child.walkTopDown()
                    .filter { it.isFile && it.name != ".nomedia" }
                    .forEach { buckets.getValue(kind.id).add(it) }
            }
        }
        return waKinds.map { k ->
            val files = buckets.getValue(k.id)
            JunkCategory(k.id, G_WHATSAPP, k.title, k.hint, files, files.sumOf { it.length() }, k.defaultChecked, k.danger)
        }
    }

    /** Старые версии Telegram хранили скачанное в /Telegram прямо в памяти. */
    private fun scanTelegramLegacy(root: File): JunkCategory? {
        val dir = File(root, "Telegram")
        if (!dir.isDirectory) return null
        val files = dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.toList()
        return JunkCategory(
            "tg_legacy", G_TELEGRAM, "Файлы из чатов (папка /Telegram)",
            "Фото, видео и файлы, которые Telegram скачал. Их можно снова открыть из чата",
            files, files.sumOf { it.length() }, defaultChecked = false,
        )
    }

    private fun scanGeneric(root: File): List<JunkCategory> {
        val skip = (listOf(File(root, "Android"), File(root, "Telegram")) +
                waBases(root).map { it.parentFile!! }).map { it.absolutePath }.toSet()
        val keep = protectedTopDirs.map { File(root, it).absolutePath }.toSet()

        val logs = mutableListOf<File>()
        val apks = mutableListOf<File>()
        val thumbs = mutableListOf<File>()
        val large = mutableListOf<File>()
        val emptyDirs = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { it.absolutePath !in skip }
            .forEach { f ->
                if (f.isDirectory) {
                    if (f != root && f.absolutePath !in keep && f.absolutePath !in skip) {
                        val list = f.list()
                        if (list != null && list.isEmpty()) emptyDirs += f
                    }
                    return@forEach
                }
                val name = f.name.lowercase()
                when {
                    f.path.contains("/.thumbnails/") -> thumbs += f
                    name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk") -> apks += f
                    name.endsWith(".log") || name.endsWith(".tmp") || name.endsWith(".temp") ||
                            name == "thumbs.db" || name == ".ds_store" -> logs += f
                    f.length() >= LARGE_FILE -> large += f
                }
            }

        return listOf(
            JunkCategory("thumbs", G_JUNK, "Миниатюры галереи", "Галерея создаст их заново", thumbs, thumbs.sumOf { it.length() }, true),
            JunkCategory("apks", G_JUNK, "Установочные APK", "Файлы установки приложений, после установки не нужны", apks, apks.sumOf { it.length() }, true),
            JunkCategory("logs", G_JUNK, "Логи и временные файлы", ".log, .tmp и подобное", logs, logs.sumOf { it.length() }, true),
            JunkCategory("empty", G_JUNK, "Пустые папки", "Папки, в которых ничего нет", emptyDirs, 0L, true, isDirs = true),
            JunkCategory("large", G_JUNK, "Большие файлы (от 100 МБ)", "Проверь список, тут могут быть нужные фильмы и бэкапы", large, large.sumOf { it.length() }, false, danger = true),
        )
    }

    /** Удаляет выбранное и возвращает, сколько байт освободили. */
    fun delete(context: Context, categories: List<JunkCategory>): Long {
        var freed = 0L
        val deleted = mutableListOf<String>()
        for (c in categories) {
            for (f in c.files) {
                val size = if (f.isFile) f.length() else 0L
                if (f.delete()) {
                    freed += size
                    deleted += f.absolutePath
                }
            }
        }
        // Говорим галерее, что файлов больше нет, чтобы не висели «призраки»
        if (deleted.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, deleted.toTypedArray(), null, null)
        }
        return freed
    }
}
