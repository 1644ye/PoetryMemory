package org.poetrymemory

import android.app.Activity
import android.test.ActivityInstrumentationTestCase2
import android.view.*
import android.widget.*
import org.poetrymemory.core.*
import org.poetrymemory.poetry.*
import com.google.gson.JsonObject

@Suppress("DEPRECATION")
class BookQuizFlowTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java){
    private fun all(v:View):List<View> = buildList{add(v);if(v is ViewGroup)for(i in 0 until v.childCount)addAll(all(v.getChildAt(i)))}
    private fun waitFor(check:()->Boolean){val end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){if(check())return;Thread.sleep(50)};assertTrue("等待界面超时",check())}
    private fun click(a:Activity,label:String){instrumentation.runOnMainSync{all(a.window.decorView).filterIsInstance<TextView>().first{it.text.toString()==label}.performClick()};instrumentation.waitForIdleSync()}
    private fun screenshot(a:Activity,name:String){waitFor{a.hasWindowFocus()};Thread.sleep(350);instrumentation.uiAutomation.takeScreenshot()?.let{bitmap->java.io.File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    fun testBookDetailStartsQuizOnlyFromThatBook(){
        var main=activity;val app=main.application as PoetryApp;val library=app.store;val before=library.backup();val store=PoemStore(library)
        var setup:QuizSetupActivity?=null;var learner:LearnActivity?=null
        try{
            assertEquals("org.poetrymemory",instrumentation.targetContext.packageName)
            assertTrue(instrumentation.targetContext.getDatabasePath("poetry-memory.db").absolutePath.contains("org.poetrymemory"))
            val document=Protocol.parse(javaClass.classLoader!!.getResourceAsStream("protocol/poetry-example.json")!!.bufferedReader().use{it.readText()},"fixture").document!!
            val book=document.getAsJsonArray("books")[0].asJsonObject
            library.importFile(Protocol.wrap(listOf(book.deepCopy().apply{addProperty("id","book-a");addProperty("title","甲本")},book.deepCopy().apply{addProperty("id","book-b");addProperty("title","乙本")})))
            store.save("session",JsonObject())
            val mainMonitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
            instrumentation.runOnMainSync{main.recreate()};main=instrumentation.waitForMonitorWithTimeout(mainMonitor,10000) as MainActivity
            waitFor{var found=false;instrumentation.runOnMainSync{found=all(main.window.decorView).any{it.contentDescription=="甲本"}};found}
            screenshot(main,"poetry-library.png")
            instrumentation.runOnMainSync{all(main.window.decorView).first{it.contentDescription=="甲本"}.performClick()};instrumentation.waitForIdleSync()
            val monitor=instrumentation.addMonitor(QuizSetupActivity::class.java.name,null,false);click(main,"抽背本书")
            setup=instrumentation.waitForMonitorWithTimeout(monitor,10000) as QuizSetupActivity
            waitFor{var ready=false;instrumentation.runOnMainSync{ready=all(setup!!.window.decorView).filterIsInstance<Button>().any{it.text=="开始抽背"&&it.isEnabled}};ready}
            screenshot(setup!!,"poetry-quiz-setup.png")
            val learnMonitor=instrumentation.addMonitor(LearnActivity::class.java.name,null,false);click(setup!!,"开始抽背")
            learner=instrumentation.waitForMonitorWithTimeout(learnMonitor,10000) as LearnActivity
            val questions=store.state("session").getAsJsonArray("questions");assertTrue(questions.size()>0)
            assertTrue(questions.all{it.asJsonObject.s("key").startsWith("book-a/")})
            assertEquals("甲本",store.state("session").s("scopeLabel"))
            screenshot(learner!!,"poetry-learning.png")
        }finally{instrumentation.runOnMainSync{learner?.finish();setup?.finish();main.finish()};instrumentation.waitForIdleSync();library.restoreBackup(before)}
    }
}
