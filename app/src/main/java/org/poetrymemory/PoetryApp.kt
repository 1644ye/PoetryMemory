package org.poetrymemory

import android.app.Application
import org.poetrymemory.data.LibraryStore

class PoetryApp : Application() {
    val store: LibraryStore by lazy { LibraryStore(this) }
    val progressSync by lazy { org.poetrymemory.remote.ProgressSync(this,store) }
    val downloads by lazy { org.poetrymemory.remote.DownloadQueue(this,store) }
    val updates by lazy { org.poetrymemory.update.AppUpdater(this) }
    @Volatile var readingActive=false
    override fun onCreate(){super.onCreate();downloads.recover();org.poetrymemory.remote.SourceUpdateJob.schedule(this);val scheduler=getSystemService(android.app.job.JobScheduler::class.java);scheduler.schedule(android.app.job.JobInfo.Builder(7142,android.content.ComponentName(this,org.poetrymemory.remote.ProgressJob::class.java)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).build())}
    fun scheduleSync(){if(!getSharedPreferences("progress-device",MODE_PRIVATE).getBoolean("enabled",false))return;getSystemService(android.app.job.JobScheduler::class.java).schedule(android.app.job.JobInfo.Builder(7143,android.content.ComponentName(this,org.poetrymemory.remote.ProgressJob::class.java)).setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1500).build())}
}
