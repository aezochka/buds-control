package dev.aezochka.budscontrol

import android.app.Application
import dev.aezochka.budscontrol.device.BudsRepository

class BudsApplication : Application() {
    lateinit var repository: BudsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BudsRepository(this)
    }
}
