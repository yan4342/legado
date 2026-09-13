package io.legado.app.domain.usecase

import kotlin.collections.ArrayDeque

/**
 * Aho-Corasick 多模式匹配器。
 * 一次遍历文本即可找出所有已注册 pattern 的全部出现位置，
 * 复杂度 O(文本长度 + 命中数)，与 pattern 数量无关。
 *
 * 用法：
 * ```
 * val matcher = AhoCorasick()
 * matcher.addPattern("foo", tag = 0)
 * matcher.addPattern("bar", tag = 1)
 * matcher.build()
 * matcher.scan("foo and bar") // -> [(0, 0), (1, 8)]
 * ```
 *
 * [tag] 由调用方定义（如 term 索引），scan 结果携带 tag 便于归并。
 * 匹配是大小写敏感的；调用方负责对文本做小写化并保证长度不变。
 */
class AhoCorasick {

    private class Node {
        val next = mutableMapOf<Char, Node>()
        var fail: Node? = null
        val outputs = mutableListOf<Int>()
    }

    private val root = Node()
    private val tags = mutableListOf<Int>()
    private val patternLengths = mutableListOf<Int>()
    private var built = false

    /** 注册一个 pattern，返回其索引；[tag] 供 scan 结果归并使用。 */
    fun addPattern(pattern: String, tag: Int): Int {
        require(pattern.isNotEmpty()) { "pattern must not be empty" }
        val index = tags.size
        var node = root
        for (c in pattern) {
            node = node.next.getOrPut(c) { Node() }
        }
        node.outputs.add(index)
        tags.add(tag)
        patternLengths.add(pattern.length)
        return index
    }

    /** 构建 fail 指针。所有 pattern 注册完毕后调用一次。 */
    fun build() {
        val queue = ArrayDeque<Node>()
        for (child in root.next.values) {
            child.fail = root
            queue.addLast(child)
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for ((c, child) in current.next) {
                var fail = current.fail
                while (fail != null && fail.next[c] == null) {
                    fail = fail.fail
                }
                child.fail = fail?.next?.get(c) ?: root
                child.outputs.addAll(child.fail!!.outputs)
                queue.addLast(child)
            }
        }
        built = true
    }

    /** 单遍扫描文本，返回 (tag, 起始偏移) 列表，顺序按文本位置升序。 */
    fun scan(text: String): List<Pair<Int, Int>> {
        check(built) { "must call build() before scan()" }
        val result = mutableListOf<Pair<Int, Int>>()
        var node = root
        for (i in text.indices) {
            val c = text[i]
            while (node !== root && node.next[c] == null) {
                node = node.fail ?: root
            }
            node = node.next[c] ?: root
            for (output in node.outputs) {
                result.add(tags[output] to (i - patternLengths[output] + 1))
            }
        }
        return result
    }
}
