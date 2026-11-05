package org.poetrymemory.poetry

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.poetrymemory.core.*
import org.poetrymemory.data.LibraryStore

/** All poem-specific state participates in LibraryStore's transactional SQLite backup. */
class PoemStore(val library: LibraryStore) {
    data class Entry(val book: String, val id: String, val sourceTitle: String, val source: JsonObject, val poem: JsonObject, val created: Long, val sourceTags:List<String> = emptyList()) {
        val key get()="$book/$id"
        val lines get()=Poetry.lines(poem)
        val text get()=lines.joinToString("") { it.s("original") }
    }
    fun state(key: String): JsonObject = library.remoteMetadata("poetry:$key") ?: JsonObject()
    fun save(key: String, value: JsonObject) = library.saveRemoteMetadata("poetry:$key",value)
    fun entries(): List<Entry> = library.listBooks().filter{it.type=="poetry"}.flatMap { b ->
        library.sections(b.id).mapNotNull { section -> library.section(b.id,section.id)?.let { source ->
            val key="${b.id}/${section.id}";val overlay=state("overlay:$key").getAsJsonObject("poem")
            Entry(b.id,section.id,b.title,source,overlay?:source,b.importedAt,b.tags)
        } }
    }
    fun entry(key: String)=entries().firstOrNull{it.key==key}
    fun selectBooks(entries:List<Entry>,bookIds:Set<String>):List<Entry> = entries.filter{it.book in bookIds}
    fun favorite(e: Entry)=library.isPoemFavorite(e.book,e.id)
    fun personal(e: Entry)=state("personal:${e.key}")
    fun tags(e: Entry)=(e.sourceTags+e.poem.getAsJsonArray("tags")?.map{it.asString}.orEmpty()+personal(e).s("tags").split('，',',').filter{it.isNotBlank()}).distinct()
    fun fields(e: Entry): Map<String,String> = mapOf("title" to e.poem.s("title"),"author" to e.poem.s("author"),"dynasty" to e.poem.s("dynasty"),"genre" to e.poem.s("genre"),"tags" to tags(e).joinToString(","),"source" to e.sourceTitle,"words" to Poetry.wordCount(e.text).toString(),"favorite" to if(favorite(e))"是" else "否","aux" to if(e.lines.any{it.s("pinyin").isNotEmpty()||it.s("translation").isNotEmpty()||it.s("note").isNotEmpty()})"是"else"否","status" to personal(e).s("status"),"score" to personal(e).s("score"))
    fun collections(): JsonArray=state("collections").getAsJsonArray("items")?:JsonArray()
    fun saveCollections(items: JsonArray)=save("collections",JsonObject().apply{add("items",items)})
    fun members(group: JsonObject)=group.getAsJsonArray("members")?.map{it.asString}.orEmpty()
    fun saveEdit(e: Entry, poem: JsonObject, expected: String) = library.poetryTransaction {
        val latest=entry(e.key)?:error("诗文已删除；草稿仍保留")
        require(Poetry.revision(latest.poem)==expected){"正文版本已变化，已保留草稿。请重新打开并对照最新版本。"}
        require(poem.s("title").isNotBlank()&&Poetry.lines(poem).isNotEmpty()){"标题和正文不能为空"}
        val book=library.book(e.book)!!;val poems=book.getAsJsonArray("poems")
        for(i in 0 until poems.size())if(poems[i].asJsonObject.s("id")==e.id)poems.set(i,poem)
        val parsed=Protocol.parse(Protocol.wrap(listOf(book)).toString(),"编辑")
        require(parsed.valid){parsed.errors.joinToString("\n"){it.message}}
        if(Poetry.revision(poem)!=expected){
            save("overlay:${e.key}",JsonObject().apply{add("poem",poem);addProperty("sourceRevision",Poetry.revision(e.source))})
            save("personal:${e.key}",personal(e).apply{addProperty("modified",System.currentTimeMillis())})
        }
        save("draft:${e.key}",JsonObject())
    }
    fun effectiveExport(): JsonObject = Protocol.wrap(library.listBooks().filter{it.type=="poetry"}.map { b -> library.book(b.id)!!.apply {
        if(b.type=="poetry") add("poems",JsonArray().apply{entries().filter{it.book==b.id}.forEach{add(it.poem)}})
    } })
}
