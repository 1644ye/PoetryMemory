package org.poetrymemory.core

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser
import kotlin.random.Random

class PoetryTest {
    private fun poem()=Protocol.parse(javaClass.getResource("/protocol/poetry-example.json")!!.readText(),"fixture").document!!.getAsJsonArray("books")[0].asJsonObject.getAsJsonArray("poems")[0].asJsonObject
    @Test fun skipsDoNotAffectAverage(){val s=Poetry.statistics(listOf(0,3,-1,null));assertEquals(2,s.rated);assertEquals(1,s.skipped);assertEquals(1,s.unfinished);assertEquals(1.5,s.average!!,0.0)}
    @Test fun noRatingsHasNoAverage(){assertNull(Poetry.statistics(listOf(-1,null)).average)}
    @Test fun codePointsExcludePunctuationAndWhitespace(){assertEquals(3,Poetry.wordCount("𠀀，明 月。"));assertEquals("𠀀明",Poetry.first(" ，𠀀明月",2))}
    @Test fun roundsExhaustCandidatesWithoutRepeatingPoems(){val qs=Poetry.questions(listOf(Poetry.Candidate("a",poem()),Poetry.Candidate("b",poem())),"随机句",20,3,"诗文不重复",Random(9));assertEquals(6,qs.size);assertEquals(qs.size,qs.map{it.id}.distinct().size);qs.groupBy{it.round}.values.forEach{assertEquals(2,it.map{it.key}.distinct().size)}}
    @Test fun zeroWeightsDoNotProduceQuestions(){assertTrue(Poetry.questions(listOf(Poetry.Candidate("a",poem(),0)),"整篇",5,1,"允许重复").isEmpty())}
    @Test fun randomLineQuestionsCanRepeatPoemButNotQuestion(){val p=poem();val qs=Poetry.questions(listOf(Poetry.Candidate("a",p)),"随机句",100,1,"题目不重复");assertEquals(Poetry.lines(p).size,qs.size);assertEquals(qs.size,qs.map{it.lineId}.distinct().size)}
    @Test fun snapshotsAreIndependent(){val p=poem();val q=Poetry.questions(listOf(Poetry.Candidate("a",p)),"整篇",1,1,"允许重复").single();val title=q.poem.s("title");p.addProperty("title","changed");assertEquals(title,q.poem.s("title"))}
    @Test fun lastLineHintAndMissingAuxiliaryAreExplicit(){val p=poem();assertEquals("已到末句",Poetry.hint(p,Poetry.lines(p).lastIndex,"下一句"));Poetry.lines(p).first().remove("pinyin");assertTrue(Poetry.hint(p,0,"拼音").contains("未提供"))}
    @Test fun nestedFilterGroupsHaveRealAndOrSemantics(){val fields=mapOf("author" to "李白","dynasty" to "唐","words" to "20");assertTrue(Poetry.FilterGroup(true,listOf(Poetry.Condition("author","杜甫"),Poetry.Condition("dynasty","唐"))).matches(fields));assertFalse(Poetry.FilterGroup(false,listOf(Poetry.Condition("author","杜甫"),Poetry.Condition("dynasty","唐"))).matches(fields));assertTrue(Poetry.Condition("minWords","20").matches(fields));assertFalse(Poetry.Condition("maxWords","19").matches(fields))}
    @Test fun continuationExcludesSingleLinePoems(){val p=JsonParser.parseString("""{"stanzas":[{"id":"s","lines":[{"id":"l","original":"天"}]}]}""").asJsonObject;assertTrue(Poetry.questions(listOf(Poetry.Candidate("a",p)),"接续",5,1,"允许重复").isEmpty())}
}
