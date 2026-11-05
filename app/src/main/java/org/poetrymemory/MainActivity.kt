package org.poetrymemory

import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.google.gson.*
import org.poetrymemory.core.*
import org.poetrymemory.poetry.*
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
    override fun onResume(){super.onResume();restoreHome();refreshTab()}
    override fun onPause(){poems.save("home",JsonObject().apply{add("preferences",Gson().toJsonTree(prefs.all));add("filters",Gson().toJsonTree(groups));addProperty("any",groupAny)});super.onPause()}
    override fun onSaveInstanceState(out:Bundle){out.putString("scope",scope);out.putString("search",search);super.onSaveInstanceState(out)}
    private fun restoreHome(){val data=poems.state("home");data.getAsJsonArray("filters")?.let{groups=Gson().fromJson(it,Array<Poetry.FilterGroup>::class.java).toMutableList()};groupAny=data.get("any")?.asBoolean?:false;data.getAsJsonObject("preferences")?.let{values->prefs.edit().apply{values.entrySet().forEach{(key,value)->when{value.asJsonPrimitive.isBoolean->putBoolean(key,value.asBoolean);value.asJsonPrimitive.isNumber->putInt(key,value.asInt);else->putString(key,value.asString)}}}.apply()}}
    private fun manageHome(){work({poems.entries()}){entries->val checked=entries.map{poems.personal(it).s("hidden")!="true"}.toBooleanArray();ui.dialog().setTitle("在首页显示").setMultiChoiceItems(entries.map{it.poem.s("title")}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("保存"){_,_->entries.forEachIndexed{i,e->poems.save("personal:${e.key}",poems.personal(e).apply{addProperty("hidden",!checked[i])})};reset()}.setNegativeButton("取消",null).show()}}
    private fun manageTags(){work({poems.entries()}){entries->val tags=entries.flatMap{poems.personal(it).s("tags").split('，',',').filter{it.isNotBlank()}}.distinct();if(tags.isEmpty()){message("尚无个人标签；可在诗文阅读页添加。");return@work};choose("个人标签管理",tags){i->edit("改名或合并到已有标签；留空删除关系",tags[i]){replacement->entries.forEach{e->val p=poems.personal(e);val old=p.s("tags").split('，',',').filter{it.isNotBlank()};val updated=old.flatMap{if(it==tags[i])listOfNotNull(replacement.takeIf{it.isNotBlank()})else listOf(it)}.distinct();p.addProperty("tags",updated.joinToString(","));poems.save("personal:${e.key}",p)};groupList("tags")}}}}
    private fun reset(){pageIndex=0;home()}
    private var libraryMode="书籍"
    private var currentTab="书库"
    private var activeBook:String?=null
    private fun tabs(selected:String){currentTab=selected;navigation(selected,listOf("书库" to {scope="全部诗文";groupFilter=null;home()},"学习" to {study()},"收藏" to {scope="收藏";groupFilter=null;libraryMode="诗文";home()},"我的" to {settings()}))}
    private fun home(){
        activeBook=null
        val signature="$scope|$search|$groupFilter|$groups|$groupAny|${prefs.getString("sort","title")}|${prefs.getBoolean("desc",false)}"
        if(lastQuery!=signature){pageIndex=0;lastQuery=signature}
        page(if(scope=="收藏")"收藏"else"诗库")
        headerActions.addView(ui.button("搜索"){edit("搜索诗文、作者或书籍",search){search=it;libraryMode="诗文";reset()}})
        menu(listOf("新建诗文" to {newPoem()},"筛选与排序" to {filters()}))
        panel.addView(ui.text("让记忆，慢慢成诗。",14f).apply{setTextColor(ui.muted)})
        if(scope!="收藏")panel.addView(ui.equalActions("书籍" to {libraryMode="书籍";scope="全部诗文";groupFilter=null;reset()},"诗文" to {libraryMode="诗文";reset()},"分组" to {choose("浏览分组",listOf("自定义诗集","作者","标签")){when(it){0->collectionList();1->groupList("author");else->groupList("tags")}}}))
        if(search.isNotBlank()||groups.isNotEmpty())panel.addView(ui.item("当前筛选",listOfNotNull(search.takeIf{it.isNotBlank()},"${groups.size} 组条件").joinToString(" · ")){filters()})
        val results=ui.panel(0);panel.addView(results)
        tabs(if(scope=="收藏")"收藏"else"书库")
        load { entries ->
            if(entries.isEmpty()){
                results.addView(ui.section("从第一首开始"));results.addView(ui.text("书库还是空的。导入自己的诗集，或连接 GitHub 诗库。",17f))
                results.addView(ui.primaryButton("导入诗集"){open(DataActivity::class.java)})
            }else if(libraryMode=="书籍"&&scope!="收藏"){
                val books=entries.groupBy{it.book};results.addView(ui.section("书籍 · ${books.size} 本"))
                books.values.forEach{members->val book=members.first();results.addView(ui.item(book.sourceTitle,"${members.size} 首诗文 · 点开阅读或按书抽背",book.sourceTitle.take(1)){bookDetail(book.book)})}
            }else renderPoems(results,entries)
        }
    }
    private fun renderPoems(target:LinearLayout,entries:List<PoemStore.Entry>){
        target.addView(ui.section("$scope · ${entries.size} 首"))
        val cols=if(prefs.getBoolean("grid",false))prefs.getInt("cols",2)else 1
        entries.drop(pageIndex*40).take(40).chunked(cols).forEach{chunk->target.addView(ui.row().apply{chunk.forEach{e->
            addView(ui.card().apply{
                addView(ui.text((if(poems.favorite(e))"★ "else"")+e.poem.s("title"),20f,true))
                addView(ui.text("${e.poem.s("dynasty")} · ${e.poem.s("author")}",13f).apply{setTextColor(ui.muted)})
                if(!prefs.getBoolean("dense",false))addView(ui.text(e.lines.take(2).joinToString(""){it.s("original")},16f))
                contentDescription="阅读 ${e.poem.s("title")}";isFocusable=true;setOnClickListener{open(PoemReaderActivity::class.java,e.key)}
            },LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(ui.dp(2),ui.dp(5),ui.dp(2),ui.dp(5))})
        }})}
        if(entries.size>40)target.addView(ui.equalActions("上一页" to {pageIndex=(pageIndex-1).coerceAtLeast(0);refreshTab()},"下一页" to {if((pageIndex+1)*40<entries.size){pageIndex++;refreshTab()}}))
    }
    private fun bookDetail(bookId:String){
        val entries=poems.entries().filter{it.book==bookId};if(entries.isEmpty()){message("本书暂无诗文");return}
        page(entries.first().sourceTitle,{home()});activeBook=bookId;currentTab="书库"
        panel.addView(ui.text("${entries.size} 首诗文 · 本书全部内容",14f).apply{setTextColor(ui.muted)})
        panel.addView(ui.primaryButton("抽背本书"){openBookQuiz(listOf(bookId))})
        val results=ui.panel(0);panel.addView(results);renderPoems(results,entries)
    }
    private fun openBookQuiz(ids:List<String> = emptyList()){startActivity(Intent(this,QuizSetupActivity::class.java).putStringArrayListExtra("bookIds",ArrayList(ids)))}
    private fun study(){
        activeBook=null;page("学习");menu(listOf("学习设置" to {learningSettings()}))
        panel.addView(ui.section("开始一次练习"))
        panel.addView(ui.card().apply{addView(ui.text("按书抽背",24f,true));addView(ui.text("选择一本或多本诗集，再设置题型与轮次。",14f));addView(ui.primaryButton("选择书籍"){openBookQuiz()})})
        if(poems.state("session").s("status")=="active")panel.addView(ui.item("继续上次学习","恢复原来的书籍范围、题目和进度","续"){open(LearnActivity::class.java)})
        panel.addView(ui.section("更多练习方式"))
        panel.addView(ui.item("抽背当前筛选结果","沿用诗库中的筛选条件","练"){load{startQuiz(it)}})
        panel.addView(ui.item("学习历史","查看评分、跳过和学习备注","记"){open(HistoryActivity::class.java)})
        tabs("学习")
    }
    private fun refreshTab(){val book=activeBook;if(book!=null)bookDetail(book)else when(currentTab){"学习"->study();"我的"->settings();else->home()}}
    private fun load(done:(List<PoemStore.Entry>)->Unit){work({
        val activeGroups=groups.filter{it.conditions.isNotEmpty()};val filtered=poems.entries().filter { e ->
            (scope!="全部诗文"||poems.personal(e).s("hidden")!="true")&&(scope!="收藏"||poems.favorite(e))&&(groupFilter==null||e.key in groupFilter!!)&&
            (search.isBlank()||listOf(e.poem.s("title"),e.poem.s("author"),e.sourceTitle,e.text).any{it.contains(search,true)})&&
            (activeGroups.isEmpty()||if(groupAny)activeGroups.any{it.matches(poems.fields(e))}else activeGroups.all{it.matches(poems.fields(e))})
        }
        val transliterator=if(android.os.Build.VERSION.SDK_INT>=29)Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")else null
        val sort=prefs.getString("sort","title")!!
        fun key(e:PoemStore.Entry):String=when(sort){"pinyin"->if(android.os.Build.VERSION.SDK_INT>=29)transliterator!!.transliterate(e.poem.s("title"))else e.poem.s("title");"words"->Poetry.wordCount(e.text).toString().padStart(16,'0');"created"->e.created.toString().padStart(16,'0');"author"->e.poem.s("author");"modified","read","rated"->poems.personal(e).s(sort).padStart(16,'0');else->e.poem.s("title")}
        val cmp=compareBy<PoemStore.Entry>{if(sort in listOf("modified","read","rated")&&poems.personal(it).s(sort).isEmpty())1 else 0}.thenComparator {a,b->val c=if(sort=="pinyin"&&android.os.Build.VERSION.SDK_INT<29)java.text.Collator.getInstance(java.util.Locale.CHINA).compare(key(a),key(b))else key(a).compareTo(key(b));if(prefs.getBoolean("desc",false))-c else c}.thenBy{it.key}
        filtered.sortedWith(cmp)
    },done)}
    private fun startQuiz(entries:List<PoemStore.Entry>){if(entries.isEmpty()){message("当前范围没有诗文");return};startActivity(Intent(this,QuizSetupActivity::class.java).putStringArrayListExtra("poemKeys",ArrayList(entries.map{it.key})).putExtra("scopeLabel","当前筛选结果"))}
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
    private fun settings(){
        activeBook=null;page("我的")
        panel.addView(ui.section("偏好设置"))
        panel.addView(ui.item("外观与布局","主题、列表、网格与首页显示","观"){appearance()})
        panel.addView(ui.item("筛选与排序","组合条件、诗文排序和分组排序","序"){filters()})
        panel.addView(ui.item("学习偏好","轮次、重复策略与退出行为","学"){learningSettings()})
        panel.addView(ui.section("诗库与应用"))
        panel.addView(ui.item("数据与诗库","导入、导出、备份及 GitHub 诗库","库"){open(DataActivity::class.java)})
        panel.addView(ui.item("应用更新","PoetryMemory 独立版本与更新","更"){open(org.poetrymemory.update.UpdateActivity::class.java)})
        tabs("我的")
    }
    private fun appearance(){page("外观布局",{settings()});panel.addView(ui.item("管理首页显示"){manageHome()});panel.addView(ui.item("列表 / 网格"){prefs.edit().putBoolean("grid",!prefs.getBoolean("grid",false)).apply();appearance()});panel.addView(ui.item("网格列数"){choose("列数",listOf("1","2","3","4")){prefs.edit().putInt("cols",it+1).apply()}});panel.addView(ui.item("密度：${if(prefs.getBoolean("dense",false))"紧凑"else"舒适"}"){prefs.edit().putBoolean("dense",!prefs.getBoolean("dense",false)).apply();appearance()});panel.addView(ui.item("主题"){choose("主题",listOf("system","sepia","dark")){store.saveSettings(store.settings().copy(theme=listOf("system","sepia","dark")[it]));appearance()}})}
    private fun filters(){page("筛选排序",{settings()});val labels=listOf("标题","拼音","字数","创建时间","修改时间","作者","最近阅读","最近评价");val keys=listOf("title","pinyin","words","created","modified","author","read","rated");panel.addView(ui.item("诗文排序"){choose("排序",labels){prefs.edit().putString("sort",keys[it]).apply();pageIndex=0}});panel.addView(ui.item("诗文升降序"){prefs.edit().putBoolean("desc",!prefs.getBoolean("desc",false)).apply();pageIndex=0});panel.addView(ui.item("分组排序"){choose("分组排序",listOf("名称","名称拼音","成员数","创建时间","修改时间","最近阅读","最近评价","手动顺序")){prefs.edit().putString("groupSort",listOf("名称","名称拼音","成员数","创建时间","修改时间","最近阅读","最近评价","手动顺序")[it]).apply()}});panel.addView(ui.item("分组升降序"){prefs.edit().putBoolean("groupDesc",!prefs.getBoolean("groupDesc",false)).apply()});panel.addView(ui.item("条件组之间：${if(groupAny)"任一满足"else"全部满足"}"){groupAny=!groupAny;filters()});panel.addView(ui.item("新增条件组"){val group=Poetry.FilterGroup();groups.add(group);filterGroup(groups.lastIndex)});groups.forEachIndexed{i,g->panel.addView(ui.item("第 ${i+1} 组 · ${if(g.any)"或"else"且"} · ${g.conditions.size} 条"){filterGroup(i)})};panel.addView(ui.item("清空筛选"){groups.clear();pageIndex=0;filters()})}
    private fun filterGroup(i:Int){val g=groups[i];page("条件组 ${i+1}",{filters()});panel.addView(ui.item("组内：${if(g.any)"任一满足"else"全部满足"}"){groups[i]=g.copy(any=!g.any);filterGroup(i)});g.conditions.forEach{panel.addView(ui.text("${it.field} 包含 ${it.value}"))};panel.addView(ui.item("添加条件"){val names=listOf("作者","朝代","体裁","标签","来源诗集","最少字数","最多字数","收藏（是/否）","有辅助（是/否）","阅读状态（已读）","评价（0~3）");val fields=listOf("author","dynasty","genre","tags","source","minWords","maxWords","favorite","aux","status","score");choose("条件字段",names){f->edit(names[f]){value->groups[i]=g.copy(conditions=g.conditions+Poetry.Condition(fields[f],value));pageIndex=0;filterGroup(i)}}});panel.addView(ui.item("删除本组"){groups.removeAt(i);filters()})}
    private fun learningSettings(){val cfg=poems.state("learning");page("学习设置",{settings()});listOf("count" to "每轮题数（1~200）","rounds" to "轮数（1~20）").forEach{(k,label)->panel.addView(ui.item("$label：${cfg.s(k)}"){edit(label,cfg.s(k)){v->require(v.toIntOrNull() in if(k=="count")1..200 else 1..20);cfg.addProperty(k,v);poems.save("learning",cfg);learningSettings()}})};panel.addView(ui.item("重复策略：${cfg.s("repeat")}"){choose("重复策略",listOf("诗文不重复","题目不重复","允许重复")){cfg.addProperty("repeat",listOf("诗文不重复","题目不重复","允许重复")[it]);poems.save("learning",cfg);learningSettings()}});panel.addView(ui.item("退出策略：${cfg.s("exit").ifBlank{"询问"}}"){choose("退出策略",listOf("询问","保存并暂停","放弃本次")){cfg.addProperty("exit",listOf("询问","保存并暂停","放弃本次")[it]);poems.save("learning",cfg);learningSettings()}})}
}
