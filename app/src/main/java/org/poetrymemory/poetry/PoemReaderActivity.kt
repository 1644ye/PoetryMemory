package org.poetrymemory.poetry

import android.os.*
import android.view.*
import android.widget.*
import android.graphics.Typeface
import com.google.gson.*
import org.poetrymemory.core.*
import org.poetrymemory.*

class PoemReaderActivity: PoetryActivity() {
    private lateinit var entry:PoemStore.Entry
    private var index=0
    private var toolbar=true
    private val handler=Handler(Looper.getMainLooper())
    private var targets=mutableMapOf<Int,View>()
    private var scroll:ScrollView?=null
    private var lazyList:ListView?=null
    private var rowIndices=mutableListOf<List<Int>>()
    private var needsRelocation=false
    private val key get()=intent.getStringExtra("key")!!
    private fun config():JsonObject {
        fun legacy(settings:ReadingSettings)=JsonObject().apply{
            addProperty("size",settings.fontSize.toInt());addProperty("margin",settings.margin.toInt());addProperty("spacing",((settings.lineSpacing-1)*settings.fontSize).toInt());addProperty("font",settings.fontFamily)
            listOf("pinyin","translation","note","background","poemTranslation","appreciation").forEach{k->addProperty(k,when(settings.auxiliaryMode(k)){"small"->"小字";"inline"->"正文";"link"->"链接";else->"隐藏"})}
        }
        val result=legacy(store.settings())
        poems.state("reader:global").entrySet().forEach{(k,v)->result.add(k,v)}
        if(::entry.isInitialized&&store.hasBookSettings(entry.book))legacy(store.settings(entry.book)).entrySet().forEach{(k,v)->result.add(k,v)}
        poems.state("reader:$key").entrySet().forEach{(k,v)->result.add(k,v)}
        return result
    }
    private fun setting(k:String,v:JsonElement?){val local=poems.state("reader:$key");if(v==null)local.remove(k)else local.add(k,v);poems.save("reader:$key",local)}
    override fun onCreate(state:Bundle?){super.onCreate(state);val e=poems.entry(key);if(e==null){finish();return};entry=e;val pos=poems.personal(entry);val id=pos.s("line");val found=entry.lines.indexOfFirst{it.s("id")==id};needsRelocation=id.isNotBlank()&&found<0;index=found.takeIf{it>=0}?:0;render();if(needsRelocation){val fallback=(pos.s("index").toIntOrNull()?:0).coerceIn(0,entry.lines.lastIndex.coerceAtLeast(0));ui.dialog().setTitle("原句位置已变化").setMessage("未找到原句 ID。是否从第 ${fallback+1} 句重新定位？原记录在确认前保留。").setPositiveButton("确认定位"){_,_->index=fallback;needsRelocation=false;render()}.setNegativeButton("暂不阅读"){_,_->finish()}.setOnCancelListener{finish()}.show()}}
    override fun onResume(){super.onResume();poems.entry(key)?.let{entry=it;if(::entry.isInitialized)render()}}
    private fun render(){
        index=index.coerceIn(0,(entry.lines.size-1).coerceAtLeast(0));targets.clear();val c=config();page(entry.poem.s("title"),{finish()},scrollContent=false)
        val descriptors=listOf("设置" to {settings()},"收藏" to {store.togglePoemFavorite(entry.book,entry.id);render()},"编辑" to {open(PoemEditorActivity::class.java,key)},"背诵" to {startLearning()},"篇级资料" to {article()},"个人标签" to {edit("个人标签，逗号分隔",poems.personal(entry).s("tags")){val p=poems.personal(entry);p.addProperty("tags",it);poems.save("personal:$key",p)}},"抽背权重" to {edit("0~100，0 表示排除",poems.personal(entry).s("weight").ifBlank{"1"}){require(it.toIntOrNull() in 0..100);poems.save("personal:$key",poems.personal(entry).apply{addProperty("weight",it)})}},"来源版本" to {sourceConflict()})
        val hidden=c.s("hiddenButtons").split('|');val order=c.s("toolOrder").split('|')
        val visible=descriptors.filter{it.first !in hidden}.sortedBy{order.indexOf(it.first).takeIf{n->n>=0}?:100}
        val tools=ui.equalActions(*visible.filter{it.first in listOf("设置","背诵")}.toTypedArray())
        menu(visible.filter{it.first !in listOf("设置","背诵")}+listOf("保存检查点" to {checkpoint();Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show()}))
        for(i in 0 until tools.childCount)(tools.getChildAt(i) as? TextView)?.textSize=c.s("buttonSize").toFloatOrNull()?:14f
        if(c.s("toolbarPolicy")=="自动隐藏")handler.postDelayed({tools.visibility=View.GONE},5000)
        tools.visibility=if(toolbar&&c.s("hideTools")!="true")View.VISIBLE else View.GONE;panel.addView(tools)
        panel.addView(ui.text("${entry.poem.s("dynasty")} · ${entry.poem.s("author")} · ${index+1}/${entry.lines.size}",14f))
        // This independent touch point survives all configurable toolbar and gesture settings.
        val safety=ui.text("◉ 按住 5 秒恢复交互",12f);safety.contentDescription="安全恢复；双击可直接打开恢复面板"
        val restore=Runnable{recover()};val ticks=object:Runnable{var elapsed=0;override fun run(){elapsed++;safety.text="◉ 恢复 ${elapsed}/5 秒";if(elapsed<5)handler.postDelayed(this,1000)}}
        safety.setOnTouchListener{_,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{ticks.elapsed=0;handler.postDelayed(ticks,1000);handler.postDelayed(restore,5000)};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{handler.removeCallbacks(ticks);handler.removeCallbacks(restore);safety.text="◉ 按住 5 秒恢复交互"}};true};safety.setOnClickListener{recover()};panel.addView(safety)
        val gestures=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(e:MotionEvent)=true
            override fun onSingleTapConfirmed(e:MotionEvent):Boolean{action(c.s("tap").ifBlank{"设置"});return true}
            override fun onDoubleTap(e:MotionEvent):Boolean{action(c.s("double").ifBlank{"工具栏"});return true}
            override fun onLongPress(e:MotionEvent){action(c.s("long").ifBlank{"提示"})}
            override fun onFling(a:MotionEvent?,b:MotionEvent,vx:Float,vy:Float):Boolean{if(a!=null&&kotlin.math.abs(b.x-a.x)>ui.dp(60)&&kotlin.math.abs(b.x-a.x)>kotlin.math.abs(b.y-a.y)*1.5){move(if(b.x<a.x)1 else -1);return true};return false}
        })
        val gestureArea=ui.text("轻点设置 · 双击工具栏 · 横滑切句 · 长按提示",12f).apply{minimumHeight=ui.dp(32);setOnTouchListener{_,e->gestures.onTouchEvent(e)}};panel.addView(gestureArea)
        val size=c.s("size").toFloatOrNull()?:26f
        val margin=c.s("margin").toIntOrNull()?:12
        fun sentence(line:JsonObject,i:Int):LinearLayout=ui.panel(2).apply{
            targets[i]=this
            addView(ui.text(line.s("original"),size).apply{typeface=Typeface.create(c.s("font").ifBlank{"serif"},Typeface.NORMAL);setTextIsSelectable(true);setLineSpacing(ui.dp(c.s("spacing").toIntOrNull()?:8).toFloat(),1f)})
            listOf("pinyin" to "拼音","translation" to "译文","note" to "注释").forEach{(k,label)->val mode=c.s(k);if(mode.isNotBlank()&&mode!="隐藏"){val text=line.s(k).ifBlank{"本句未提供$label"};if(mode=="链接")addView(ui.button(label){message(text)})else addView(ui.text(text,if(mode=="正文")size else 14f))}}
        }
        val factories=mutableListOf<()->View>();rowIndices.clear()
        fun addRow(indices:List<Int>,factory:()->View){rowIndices.add(indices);factories.add(factory)}
        if(c.s("mode")=="逐句")entry.lines.getOrNull(index)?.let{line->val at=index;addRow(listOf(at)){sentence(line,at)}}else{
            var offset=0
            entry.poem.getAsJsonArray("stanzas")?.forEachIndexed { sectionIndex,value ->
                addRow(emptyList()){ui.text("— 第 ${sectionIndex+1} 节 —",12f)}
                val lines=value.asJsonObject.getAsJsonArray("lines").map{it.asJsonObject};val start=offset
                val layout=c.s("layout").ifBlank{"自动"}
                val auxiliary=listOf("pinyin","translation","note").any{c.s(it) in listOf("小字","正文","链接")}
                if(layout=="段落"&&!auxiliary){
                    // Paragraphs keep a logical sentence anchor; long paragraphs are built on demand.
                    addRow(lines.indices.map{start+it}){ui.text(lines.joinToString(""){it.s("original")},size).apply{setTextIsSelectable(true)}}
                }else if(layout in listOf("自动","两句一行")){
                    lines.chunked(2).forEachIndexed{pairIndex,pair->
                        val indices=pair.indices.map{start+pairIndex*2+it}
                        addRow(indices){
                            val width=resources.displayMetrics.widthPixels-ui.dp(48+2*margin)
                            val paint=android.text.TextPaint().apply{textSize=size*resources.displayMetrics.scaledDensity;typeface=Typeface.create(c.s("font").ifBlank{"serif"},Typeface.NORMAL)}
                            val fit=pair.all{paint.measureText(it.s("original"))+ui.dp(16)<=width/pair.size}&&!auxiliary
                            if(fit)ui.row().apply{pair.forEachIndexed{j,line->addView(sentence(line,indices[j]),LinearLayout.LayoutParams(0,-2,1f))}}
                            else ui.panel(0).apply{pair.forEachIndexed{j,line->addView(sentence(line,indices[j]))}}
                        }
                    }
                }else lines.forEachIndexed{j,line->addRow(listOf(start+j)){sentence(line,start+j)}}
                offset+=lines.size
            }
        }
        listOf("background" to "创作背景","poemTranslation" to "全篇译文","appreciation" to "赏析").forEach{(k,label)->val mode=c.s(k);val value=entry.poem.s(if(k=="poemTranslation")"translation"else k).ifBlank{"本篇未提供$label"};if(mode=="链接")addRow(emptyList()){ui.button(label){message(value)}}else if(mode in listOf("小字","正文"))addRow(emptyList()){ui.text("$label\n$value",if(mode=="正文")size else 14f)}}
        lazyList=ListView(this).apply{
            divider=null;setOnTouchListener{_,event->gestures.onTouchEvent(event);false};setPadding(ui.dp(margin),0,ui.dp(margin),0)
            adapter=object:BaseAdapter(){override fun getCount()=factories.size;override fun getItem(p:Int)=p;override fun getItemId(p:Int)=p.toLong();override fun getView(p:Int,recycled:View?,parent:ViewGroup):View=factories[p]()}
            setOnScrollListener(object:AbsListView.OnScrollListener{
                override fun onScrollStateChanged(v:AbsListView,state:Int){if(state==AbsListView.OnScrollListener.SCROLL_STATE_IDLE){rowIndices.drop(firstVisiblePosition).firstOrNull{it.isNotEmpty()}?.firstOrNull()?.let{index=it};checkpoint()}}
                override fun onScroll(v:AbsListView,a:Int,b:Int,total:Int){}
            })
        }
        panel.addView(lazyList,LinearLayout.LayoutParams(-1,0,1f))
        panel.addView(ui.equalActions("上一句" to {move(-1)},"下一句" to {move(1)}))
        lazyList!!.post{locate()};checkpoint()
    }
    private fun locate(){if(lazyList!=null){val row=rowIndices.indexOfFirst{index in it};if(row>=0)lazyList?.setSelectionFromTop(row,0);return};val v=targets[index]?:return;val loc=IntArray(2);val sl=IntArray(2);v.getLocationInWindow(loc);scroll?.getLocationInWindow(sl);scroll?.scrollBy(0,loc[1]-sl[1])}
    private fun move(delta:Int){index=(index+delta).coerceIn(0,(entry.lines.size-1).coerceAtLeast(0));if(config().s("mode")=="逐句")render()else locate();checkpoint()}
    private fun checkpoint(){if(!::entry.isInitialized||needsRelocation)return;val p=poems.personal(entry);p.addProperty("line",entry.lines.getOrNull(index)?.s("id"));p.addProperty("index",index);p.addProperty("read",System.currentTimeMillis());if(index==entry.lines.lastIndex)p.addProperty("status","已读");poems.save("personal:$key",p);entry.lines.getOrNull(index)?.let{if(poems.state("overlay:$key").get("poem")==null)store.savePosition(Anchor(entry.book,entry.id,it.s("id")),(index+1f)/entry.lines.size)}}
    private fun action(name:String){when(name){"设置"->settings();"工具栏"->{toolbar=!toolbar;render()};"下一句"->move(1);"上一句"->move(-1);"提示"->hints();"背诵"->startLearning()}}
    private fun hints(){val names=listOf("拼音","译文","注释","下一句","首字","前两字","全部辅助");choose("提示",names){message(Poetry.hint(entry.poem,index,names[it]))}}
    private fun article(){message(listOf("创作背景" to "background","全篇译文" to "translation","赏析" to "appreciation").joinToString("\n\n"){(label,k)->"$label\n${entry.poem.s(k).ifBlank{"本篇未提供"}}"})}
    private fun sourceConflict(){val overlay=poems.state("overlay:$key");if(overlay.get("poem")==null){message("当前使用来源正文");return};ui.dialog().setTitle(if(overlay.s("sourceRevision")!=Poetry.revision(entry.source))"来源已更新，本地覆盖已保留"else"当前使用本地编辑内容").setMessage("采用来源正文会移除本地覆盖；学习历史保留原快照。").setNegativeButton("保留本地",null).setNeutralButton("查看来源"){_,_->message(Poetry.lines(entry.source).joinToString("\n"){it.s("original")})}.setPositiveButton("采用来源"){_,_->poems.save("overlay:$key",JsonObject());entry=poems.entry(key)!!;render()}.show()}
    private fun startLearning(){if(poems.state("session").s("status")=="active"){open(LearnActivity::class.java);return};val modes=listOf("整篇","逐句","接续");choose("背诵模式",modes){i->val q=Poetry.Question(Poetry.id(),key,entry.poem.deepCopy(),entry.lines[index].s("id"),index,modes[i],1);LearnActivity.create(poems,listOf(q),"背诵");open(LearnActivity::class.java)}}
    private fun recover(){ui.dialog().setTitle("恢复阅读交互").setMessage("恢复默认手势并显示工具栏，保留正文及学习记录。").setPositiveButton("恢复"){_,_->val c=poems.state("reader:$key");c.addProperty("tap","设置");c.addProperty("double","工具栏");c.addProperty("long","提示");c.addProperty("hideTools",false);c.addProperty("hiddenButtons","");c.addProperty("toolbarPolicy","常显");poems.save("reader:$key",c);toolbar=true;render()}.setNegativeButton("取消",null).show()}
    private fun settings(){page("阅读设置",{render()});panel.addView(ui.button("辅助"){options("辅助",listOf("pinyin" to "拼音","translation" to "译文","note" to "注释","background" to "创作背景","poemTranslation" to "全篇译文","appreciation" to "赏析"),listOf("隐藏","小字","正文","链接"))});panel.addView(ui.button("篇级资料"){article()});panel.addView(ui.button("正文"){bodySettings()});panel.addView(ui.button("交互 → 手势"){options("手势区域：上方交互区",listOf("tap" to "轻点","double" to "双击","long" to "长按"),listOf("设置","工具栏","上一句","下一句","提示","背诵","无"))});panel.addView(ui.button("交互 → 按钮和工具栏"){toolbarSettings()});panel.addView(ui.button("恢复单篇继承"){poems.save("reader:$key",JsonObject());render()});panel.addView(ui.button("将当前设置保存为全局"){poems.save("reader:global",config());message("已保存全局设置；单篇覆盖仍优先")})}
    private fun toolbarSettings(){page("按钮和工具栏",{settings()});panel.addView(ui.button("显隐"){edit("隐藏的按钮，以 | 分隔（设置、收藏、编辑、背诵、篇级资料、个人标签、抽背权重、来源版本）",config().s("hiddenButtons")){setting("hiddenButtons",JsonPrimitive(it));toolbarSettings()}});panel.addView(ui.button("按钮顺序"){edit("按钮名称，以 | 分隔",config().s("toolOrder")){setting("toolOrder",JsonPrimitive(it));toolbarSettings()}});panel.addView(ui.button("按钮字号"){edit("12~24",config().s("buttonSize").ifBlank{"14"}){require(it.toIntOrNull() in 12..24);setting("buttonSize",JsonPrimitive(it))}});panel.addView(ui.button("显示策略"){choose("工具栏显示策略",listOf("常显","自动隐藏","隐藏")){setting("hideTools",JsonPrimitive(it==2));setting("toolbarPolicy",JsonPrimitive(listOf("常显","自动隐藏","隐藏")[it]))}});panel.addView(ui.button("恢复默认工具栏"){listOf("hiddenButtons","toolOrder","buttonSize","toolbarPolicy","hideTools").forEach{setting(it,JsonPrimitive(""))};toolbar=true;render()})}
    private fun options(title:String,fields:List<Pair<String,String>>,values:List<String>){page(title,{settings()});fields.forEach{(k,label)->val local=poems.state("reader:$key");panel.addView(ui.button("$label：${config().s(k).ifBlank{"默认"}} · ${if(local.has(k))"单篇"else"全局继承"}"){choose(label,values){setting(k,JsonPrimitive(values[it]));options(title,fields,values)}})}}
    private fun bodySettings(){page("正文",{settings()});panel.addView(ui.button("阅读模式"){choose("阅读模式",listOf("连续","逐句")){setting("mode",JsonPrimitive(listOf("连续","逐句")[it]))}});panel.addView(ui.button("排版"){choose("排版",listOf("自动","一句一行","两句一行","段落")){setting("layout",JsonPrimitive(listOf("自动","一句一行","两句一行","段落")[it]))}});panel.addView(ui.button("字体"){choose("字体",listOf("serif","sans-serif","monospace")){setting("font",JsonPrimitive(listOf("serif","sans-serif","monospace")[it]))}});listOf("size" to "字号（14~48）","spacing" to "行间距（0~40）","margin" to "边距（0~48）").forEach{(k,label)->panel.addView(ui.button("$label：${config().s(k)}"){edit(label,config().s(k)){require(it.toIntOrNull() in if(k=="size")14..48 else 0..48);setting(k,JsonPrimitive(it));bodySettings()}})}}
    override fun onPause(){checkpoint();super.onPause()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
