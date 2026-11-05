package org.quietreader.core

import com.google.gson.JsonObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

object Poetry {
    fun id() = UUID.randomUUID().toString()
    fun revision(value: JsonObject): String = MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    fun lines(poem: JsonObject): List<JsonObject> = poem.getAsJsonArray("stanzas")?.flatMap { it.asJsonObject.getAsJsonArray("lines").map { l -> l.asJsonObject } }.orEmpty()
    fun wordCount(text: String): Int = text.codePoints().filter { Character.isLetterOrDigit(it) }.count().toInt()
    fun first(text: String, count: Int): String = text.codePoints().filter { Character.isLetterOrDigit(it) }.limit(count.toLong()).toArray().let { String(it, 0, it.size) }
    fun hint(poem: JsonObject, index: Int, kind: String): String {
        val rows = lines(poem); val line = rows.getOrNull(index) ?: return "本篇没有句子"
        return when(kind) {
            "首字" -> first(line.s("original"),1)
            "前两字" -> first(line.s("original"),2)
            "下一句" -> rows.getOrNull(index+1)?.s("original") ?: "已到末句"
            "全部辅助" -> listOf("pinyin","translation","note").mapNotNull { line.s(it).takeIf(String::isNotBlank) }.joinToString("\n").ifBlank { "本句未提供辅助内容" }
            else -> line.s(when(kind){"拼音"->"pinyin";"译文"->"translation";else->"note"}).ifBlank { "本句未提供$kind" }
        }
    }
    data class Candidate(val key: String, val poem: JsonObject, val weight: Int = 1)
    data class Question(val id: String, val key: String, val poem: JsonObject, val lineId: String, val index: Int, val form: String, val round: Int)
    fun questions(candidates: List<Candidate>, form: String, count: Int, rounds: Int, repeat: String, random: Random = Random.Default): List<Question> {
        require(count in 1..200 && rounds in 1..20)
        val pool = candidates.filter { it.weight > 0 }.flatMap { c ->
            val ls=lines(c.poem)
            val indices=when(form){"随机句"->ls.indices.toList();"接续"->(0 until (ls.size-1).coerceAtLeast(0)).toList();else->if(ls.isEmpty()) emptyList() else listOf(0)}
            indices.map { c to it }
        }
        return buildList {
            for(round in 1..rounds) {
                val available=pool.toMutableList()
                for(n in 0 until count) {
                    if(available.isEmpty())break
                    var ticket=random.nextInt(available.sumOf { it.first.weight.coerceAtMost(100) })
                    val picked=available.first { ticket-=it.first.weight.coerceAtMost(100); ticket<0 }
                    val (c,index)=picked
                    add(Question(id(),c.key,c.poem.deepCopy(),lines(c.poem)[index].s("id"),index,form,round))
                    when(repeat){"诗文不重复"->available.removeAll { it.first.key==c.key };"题目不重复"->available.remove(picked)}
                }
            }
        }
    }
    data class Statistics(val total: Int, val rated: Int, val skipped: Int, val unfinished: Int, val average: Double?, val scores: List<Int>)
    fun statistics(results: List<Int?>): Statistics {
        val scores=results.filterNotNull().filter { it in 0..3 }
        return Statistics(results.size,scores.size,results.count{it == -1},results.count{it==null},scores.takeIf{it.isNotEmpty()}?.average(),(0..3).map{s->scores.count{it==s}})
    }
    data class Condition(val field: String, val value: String) {
        fun matches(fields: Map<String,String>): Boolean = when(field) {
            "minWords" -> (fields["words"]?.toIntOrNull()?:0)>=(value.toIntOrNull()?:0)
            "maxWords" -> (fields["words"]?.toIntOrNull()?:0)<=(value.toIntOrNull()?:Int.MAX_VALUE)
            else -> fields[field].orEmpty().contains(value,true)
        }
    }
    data class FilterGroup(val any: Boolean = false, val conditions: List<Condition> = emptyList()) {
        fun matches(fields: Map<String,String>) = conditions.isEmpty() || if(any) conditions.any{it.matches(fields)} else conditions.all{it.matches(fields)}
    }
}
fun JsonObject.s(key: String): String = get(key)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
