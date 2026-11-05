package org.quietreader.poetry

import com.google.gson.*
import org.quietreader.core.s

object PoetryBackups {
    /** Selected-book backups must never carry other books' private drafts or question snapshots. */
    fun restrict(metadata:JsonObject,books:Set<String>){
        fun selected(key:String)=key.substringBefore('/') in books
        fun session(s:JsonObject):JsonObject? {
            val questions=s.getAsJsonArray("questions")?:return null
            val kept=questions.filter{selected(it.asJsonObject.s("key"))};if(kept.isEmpty())return null
            val result=s.deepCopy();result.add("questions",JsonArray().apply{kept.forEach{add(it)}})
            val ids=kept.map{it.asJsonObject.s("id")}.toSet()
            listOf("results","hints","notes").forEach{k->result.getAsJsonObject(k)?.keySet()?.toList()?.filter{it !in ids}?.forEach{result.getAsJsonObject(k).remove(it)}}
            result.addProperty("cursor",kept.takeWhile{result.getAsJsonObject("results")?.has(it.asJsonObject.s("id"))==true}.size)
            return result
        }
        metadata.keySet().toList().filter{it.startsWith("remote:poetry:")}.forEach { key ->
            val suffix=key.removePrefix("remote:poetry:")
            val data=JsonParser.parseString(metadata.get(key).asString).asJsonObject
            val restricted:JsonObject?=when {
                suffix=="collections"->data.apply{getAsJsonArray("items")?.forEach{g->val group=g.asJsonObject;group.add("members",JsonArray().apply{group.getAsJsonArray("members")?.filter{selected(it.asString)}?.forEach{add(it)}})}}
                suffix=="session"->session(data)
                suffix=="history"->JsonObject().apply{data.entrySet().forEach{(id,value)->session(value.asJsonObject)?.let{add(id,it)}}}
                suffix.startsWith("reader:")&&suffix!="reader:global" -> data.takeIf{selected(suffix.removePrefix("reader:"))}
                suffix.substringBefore(':') in listOf("personal","overlay","draft","removed")->data.takeIf{selected(suffix.substringAfter(':'))}
                else->data
            }
            if(restricted==null)metadata.remove(key)else metadata.addProperty(key,restricted.toString())
        }
    }
}
