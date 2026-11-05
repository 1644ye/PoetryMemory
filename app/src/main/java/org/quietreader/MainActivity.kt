package org.quietreader

import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.google.gson.*
import org.quietreader.core.*
import org.quietreader.poetry.*
import android.icu.text.Transliterator

class MainActivity: PoetryActivity() {
    private var scope="全部诗文"
    private var search=""
    private var pageIndex=0
    private var groups=mutableListOf<Poetry.FilterGroup>()
    private var groupAny=false
    private val prefs get()=getSharedPreferences("poetry-home",0)
    private var groupFilter:List<String>?=null
    private var lastQuery=""
    override fun onCreate(state:Bundle?){super.onCreate(state);restoreHome();state?.let{scope=it.getString("scope","全部诗文");search=it.getString("search","")};home();app.updates.autoCheck{Toast.makeText(this,"发现新版本，请到更新页查看",Toast.LENGTH_LONG).show()}}
    override fun onResume(){super.onResume();restoreHome();pageIndex=0;home()}
    override fun onPause(){poems.save("home",JsonObject().apply{add("preferences",Gson().toJsonTree(prefs.all));add("filters",Gson().toJsonTree(groups));addProperty("any",groupAny)});super.onPause()}
    override fun onSaveInstanceState(out:Bundle){out.putString("scope",scope);out.putString("search",search);super.onSaveInstanceState(out)}
    private fun restoreHome(){val data=poems.state("home");data.getAsJsonArray("filters")?.let{groups=Gson().fromJson(it,Array<Poetry.FilterGroup>::class.java).toMutableList()};groupAny=data.get("any")?.asBoolean?:false;data.getAsJsonObject("preferences")?.let{values->prefs.edit().apply{values.entrySet().forEach{(key,value)->when{value.asJsonPrimitive.isBoolean->putBoolean(key,value.asBoolean);value.asJsonPrimitive.isNumber->putInt(key,value.asInt);else->putString(key,value.asString)}}}.apply()}}
    private fun manageHome(){work({poems.entries()}){entries->val checked=entries.map{poems.personal(it).s("hidden")!="true"}.toBooleanArray();ui.dialog().setTitle("在首页显示").setMultiChoiceItems(entries.map{it.poem.s("title")}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("保存"){_,_->entries.forEachIndexed{i,e->poems.save("personal:${e.key}",poems.personal(e).apply{addProperty("hidden",!checked[i])})};reset()}.setNegativeButton("取消",null).show()}}
    private fun manageTags(){work({poems.entries()}){entries->val tags=entries.flatMap{poems.personal(it).s("tags").split('，',',').filter{it.isNotBlank()}}.distinct();if(tags.isEmpty()){message("尚无个人标签；可在诗文阅读页添加。");return@work};choose("个人标签管理",tags){i->edit("改名或合并到已有标签；留空删除关系",tags[i]){replacement->entries.forEach{e->val p=poems.personal(e);val old=p.s("tags").split('，',',').filter{it.isNotBlank()};val updated=old.flatMap{if(it==tags[i])listOfNotNull(replacement.takeIf{it.isNotBlank()})else listOf(it)}.distinct();p.addProperty("tags",updated.joinToString(","));poems.save("personal:${e.key}",p)};groupList("tags")}}}}
    private fun reset(){pageIndex=0;home()}
    private fun home(){
        val signature="$scope|$search|$groupFilter|$groups|$groupAny|${prefs.getString("sort","title")}|${prefs.getBoolean("desc",false)}"
        if(lastQuery!=signature){pageIndex=0;lastQuery=signature}
        page("背诗 · PoetryMemory");panel.addView(ui.text("日积一首，久而成诵",14f))
        panel.addView(ui.actions("搜索" to {edit("标题、作者或原文",search){search=it;reset()}},"首页设置" to {settings()},"新建诗文" to {newPoem()}))
        panel.addView(ui.actions("全部诗文" to {scope="全部诗文";groupFilter=null;reset()},"收藏" to {scope="收藏";groupFilter=null;reset()},"诗集" to {collectionList()},"作者" to {groupList("author")},"标签" to {groupList("tags")}))
        panel.addView(ui.actions("继续学习" to {open(LearnActivity::class.java)},"抽背当前结果" to {load{startQuiz(it)}},"学习历史" to {open(HistoryActivity::class.java)}))
        panel.addView(ui.button("继续阅读"){work({poems.entries().maxByOrNull{poems.personal(it).s("read").toLongOrNull()?:0L}}){it?.let{open(PoemReaderActivity::class.java,it.key)}?:message("还没有诗文，请先导入")}})
        panel.addView(ui.text("$scope  ${search.takeIf{it.isNotBlank()}?.let{"· $it"}.orEmpty()} · ${groups.size} 组筛选",14f))
        val resultPanel=ui.panel(0);panel.addView(resultPanel)
        load { entries ->
            resultPanel.addView(ui.text("共 ${entries.size} 首",14f))
            if(entries.isEmpty())resultPanel.addView(ui.text("暂无诗文。可从数据页导入 JSON 或连接 GitHub 书源。"))
            val cols=if(prefs.getBoolean("grid",false))prefs.getInt("cols",2)else 1
            entries.drop(pageIndex*40).take(40).chunked(cols).forEach { chunk -> resultPanel.addView(ui.row().apply{chunk.forEach {e->addView(ui.card().apply{
                addView(ui.text((if(poems.favorite(e))"★ " else "")+e.poem.s("title"),20f,true));addView(ui.text("${e.poem.s("dynasty")} · ${e.poem.s("author")} · ${Poetry.wordCount(e.text)} 字",13f))
                if(!prefs.getBoolean("dense",false))addView(ui.text(e.lines.take(2).joinToString(""){it.s("original")},16f))
                setOnClickListener{open(PoemReaderActivity::class.java,e.key)};contentDescription="阅读 ${e.poem.s("title")}"
            },LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(3,4,3,4)})}})}
            resultPanel.addView(ui.actions("上一页" to {pageIndex=(pageIndex-1).coerceAtLeast(0);home()},"下一页" to {if((pageIndex+1)*40<entries.size){pageIndex++;home()}}))
        }
    }
    private fun load(done:(List<PoemStore.Entry>)->Unit){work({
        val activeGroups=groups.filter{it.conditions.isNotEmpty()};val filtered=poems.entries().filter { e ->
            (scope!="全部诗文"||poems.personal(e).s("hidden")!="true")&&(scope!="收藏"||poems.favorite(e))&&(groupFilter==null||e.key in groupFilter!!)&&
            (search.isBlank()||listOf(e.poem.s("title"),e.poem.s("author"),e.text).any{it.contains(search,true)})&&
            (activeGroups.isEmpty()||if(groupAny)activeGroups.any{it.matches(poems.fields(e))}else activeGroups.all{it.matches(poems.fields(e))})
        }
        val transliterator=if(android.os.Build.VERSION.SDK_INT>=29)Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")else null
        val sort=prefs.getString("sort","title")!!
        fun key(e:PoemStore.Entry):String=when(sort){"pinyin"->if(android.os.Build.VERSION.SDK_INT>=29)transliterator!!.transliterate(e.poem.s("title"))else e.poem.s("title");"words"->Poetry.wordCount(e.text).toString().padStart(16,'0');"created"->e.created.toString().padStart(16,'0');"author"->e.poem.s("author");"modified","read","rated"->poems.personal(e).s(sort).padStart(16,'0');else->e.poem.s("title")}
        val cmp=compareBy<PoemStore.Entry>{if(sort in listOf("modified","read","rated")&&poems.personal(it).s(sort).isEmpty())1 else 0}.thenComparator {a,b->val c=if(sort=="pinyin"&&android.os.Build.VERSION.SDK_INT<29)java.text.Collator.getInstance(java.util.Locale.CHINA).compare(key(a),key(b))else key(a).compareTo(key(b));if(prefs.getBoolean("desc",false))-c else c}.thenBy{it.key}
        filtered.sortedWith(cmp)
    },done)}
    private fun startQuiz(entries:List<PoemStore.Entry>){if(entries.isEmpty()){message("当前范围没有诗文");return};val active=poems.state("session");if(active.s("status")=="active"){open(LearnActivity::class.java);return};val forms=listOf("整篇","随机句","接续","题目提示","隐藏题目");choose("抽背形式",forms){f->choose("权重",listOf("等权重","薄弱优先","手动权重")){weight->
        val candidates=entries.map{e->val p=poems.personal(e);Poetry.Candidate(e.key,e.poem,when(weight){1->when(p.s("score")){"0"->4;"1"->3;"2"->2;"3"->1;else->2};2->p.s("weight").toIntOrNull()?:1;else->1})}
        val config=poems.state("learning")
        val questions=Poetry.questions(candidates,forms[f],(config.s("count").toIntOrNull()?:10).coerceIn(1,200),(config.s("rounds").toIntOrNull()?:1).coerceIn(1,20),config.s("repeat").ifBlank{"诗文不重复"})
        if(questions.isEmpty())message("没有适用题目：请检查句数及权重")else{LearnActivity.create(poems,questions,"抽背");open(LearnActivity::class.java)}
    }}}
    private fun sortedGroups(values:List<JsonObject>):List<JsonObject>{
        val mode=prefs.getString("groupSort","名称");val descending=prefs.getBoolean("groupDesc",false)
        val chinese=java.text.Collator.getInstance(java.util.Locale.CHINA)
        fun stamp(g:JsonObject,k:String):Long=if(k in listOf("read","rated"))poems.members(g).maxOfOrNull{poems.state("personal:$it").s(k).toLongOrNull()?:0L}?:0L else g.s(k).toLongOrNull()?:0L
        return values.sortedWith(Comparator{a,b->
            val timeKey=when(mode){"创建时间"->"created";"修改时间"->"modified";"最近阅读"->"read";"最近评价"->"rated";else->null};if(timeKey!=null){val missing=(if(stamp(a,timeKey)==0L)1 else 0).compareTo(if(stamp(b,timeKey)==0L)1 else 0);if(missing!=0)return@Comparator missing};var result=when(mode){"成员数"->poems.members(a).size.compareTo(poems.members(b).size);"创建时间"->stamp(a,"created").compareTo(stamp(b,"created"));"修改时间"->stamp(a,"modified").compareTo(stamp(b,"modified"));"最近阅读"->stamp(a,"read").compareTo(stamp(b,"read"));"最近评价"->stamp(a,"rated").compareTo(stamp(b,"rated"));"手动顺序"->values.indexOf(a).compareTo(values.indexOf(b));"名称拼音"->chinese.compare(a.s("name"),b.s("name"));else->a.s("name").compareTo(b.s("name"))}
            if(descending)result=-result
            if(result==0)a.s("id").compareTo(b.s("id"))else result
        })
    }
    private fun groupList(field:String){work({poems.entries().flatMap{e->(if(field=="tags")poems.tags(e)else listOf(e.poem.s("author").ifBlank{"佚名"})).map{it to e.key}}.groupBy({it.first},{it.second})}){map->page(if(field=="tags")"标签"else"作者",{home()});if(field=="tags")panel.addView(ui.button("管理个人标签"){manageTags()});val values=map.map{(name,keys)->JsonObject().apply{addProperty("id",name);addProperty("name",name);add("members",JsonArray().apply{keys.forEach{add(it)}})}};sortedGroups(values).forEach{g->panel.addView(ui.button("${g.s("name")} · ${poems.members(g).size}"){scope=g.s("name");groupFilter=poems.members(g);reset()})}}}
    private fun collectionList(){page("自定义诗集",{home()});panel.addView(ui.button("新增诗集"){edit("诗集名称"){name->require(name.isNotBlank());val list=poems.collections();list.add(JsonObject().apply{addProperty("id",Poetry.id());addProperty("name",name);addProperty("created",System.currentTimeMillis());add("members",JsonArray())});poems.saveCollections(list);collectionList()}});val list=poems.collections().map{it.asJsonObject};sortedGroups(list).forEach{g->panel.addView(ui.button("${g.s("name")} · ${poems.members(g).size}"){collection(g.s("id"))})}}
    private fun moveCollection(id:String,delta:Int){val items=poems.collections().map{it.asJsonObject}.toMutableList();val from=items.indexOfFirst{it.s("id")==id};val target=(from+delta).coerceIn(0,items.lastIndex);java.util.Collections.swap(items,from,target);poems.saveCollections(JsonArray().apply{items.forEach{add(it)}});collection(id)}
    private fun collection(id:String){val all=poems.collections();val g=all.first{it.asJsonObject.s("id")==id}.asJsonObject;page(g.s("name"),{collectionList()});panel.addView(ui.actions("顺序上移" to {moveCollection(id,-1)},"顺序下移" to {moveCollection(id,1)}));panel.addView(ui.actions("阅读诗集" to {scope=g.s("name");groupFilter=poems.members(g);reset()},"改名" to {edit("名称",g.s("name")){require(it.isNotBlank());if(g.s("name")!=it){g.addProperty("name",it);g.addProperty("modified",System.currentTimeMillis())};poems.saveCollections(all);collection(id)}},"删除诗集" to {ui.dialog().setMessage("只删除诗集关系，保留全部诗文。").setNegativeButton("取消",null).setPositiveButton("删除"){_,_->all.remove(g);poems.saveCollections(all);collectionList()}.show()}));work({poems.entries()}){entries->val checked=entries.map{it.key in poems.members(g)}.toBooleanArray();panel.addView(ui.button("管理成员"){ui.dialog().setMultiChoiceItems(entries.map{it.poem.s("title")}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("保存"){_,_->g.add("members",JsonArray().apply{entries.forEachIndexed{i,e->if(checked[i])add(e.key)}});g.addProperty("modified",System.currentTimeMillis());poems.saveCollections(all);collection(id)}.setNegativeButton("取消",null).show()})}}
    private fun newPoem(){val id=Poetry.id();val poem=JsonObject().apply{addProperty("id",Poetry.id());addProperty("title","新诗文");add("stanzas",JsonArray().apply{add(JsonObject().apply{addProperty("id",Poetry.id());add("lines",JsonArray().apply{add(JsonObject().apply{addProperty("id",Poetry.id());addProperty("original","在编辑器填写原文")})})})})};val book=JsonObject().apply{addProperty("id",id);addProperty("title","本地创作");addProperty("type","poetry");add("poems",JsonArray().apply{add(poem)})};work({store.importFile(Protocol.wrap(listOf(book)))}){open(PoemEditorActivity::class.java,"$id/${poem.s("id")}")}}
    private fun settings(){page("首页显示设置",{home()});panel.addView(ui.button("外观布局"){appearance()});panel.addView(ui.button("筛选排序"){filters()});panel.addView(ui.button("学习"){learningSettings()});panel.addView(ui.button("数据"){open(DataActivity::class.java)});panel.addView(ui.button("更新"){open(org.quietreader.update.UpdateActivity::class.java)});panel.addView(ui.button("原书架与小说"){open(ShelfActivity::class.java)})}
    private fun appearance(){page("外观布局",{settings()});panel.addView(ui.button("管理首页显示"){manageHome()});panel.addView(ui.button("列表 / 网格"){prefs.edit().putBoolean("grid",!prefs.getBoolean("grid",false)).apply();appearance()});panel.addView(ui.button("网格列数"){choose("列数",listOf("1","2","3","4")){prefs.edit().putInt("cols",it+1).apply()}});panel.addView(ui.button("密度：${if(prefs.getBoolean("dense",false))"紧凑"else"舒适"}"){prefs.edit().putBoolean("dense",!prefs.getBoolean("dense",false)).apply();appearance()});panel.addView(ui.button("主题"){choose("主题",listOf("system","sepia","dark")){store.saveSettings(store.settings().copy(theme=listOf("system","sepia","dark")[it]));appearance()}})}
    private fun filters(){page("筛选排序",{settings()});val labels=listOf("标题","拼音","字数","创建时间","修改时间","作者","最近阅读","最近评价");val keys=listOf("title","pinyin","words","created","modified","author","read","rated");panel.addView(ui.button("诗文排序"){choose("排序",labels){prefs.edit().putString("sort",keys[it]).apply();pageIndex=0}});panel.addView(ui.button("诗文升降序"){prefs.edit().putBoolean("desc",!prefs.getBoolean("desc",false)).apply();pageIndex=0});panel.addView(ui.button("分组排序"){choose("分组排序",listOf("名称","名称拼音","成员数","创建时间","修改时间","最近阅读","最近评价","手动顺序")){prefs.edit().putString("groupSort",listOf("名称","名称拼音","成员数","创建时间","修改时间","最近阅读","最近评价","手动顺序")[it]).apply()}});panel.addView(ui.button("分组升降序"){prefs.edit().putBoolean("groupDesc",!prefs.getBoolean("groupDesc",false)).apply()});panel.addView(ui.button("条件组之间：${if(groupAny)"任一满足"else"全部满足"}"){groupAny=!groupAny;filters()});panel.addView(ui.button("新增条件组"){val group=Poetry.FilterGroup();groups.add(group);filterGroup(groups.lastIndex)});groups.forEachIndexed{i,g->panel.addView(ui.button("第 ${i+1} 组 · ${if(g.any)"或"else"且"} · ${g.conditions.size} 条"){filterGroup(i)})};panel.addView(ui.button("清空筛选"){groups.clear();pageIndex=0;filters()})}
    private fun filterGroup(i:Int){val g=groups[i];page("条件组 ${i+1}",{filters()});panel.addView(ui.button("组内：${if(g.any)"任一满足"else"全部满足"}"){groups[i]=g.copy(any=!g.any);filterGroup(i)});g.conditions.forEach{panel.addView(ui.text("${it.field} 包含 ${it.value}"))};panel.addView(ui.button("添加条件"){val names=listOf("作者","朝代","体裁","标签","来源诗集","最少字数","最多字数","收藏（是/否）","有辅助（是/否）","阅读状态（已读）","评价（0~3）");val fields=listOf("author","dynasty","genre","tags","source","minWords","maxWords","favorite","aux","status","score");choose("条件字段",names){f->edit(names[f]){value->groups[i]=g.copy(conditions=g.conditions+Poetry.Condition(fields[f],value));pageIndex=0;filterGroup(i)}}});panel.addView(ui.button("删除本组"){groups.removeAt(i);filters()})}
    private fun learningSettings(){val cfg=poems.state("learning");page("学习设置",{settings()});listOf("count" to "每轮题数（1~200）","rounds" to "轮数（1~20）").forEach{(k,label)->panel.addView(ui.button("$label：${cfg.s(k)}"){edit(label,cfg.s(k)){v->require(v.toIntOrNull() in if(k=="count")1..200 else 1..20);cfg.addProperty(k,v);poems.save("learning",cfg);learningSettings()}})};panel.addView(ui.button("重复策略：${cfg.s("repeat")}"){choose("重复策略",listOf("诗文不重复","题目不重复","允许重复")){cfg.addProperty("repeat",listOf("诗文不重复","题目不重复","允许重复")[it]);poems.save("learning",cfg);learningSettings()}});panel.addView(ui.button("退出策略：${cfg.s("exit").ifBlank{"询问"}}"){choose("退出策略",listOf("询问","保存并暂停","放弃本次")){cfg.addProperty("exit",listOf("询问","保存并暂停","放弃本次")[it]);poems.save("learning",cfg);learningSettings()}})}
}
