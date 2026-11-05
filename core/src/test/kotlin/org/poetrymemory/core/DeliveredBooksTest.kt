package org.poetrymemory.core
import org.junit.Assert.*
import org.junit.Test
class DeliveredBooksTest {
    @Test fun bundledTemplatesAndExamplesRemainCompatible(){
        listOf("poetry-template","poetry-example","novel-template","novel-example").forEach{name->
            val result=Protocol.parse(javaClass.getResource("/protocol/$name.json")!!.readText(),name)
            assertTrue(result.errors.joinToString{it.message},result.valid)
            assertTrue(result.warnings.isEmpty())
        }
    }
}
