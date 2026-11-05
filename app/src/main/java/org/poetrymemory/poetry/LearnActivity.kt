package org.poetrymemory.poetry

import android.os.Bundle
import com.google.gson.*
import org.poetrymemory.core.*

class LearnActivity: PoetryActivity() {
    private lateinit var session:JsonObject
    private var answer=false
    companion object {
        @Synchronized fun create(store:PoemStore,questions:List<Poetry.Question>,kind:String) {
            if(store.state("session").s("status")=="active")return
            store.save("session",JsonObject().apply{addProperty("id",Poetry.id());addProperty("kind",kind);addProperty("status","active");addProperty("started",System.currentTimeMillis());addProperty("cursor",0);add("questions",Gson().toJsonTree(questions));add("results",JsonObject());add("hints",JsonObject());add("notes",JsonObject())})
        }
    }
    private fun cursor()=session.get("cursor")?.asInt?:0
    private fun questions()=session.getAsJsonArray("questions")
    private fun question()=questions()[cursor()].asJsonObject
    private fun save(){poems.save("session",session)}
    override fun onCreate(state:Bundle?){super.onCreate(state);session=poems.state("session");if(session.s("status")!="active"){page("继续学习",{finish()});panel.addView(ui.text("没有待恢复的学习。请从诗文页开始背诵，或在首页抽背。"));return};render()}
    private fun render(){
        if(cursor()>=questions().size()){finishSession();return}
        val q=question();val poem=q.getAsJsonObject("poem");val rows=Poetry.lines(poem);val id=q.s("id")
        answer=session.getAsJsonObject("answers")?.get(id)?.asBoolean?:false
        val form=q.s("form");val kind=session.s("kind");val pos=(q.get("index")?.asInt?:0).coerceIn(0,rows.lastIndex.coerceAtLeast(0))
        page(kind,{exit()})
        menu(listOf("学习备注" to {edit("本题学习备注",session.getAsJsonObject("notes").s(id)){session.getAsJsonObject("notes").addProperty(id,it);save()}},"篇级资料" to {message(listOf("background","translation","appreciation").joinToString("\n\n"){poem.s(it).ifBlank{"本篇未提供"}})},"退出学习" to {exit()}))
        if(session.s("scopeLabel").isNotBlank())panel.addView(ui.text(session.s("scopeLabel"),14f).apply{setTextColor(ui.muted)})
        panel.addView(ui.text("${cursor()+1} / ${questions().size()}  ·  第 ${q.s("round")} 轮  ·  $form",13f))
        panel.addView(android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=questions().size();progress=cursor();progressTintList=android.content.res.ColorStateList.valueOf(ui.accent)})
        panel.addView(ui.section("本题"))
        val content=when {
            answer -> if(form=="随机句")rows[pos].s("original")else rows.joinToString("\n"){it.s("original")}
            form=="接续" -> "请接着背诵\n${rows[pos].s("original")}\n……"
            form=="逐句" -> "请背第 ${pos+1} 句\n${if(pos>0)"上一句："+rows[pos-1].s("original")else"从首句开始"}"
            form=="随机句" -> "请背第 ${pos+1} 句（共 ${rows.size} 句）"
            else -> "试着完整背诵这首诗。\n想不起来时，可以先看提示。"
        }
        panel.addView(ui.card().apply{
            addView(ui.text(if(form=="隐藏题目")"${poem.s("author")}：${rows.firstOrNull()?.s("original").orEmpty()}"else poem.s("title"),24f,true))
            if(form!="隐藏题目")addView(ui.text(poem.s("author"),14f).apply{setTextColor(ui.muted)})
            addView(ui.text(content,23f))
        })
        panel.addView(ui.equalActions("提示" to {val names=listOf("拼音","译文","注释","下一句","首字","前两字","全部辅助");choose("提示",names){session.getAsJsonObject("hints").addProperty(id,true);save();message(Poetry.hint(poem,if(form=="接续")(pos+1).coerceAtMost(rows.lastIndex)else pos,names[it]))}},"查看答案" to {answer=true;val answers=session.getAsJsonObject("answers")?:JsonObject().also{session.add("answers",it)};answers.addProperty(id,true);session.getAsJsonObject("hints").addProperty(id,true);save();render()}))
        if(form=="逐句"&&kind=="背诵")panel.addView(ui.equalActions("上一句" to {q.addProperty("index",(pos-1).coerceAtLeast(0));save();answer=false;render()},"记住本句，下一句" to {if(pos<rows.lastIndex){q.addProperty("index",pos+1);save();answer=false;render()}else message("已到末句，请评价本次背诵")}))
        root.addView(ui.panel(0).apply{
            addView(ui.section("这次记得怎么样？"))
            addView(ui.equalActions("不会" to {submit(0)},"困难" to {submit(1)},"基本会" to {submit(2)},"熟练" to {submit(3)}))
            addView(ui.text("跳过",14f).apply{gravity=android.view.Gravity.CENTER;minimumHeight=ui.dp(44);isClickable=true;isFocusable=true;setOnClickListener{submit(-1)}})
        })
        save()
    }
    private fun submit(score:Int){
        val q=question();val id=q.s("id");val results=session.getAsJsonObject("results");if(results.has(id))return
        results.addProperty(id,score)
        if(score>=0){val p=poems.state("personal:${q.s("key")}");p.addProperty("rated",System.currentTimeMillis());p.addProperty("score",score);poems.save("personal:${q.s("key")}",p)}
        q.addProperty("ratedAt",if(score>=0)System.currentTimeMillis()else 0L);session.addProperty("cursor",cursor()+1);answer=false;save();render()
    }
    private fun finishSession(){session.addProperty("status","completed");session.addProperty("ended",System.currentTimeMillis());val history=poems.state("history");history.add(session.s("id"),session.deepCopy());poems.save("history",history);save();page("本次学习已结束",{finish()});panel.addView(ui.text(HistoryActivity.summary(session)));panel.addView(ui.button("返回首页"){finish()})}
    private fun exit(){val strategy=poems.state("learning").s("exit");fun pause(){save();finish()};fun abandon(){session.addProperty("status","abandoned");session.addProperty("ended",System.currentTimeMillis());val h=poems.state("history");h.add(session.s("id"),session.deepCopy());poems.save("history",h);save();finish()};when(strategy){"保存并暂停"->pause();"放弃本次"->abandon();else->ui.dialog().setTitle("退出学习").setMessage("保存后可恢复当前题目和备注。").setPositiveButton("保存并暂停"){_,_->pause()}.setNegativeButton("继续学习",null).setNeutralButton("放弃本次"){_,_->abandon()}.show()}}
    override fun onPause(){if(::session.isInitialized&&session.has("id"))save();super.onPause()}
    override fun navigateBack(){if(::session.isInitialized&&session.s("status")=="active")exit()else finish()}
}
