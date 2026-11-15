package com.example.pleostest

/**
 * 의존성 없는 최소 JSON 파서 (라벨 JSON 읽기 전용).
 * Android org.json 은 JVM 단위 테스트에서 동작하지 않으므로, 순수 Kotlin 으로 구현해
 * 앱과 단위 테스트가 같은 코드로 라벨을 읽습니다.
 * 반환 타입: Map<String, Any?> / List<Any?> / Double / String / Boolean / null
 */
object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.value()
        p.skipWs()
        require(p.i == text.length) { "JSON 끝에 불필요한 문자: ${p.i}" }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    fun arr(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()
    fun num(value: Any?, default: Double = 0.0): Double = (value as? Double) ?: default
    fun str(value: Any?, default: String = ""): String = (value as? String) ?: default

    private class Parser(val s: String) {
        var i = 0
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            skipWs()
            require(i < s.length) { "JSON 이 비어 있습니다" }
            return when (val c = s[i]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) numberValue() else error("예상치 못한 문자 '$c' @$i")
            }
        }
        private fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, i)) { "잘못된 값 @$i" }
            i += word.length
            return result
        }
        private fun objectValue(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++; skipWs()
            if (s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = stringValue()
                skipWs(); require(s[i] == ':') { "':' 필요 @$i" }; i++
                out[key] = value()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> error("',' 또는 '}' 필요 @$i")
                }
            }
        }
        private fun arrayValue(): List<Any?> {
            val out = ArrayList<Any?>()
            i++; skipWs()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out.add(value())
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> error("',' 또는 ']' 필요 @$i")
                }
            }
        }
        private fun stringValue(): String {
            require(s[i] == '"') { "문자열 필요 @$i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> error("잘못된 escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun numberValue(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(start, i).toDouble()
        }
    }

    /** JSON 문자열 이스케이프 (AI 요청 본문 생성용) */
    fun quote(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }
}
