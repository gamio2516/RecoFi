package jp.knaka.cardmemo.storage

import android.content.Context

object StorageProvider {
    @Volatile private var cached: StorageBootstrapResult? = null
    fun get(context: Context): StorageBootstrapResult = cached ?: synchronized(this) {
        cached ?: StorageBootstrap(context.applicationContext).open().also { cached = it }
    }
    internal fun resetForTest() { (cached as? StorageBootstrapResult.RoomReady)?.database?.close(); cached = null }
}
