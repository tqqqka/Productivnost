package com.tihon.shtorka

import android.app.Application
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.ui.Fonts

class ShtorkaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Fonts.init(this)
    }
}
