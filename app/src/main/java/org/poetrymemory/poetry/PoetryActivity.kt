package org.poetrymemory.poetry

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.*
import org.poetrymemory.*
import java.util.concurrent.Executors

open class PoetryActivity: Activity() {
    val app get()=application as PoetryApp
    val store get()=app.store
    val poems by lazy{PoemStore(store)}
    lateinit var ui: Ui
    lateinit var panel: LinearLayout
    lateinit var root: LinearLayout
    lateinit var headerActions:LinearLayout
    private val worker=Executors.newSingleThreadExecutor()
    var generation=0
    private var backAction:(()->Unit)?=null
    override fun onCreate(state:Bundle?){super.onCreate(state);ui=Ui(this,store.settings());if(android.os.Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){navigateBack()}}
    protected open fun navigateBack(){backAction?.invoke()?:finish()}
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("platform") override fun onBackPressed(){navigateBack()}
    fun page(title:String, back:(()->Unit)?=null,scrollContent:Boolean=true) {
        backAction=back
        generation++
        ui=Ui(this,store.settings());root=ui.panel(12);root.setBackgroundColor(ui.background)
        root.setOnApplyWindowInsetsListener{v,i->v.setPadding(i.systemWindowInsetLeft+ui.dp(12),i.systemWindowInsetTop,i.systemWindowInsetRight+ui.dp(12),i.systemWindowInsetBottom);i}
        root.addView(ui.row().apply{if(back!=null)addView(ui.button("‹"){back()}.apply{contentDescription="返回"});addView(ui.text(title,24f,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f));headerActions=ui.row();addView(headerActions)})
        panel=ui.panel(0);root.addView(if(scrollContent)ui.scroll(panel)else panel,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        window.statusBarColor=ui.background;window.navigationBarColor=ui.background
    }
    fun menu(items:List<Pair<String,()->Unit>>,label:String="更多"){
        val button=ui.button(if(label=="更多")"⋯"else label){}
        button.contentDescription=label
        button.setOnClickListener{android.widget.PopupMenu(this,button).apply{items.forEachIndexed{i,(title,_)->menu.add(0,i,i,title)};setOnMenuItemClickListener{item->items[item.itemId].second();true};show()}}
        headerActions.addView(button)
    }
    fun navigation(selected:String,items:List<Pair<String,()->Unit>>){root.addView(ui.row().apply{setPadding(0,ui.dp(8),0,0);items.forEach{(label,action)->addView(ui.text(label,14f,label==selected).apply{gravity=android.view.Gravity.CENTER;minimumHeight=ui.dp(52);setTextColor(if(label==selected)ui.accent else ui.muted);isSelected=label==selected;contentDescription="导航：$label";setOnClickListener{action()}},LinearLayout.LayoutParams(0,-2,1f))}})}
    fun <T> work(task:()->T, done:(T)->Unit) {val version=generation;worker.execute{val result=runCatching(task);runOnUiThread{if(!isDestroyed&&version==generation)result.fold(done){message(it.message?:"操作失败")}}}}
    fun message(text:String){ui.dialog().setMessage(text).setPositiveButton("确定",null).show()}
    fun choose(title:String,values:List<String>,selected:(Int)->Unit){ui.dialog().setTitle(title).setItems(values.toTypedArray()){_,i->selected(i)}.show()}
    fun edit(title:String,value:String="",done:(String)->Unit){val field=ui.field(title,value,true);ui.dialog().setTitle(title).setView(field).setNegativeButton("取消",null).setPositiveButton("保存"){_,_->runCatching{done(field.text.toString())}.onFailure{message(it.message?:"保存失败")}}.show()}
    fun open(cls:Class<*>,key:String?=null){startActivity(Intent(this,cls).apply{key?.let{putExtra("key",it)}})}
    override fun onDestroy(){worker.shutdownNow();super.onDestroy()}
}
