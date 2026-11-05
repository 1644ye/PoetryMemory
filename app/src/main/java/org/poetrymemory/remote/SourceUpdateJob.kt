package org.poetrymemory.remote

import android.app.job.*
import android.content.*
import com.google.gson.JsonParser
import org.poetrymemory.PoetryApp
import org.poetrymemory.core.*
import java.util.concurrent.Executors

class SourceUpdateJob:JobService(){
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var stopped=false
    override fun onStartJob(params:JobParameters):Boolean{stopped=false;worker.execute{val p=getSharedPreferences("poetry-source-update",0);val result=runCatching{check(this,p.getBoolean("download",false))}.getOrElse{"检查失败：${it.message}"};p.edit().putString("result",result).apply();if(!stopped)jobFinished(params,false)};return true}
    override fun onStopJob(params:JobParameters):Boolean{stopped=true;return true}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
    companion object{
        fun schedule(context:Context){val p=context.getSharedPreferences("poetry-source-update",0);val jobs=context.getSystemService(JobScheduler::class.java);jobs.cancel(7150);if(p.getBoolean("check",false))jobs.schedule(JobInfo.Builder(7150,ComponentName(context,SourceUpdateJob::class.java)).setRequiredNetworkType(if(p.getBoolean("wifi",true))JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).build())}
        fun check(context:Context,download:Boolean):String{
            val app=context.applicationContext as PoetryApp;val prefs=RemotePreferences(context);require(prefs.validated(false)){"请先连接并保存 GitHub 书源"};val connection=prefs.connection(false);val api=prefs.api(false);val commit=api.revision(connection);val repo=api.get(connection.prefix()).asJsonObject.s("id");val catalog=RemoteLibrary.catalog(JsonParser.parseString(api.file(connection,connection.path(connection.catalog),commit,true)).asJsonObject)
            val sources=app.store.remoteSources();val existing=sources.filter{it.second.s("repositoryId")==repo};val updates=catalog.books.filter{e->e.type=="poetry"&&existing.any{(_,s)->s.s("identity")=="$repo/${catalog.libraryId}/${e.id}"&&s.s("revision")!=api.fileVersion(connection,connection.path(e.path),commit)}}
            if(download&&updates.isNotEmpty())app.downloads.enqueue(connection,repo,catalog.libraryId,commit,updates)
            app.store.reconcileSources(repo,catalog.libraryId,catalog.books.map{"$repo/${catalog.libraryId}/${it.id}"}.toSet(),commit)
            return "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}：${updates.size} 本可更新${if(download)"，已加入下载任务"else""}".also{context.getSharedPreferences("poetry-source-update",0).edit().putString("result",it).apply()}
        }
    }
}
