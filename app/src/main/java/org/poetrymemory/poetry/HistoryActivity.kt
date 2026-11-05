package org.poetrymemory.poetry

import android.os.Bundle
import com.google.gson.JsonObject
import org.poetrymemory.core.*

class HistoryActivity:PoetryActivity(){
    companion object{fun summary(session:JsonObject):String{val results=session.getAsJsonObject("results");val stats=Poetry.statistics(session.getAsJsonArray("questions").map{results.get(it.asJsonObject.s("id"))?.asInt});return "实际出题 ${stats.total} · 已评分 ${stats.rated} · 跳过 ${stats.skipped} · 未完成 ${stats.unfinished}\n自评均分 ${stats.average?.let{"%.2f".format(it)}?:"暂无评分"}\n0/1/2/3 分题数：${stats.scores.joinToString(" / ")}\n使用提示 ${session.getAsJsonObject("hints").size()} 题"}}
    override fun onCreate(state:Bundle?){super.onCreate(state);showHistory()}
    private fun showHistory(){page("学习历史",{finish()});val history=poems.state("history");if(history.size()==0)panel.addView(ui.text("尚无完成的学习记录"));history.entrySet().reversed().forEach{(_,value)->val s=value.asJsonObject;panel.addView(ui.button("${s.s("kind")} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.get("started").asLong))}\n${summary(s)}"){details(s.s("id"))})}}
    private fun details(id:String){val history=poems.state("history");val s=history.getAsJsonObject(id);page("记录详情",{showHistory()});panel.addView(ui.text(summary(s)));s.getAsJsonArray("questions").forEach{value->val q=value.asJsonObject;val qid=q.s("id");val result=s.getAsJsonObject("results").get(qid)?.asInt;panel.addView(ui.text("${q.getAsJsonObject("poem").s("title")} · ${if(result==-1)"跳过"else result?.toString()?:"未完成"}\n${s.getAsJsonObject("notes").s(qid)}"));if(result!=null&&result>=0)panel.addView(ui.button("修改评价"){choose("评价",listOf("不会","困难","基本会","熟练")){score->s.getAsJsonObject("results").addProperty(qid,score);q.addProperty("ratedAt",System.currentTimeMillis());poems.save("history",history);val p=poems.state("personal:${q.s("key")}");p.addProperty("rated",System.currentTimeMillis());p.addProperty("score",score);poems.save("personal:${q.s("key")}",p);details(id)}})}}
}
