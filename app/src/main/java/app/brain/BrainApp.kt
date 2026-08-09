package app.brain

import android.app.Application
import android.util.Log
import app.brain.data.admin.AdminServer
import app.brain.data.db.BrainDatabase
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.db.DatabaseInitializer
import app.brain.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BrainApp : Application() {

    @Inject
    lateinit var database: BrainDatabase

    @Inject
    lateinit var aiQueueProcessor: AiQueueProcessor

    @Inject
    lateinit var adminServer: AdminServer

    @Inject
    lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                DatabaseInitializer.initialize(applicationContext, database)
            } catch (e: Exception) {
                Log.e("BrainDb", "database initialization failed", e)
            }
            aiQueueProcessor.start()
            // 配置过管理端密码后，随 App 启动自动开启电脑管理端
            if (settings.adminPassword.isNotBlank()) {
                try {
                    adminServer.start(settings.adminPort, settings.adminPassword)
                } catch (e: Exception) {
                    Log.w("AdminServer", "auto start failed", e)
                }
            }
        }
    }
}