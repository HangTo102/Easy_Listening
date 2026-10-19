package com.easylistening.app

import android.app.Application
import com.easylistening.app.ui.PlaybackHolder

class EsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackHolder.init(this)
    }
}
