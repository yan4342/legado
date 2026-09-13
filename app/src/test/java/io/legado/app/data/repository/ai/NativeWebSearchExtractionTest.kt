package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.AiBuiltinTool
import io.legado.app.domain.usecase.parseWebSearchData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for the native (provider built-in) web_search pipeline:
 * Responses-item extraction, compact-payload parsing, and builtin-tool request mapping.
 */
class NativeWebSearchExtractionTest {

    private fun obj(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    // ---- extractSearchQueries ----

    @Test
    fun `search_queries texts are returned in full`() {
        val item = obj("""{"search_queries":[{"text":"legado"},{"text":"阅读 3.0"}]}""")
        assertEquals(listOf("legado", "阅读 3.0"), extractSearchQueries(item))
    }

    @Test
    fun `action query string fallback`() {
        val item = obj("""{"action":{"type":"search","query":"fallback q"}}""")
        assertEquals(listOf("fallback q"), extractSearchQueries(item))
    }

    @Test
    fun `action queries array fallback`() {
        val item = obj("""{"action":{"queries":["a","b"]}}""")
        assertEquals(listOf("a", "b"), extractSearchQueries(item))
    }

    @Test
    fun `no queries yields empty list`() {
        assertEquals(emptyList<String>(), extractSearchQueries(obj("{}")))
    }

    // ---- extractSearchSources ----

    @Test
    fun `sources keep title url and first date field`() {
        val item = obj(
            """{"sources":[
                {"title":"A","url":"https:\/\/a.test","page_age":"2 days ago"},
                {"title":"B","url":"https:\/\/b.test","published_date":"2026-02-02"}]}""",
        )
        val sources = extractSearchSources(item)
        assertEquals(2, sources.size)
        assertEquals("https://a.test", sources[0]["url"])
        assertEquals("2 days ago", sources[0]["publishedAt"])
        assertEquals("2026-02-02", sources[1]["publishedAt"])
    }

    @Test
    fun `blank urls and non objects are dropped`() {
        val item = obj("""{"sources":[{"url":""},{"title":"X"},"skipped"]}""")
        assertTrue(extractSearchSources(item).isEmpty())
    }

    // ---- extractUrlCitations ----

    @Test
    fun `url_citation annotations are collected deduped by url`() {
        val message = obj(
            """{"type":"message","content":[
                {"type":"output_text","text":"answer","annotations":[
                    {"type":"url_citation","url":"https:\/\/a.test","title":"A"},
                    {"type":"url_citation","url":"https:\/\/b.test","title":"B"},
                    {"type":"url_citation","url":"https:\/\/a.test","title":"A2"}]},
                {"type":"refusal","refusal":"nope"}]}""",
        )
        val citations = extractUrlCitations(message)
        assertEquals(setOf("https://a.test", "https://b.test"), citations.keys)
        assertEquals("A", citations["https://a.test"]?.get("title"))
    }

    // ---- extractWebSearchData roundtrip ----

    @Test
    fun `compact payload carries queries and dated sources`() {
        val item = obj(
            """{"search_queries":[{"text":"q1"},{"text":"q2"}],"sources":[
                {"title":"T","url":"https:\/\/t.test","page_age":"yesterday"}]}""",
        )
        val data = parseWebSearchData(extractWebSearchData(item))
        assertEquals(listOf("q1", "q2"), data?.queries)
        assertEquals("yesterday", data?.sources?.single()?.publishedAt)
    }

    @Test
    fun `item without queries or sources produces null payload`() {
        assertNull(extractWebSearchData(obj("""{"id":"ws_1"}""")))
    }

    // ---- parseWebSearchData robustness ----

    @Test
    fun `legacy query-only payloads still parse`() {
        val data = parseWebSearchData("""{"query":"legacy","sources":[]}""")
        assertEquals(listOf("legacy"), data?.queries)
    }

    @Test
    fun `malformed payloads return null`() {
        assertNull(parseWebSearchData(null))
        assertNull(parseWebSearchData(""))
        assertNull(parseWebSearchData("not json"))
        assertNull(parseWebSearchData("[1,2]"))
    }

    // ---- builtin tool mapping ----

    @Test
    fun `default builtin tool maps to bare type object`() {
        assertEquals(mapOf("type" to "web_search"), AiBuiltinTool("web_search").toResponsesTool())
    }

    @Test
    fun `configured params merge into the tool object`() {
        val tool = AiBuiltinTool(
            type = "web_search",
            params = mapOf(
                "max_uses" to 5,
                "search_context_size" to "high",
                "filters" to mapOf("allowed_domains" to listOf("a.test")),
            ),
        )
        val mapped = tool.toResponsesTool()
        assertEquals("web_search", mapped["type"])
        assertEquals(5, mapped["max_uses"])
        assertEquals("high", mapped["search_context_size"])
    }
}
