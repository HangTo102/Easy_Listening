package com.easylistening.app.ui

/**
 * 清理释义文本，便于中文朗读：
 * 去掉 [医] 之类的括号标注，以及开头的词性缩写（vt. / n. / adj. ...）。
 */
fun cleanForSpeech(text: String): String {
    var t = text.replace(Regex("\\[[^\\]]*\\]"), " ")
    t = t.replace(
        Regex(
            "^\\s*(n|vt|vi|v|adj|adv|prep|pron|conj|num|art|int|abbr|aux)\\.\\s*",
            RegexOption.IGNORE_CASE
        ),
        ""
    )
    return t.trim().replace(Regex("\\s+"), " ")
}
