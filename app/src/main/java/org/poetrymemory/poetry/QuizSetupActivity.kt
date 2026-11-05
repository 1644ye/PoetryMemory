package org.poetrymemory.poetry

import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import org.poetrymemory.core.*
import com.google.gson.JsonObject

/** A quiz owns an explicit, immutable candidate snapshot, independent of home filters. */
class QuizSetupActivity:PoetryActivity(){
    private var entries=emptyList<PoemStore.Entry>()
    private val selected=linkedSetOf<String>()
    private var fixedKeys:Set<String>?=null
    private var form="整篇"
    private var weight="等权重"
    private var count=10
    private var rounds=1
    private var repeat="诗文不重复"
    private var starting=false
    private val forms=listOf("整篇","随机句","接续","题目提示","隐藏题目")
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val config=poems.state("learning")
        selected.addAll(state?.getStringArrayList("books")?:intent.getStringArrayListExtra("bookIds").orEmpty())
        fixedKeys=intent.getStringArrayListExtra("poemKeys")?.toSet()
        form=state?.getString("form")?:"整篇";weight=state?.getString("weight")?:"等权重"
        count=state?.getInt("count")?:config.s("count").toIntOrNull()?:10
        rounds=state?.getInt("rounds")?:config.s("rounds").toIntOrNull()?:1
        repeat=state?.getString("repeat")?:config.s("repeat").ifBlank{"诗文不重复"}
        page("设置抽背",{finish()});panel.addView(ui.text("正在读取诗集…"))
        work({poems.entries()}){entries=it;selected.retainAll(entries.map{e->e.book}.toSet());render()}
    }
    override fun onSaveInstanceState(out:Bundle){out.putStringArrayList("books",ArrayList(selected));out.putString("form",form);out.putString("weight",weight);out.putInt("count",count);out.putInt("rounds",rounds);out.putString("repeat",repeat);super.onSaveInstanceState(out)}
    private fun candidates()=if(fixedKeys!=null)entries.filter{it.key in fixedKeys!!}else poems.selectBooks(entries,selected)
    private fun scopeLabel()=if(fixedKeys!=null)intent.getStringExtra("scopeLabel")?:"已选诗文"else entries.filter{it.book in selected}.distinctBy{it.book}.joinToString("、"){it.sourceTitle}
    private fun render(){
        page("设置抽背",{finish()})
        panel.addView(ui.section("01  选择范围"))
        val range=candidates()
        panel.addView(ui.card().apply{
            addView(ui.text(if(fixedKeys!=null)"当前筛选结果"else if(selected.isEmpty())"还没有选择书籍"else scopeLabel(),21f,true))
            addView(ui.text("${if(fixedKeys==null)"${selected.size} 本 · "else""}${range.size} 首候选诗文",14f))
            if(fixedKeys==null)addView(ui.button("选择书籍"){selectBooks()})
        })
        panel.addView(ui.section("02  练习方式"))
        panel.addView(ui.item("抽背形式",form){choose("抽背形式",forms){form=forms[it];render()}})
        val example=when(form){"随机句"->"给出句子位置，背出指定句子";"接续"->"给出上一句，从下一句继续背诵";"隐藏题目"->"隐藏标题，保留作者和起始句线索";"题目提示"->"给出标题与作者，背诵整篇";else->"给出篇目，背诵完整诗文"}
        panel.addView(ui.text(example,13f).apply{setTextColor(ui.muted)})
        panel.addView(ui.item("抽取权重",weight){val values=listOf("等权重","薄弱优先","手动权重");choose("抽取权重",values){weight=values[it];render()}})
        panel.addView(ui.section("03  题数与轮次"))
        panel.addView(ui.item("每轮题数","$count 题"){edit("每轮题数（1–200）",count.toString()){require(it.toIntOrNull() in 1..200);count=it.toInt();render()}})
        panel.addView(ui.item("练习轮次","$rounds 轮"){edit("轮数（1–20）",rounds.toString()){require(it.toIntOrNull() in 1..20);rounds=it.toInt();render()}})
        panel.addView(ui.item("重复策略",repeat){val values=listOf("诗文不重复","题目不重复","允许重复");choose("重复策略",values){repeat=values[it];render()}})
        panel.addView(ui.text("候选不足时按实际题数结束；评分由你自行评价。",12f).apply{setTextColor(ui.muted)})
        root.addView(ui.primaryButton("开始抽背"){start()}.apply{isEnabled=range.isNotEmpty()&&!starting;alpha=if(isEnabled)1f else .45f},LinearLayout.LayoutParams(-1,-2))
    }
    private fun selectBooks(){
        val books=entries.groupBy{it.book}.values.toList()
        if(books.isEmpty()){message("尚无诗集，请先导入诗集或下载 GitHub 诗库内容");return}
        val checked=books.map{it.first().book in selected}.toBooleanArray()
        ui.dialog().setTitle("选择书籍，可多选").setMultiChoiceItems(books.map{"${it.first().sourceTitle} · ${it.size} 首"}.toTypedArray(),checked){_,i,value->checked[i]=value}
            .setNegativeButton("取消",null).setPositiveButton("确认范围"){_,_->selected.clear();books.forEachIndexed{i,book->if(checked[i])selected.add(book.first().book)};render()}.show()
    }
    private fun start(){
        if(starting)return
        if(poems.state("session").s("status")=="active"){Toast.makeText(this,"已恢复上次学习，原范围保持不变",Toast.LENGTH_LONG).show();open(LearnActivity::class.java);return}
        val chosen=candidates();if(chosen.isEmpty()){message("请先选择书籍");return}
        starting=true
        val config=JsonObject().apply{addProperty("count",count);addProperty("rounds",rounds);addProperty("repeat",repeat);addProperty("exit",poems.state("learning").s("exit"))};poems.save("learning",config)
        val pool=chosen.map{entry->val p=poems.personal(entry);Poetry.Candidate(entry.key,entry.poem,when(weight){"薄弱优先"->when(p.s("score")){"0"->4;"1"->3;"3"->1;else->2};"手动权重"->p.s("weight").toIntOrNull()?:1;else->1})}
        val questions=Poetry.questions(pool,form,count,rounds,repeat)
        if(questions.isEmpty()){starting=false;message("所选书籍没有适用题目，请检查句数或手动权重");return}
        LearnActivity.create(poems,questions,"抽背")
        poems.save("session",poems.state("session").apply{addProperty("scopeLabel",scopeLabel())})
        startActivity(Intent(this,LearnActivity::class.java));finish()
    }
}
