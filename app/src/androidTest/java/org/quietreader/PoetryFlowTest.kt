package org.quietreader

import android.app.Activity
import android.test.ActivityInstrumentationTestCase2
import android.view.*
import android.widget.*
import org.quietreader.core.*
import org.quietreader.poetry.*

@Suppress("DEPRECATION")
class PoetryFlowTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java){
    private fun all(view:View):List<View> = buildList{add(view);if(view is ViewGroup)for(i in 0 until view.childCount)addAll(all(view.getChildAt(i)))}
    private fun click(a:Activity,label:String){instrumentation.runOnMainSync{all(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()==label}.performClick()};instrumentation.waitForIdleSync()}
    private fun waitFor(check:()->Boolean){val until=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<until){if(check())return;Thread.sleep(50)};assertTrue("condition timed out",check())}
    fun testHomeReaderEditorLearningAndHistory(){
        val main=activity;val app=main.application as ReaderApp;val store=app.store;val before=store.backup();val poems=PoemStore(store)
        app.getSharedPreferences("app-updates",0).edit().putBoolean("auto-check",false).commit()
        var reader:PoemReaderActivity?=null;var editor:PoemEditorActivity?=null;var learning:LearnActivity?=null
        try{
            val document=Protocol.parse(javaClass.classLoader!!.getResourceAsStream("protocol/poetry-example.json")!!.bufferedReader().use{it.readText()},"fixture").document!!
            store.importFile(document);val e=poems.entries().first()
            val readMonitor=instrumentation.addMonitor(PoemReaderActivity::class.java.name,null,false)
            instrumentation.runOnMainSync{main.open(PoemReaderActivity::class.java,e.key)}
            reader=instrumentation.waitForMonitorWithTimeout(readMonitor,10000) as PoemReaderActivity
            instrumentation.waitForIdleSync();assertNotNull(reader!!.window.decorView)
            waitFor{reader!!.hasWindowFocus()};Thread.sleep(400);instrumentation.uiAutomation.takeScreenshot()?.let{bitmap->val file=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"poetry-reader.png");file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}

            // Locate the foreground editor through an Activity monitor registered before a fresh launch.
            val editMonitor=instrumentation.addMonitor(PoemEditorActivity::class.java.name,null,false)
            click(reader!!,"编辑")
            editor=instrumentation.waitForMonitorWithTimeout(editMonitor,10000) as PoemEditorActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync{all(editor!!.window.decorView).filterIsInstance<EditText>().first{it.hint=="标题"}.setText("设备测试修改")}
            assertTrue(poems.state("draft:${e.key}").has("poem"))
            click(editor!!,"保存正文");waitFor{poems.entry(e.key)!!.poem.s("title")=="设备测试修改"}
            assertEquals(e.poem.s("title"),store.section(e.book,e.id)!!.s("title"))
            val qs=Poetry.questions(listOf(Poetry.Candidate(e.key,poems.entry(e.key)!!.poem)),"随机句",2,1,"题目不重复")
            poems.save("session",com.google.gson.JsonObject());LearnActivity.create(poems,qs,"抽背")
            val learnMonitor=instrumentation.addMonitor(LearnActivity::class.java.name,null,false)
            instrumentation.runOnMainSync{reader!!.open(LearnActivity::class.java)}
            learning=instrumentation.waitForMonitorWithTimeout(learnMonitor,10000) as LearnActivity
            instrumentation.waitForIdleSync();click(learning!!,"熟练");click(learning!!,"跳过")
            val completed=poems.state("session");assertEquals("completed",completed.s("status"));assertTrue(HistoryActivity.summary(completed).contains("已评分 1"));assertTrue(HistoryActivity.summary(completed).contains("跳过 1"))
            assertEquals(1,poems.state("history").getAsJsonObject(completed.s("id")).getAsJsonObject("results").entrySet().count{it.value.asInt==3})
        }finally{instrumentation.runOnMainSync{learning?.finish();editor?.finish();reader?.finish()};instrumentation.waitForIdleSync();store.restoreBackup(before)}
    }
}
