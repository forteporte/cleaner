package com.greshok.cleaner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что сейчас на экране. */
enum class Stage { Ready, Scanning, Found, Cleaning, Done }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var stage by mutableStateOf(Stage.Ready)
        private set

    /** Только безопасный мусор: фото, видео, документы и большие файлы сюда не попадают. */
    var found by mutableStateOf<List<JunkCategory>>(emptyList())
        private set

    var freed by mutableStateOf(0L)
        private set

    val foundBytes: Long get() = found.sumOf { it.bytes }
    val foundCount: Int get() = found.sumOf { it.files.size }

    fun scan() {
        if (stage == Stage.Scanning || stage == Stage.Cleaning) return
        stage = Stage.Scanning
        viewModelScope.launch {
            found = withContext(Dispatchers.IO) {
                JunkScanner.scan { }.filter { it.defaultChecked && !it.danger }
            }
            stage = Stage.Found
        }
    }

    fun clean() {
        if (stage != Stage.Found) return
        val toClean = found
        stage = Stage.Cleaning
        viewModelScope.launch {
            freed = withContext(Dispatchers.IO) { JunkScanner.delete(getApplication(), toClean) }
            found = emptyList()
            stage = Stage.Done
        }
    }

    fun reset() {
        stage = Stage.Ready
    }
}
