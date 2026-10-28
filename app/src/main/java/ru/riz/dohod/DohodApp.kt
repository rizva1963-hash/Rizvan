package ru.riz.dohod

import android.app.Application

class DohodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Categories.load(this)
        ThemeState.load(this)
        ThemeState.loaded = true
    }
}
