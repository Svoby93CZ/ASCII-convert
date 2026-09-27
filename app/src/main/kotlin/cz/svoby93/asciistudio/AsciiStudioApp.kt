package cz.svoby93.asciistudio

import android.app.Application

class AsciiStudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
