package com.doubao.helper.wakeup

import android.util.Log

/**
 * 将中文转换为 sherpa-onnx KWS 模型所需的拼音 token 序列。
 *
 * 模型 token 体系：声母(如 x, d, sh) + 韵母(带声调, 如 iǎo, òu, āng)
 * 关键词格式：声母1 韵母1 声母2 韵母2 @显示名
 *
 * 例如 "小豆小豆" → "x iǎo d òu x iǎo d òu @小豆小豆"
 */
object PinyinTokenizer {

    /**
     * 常用汉字到带调拼音 token 的映射。
     * 格式：汉字 → "声母 韵母"（韵母带声调符号，空格分隔）
     */
    private val charToPinyin: Map<String, String> = mapOf(
        // 小豆相关
        "小" to "x iǎo",
        "豆" to "d òu",

        // 常用唤醒词字
        "你" to "n ǐ",
        "好" to "h ǎo",
        "阿" to "ā",
        "里" to "l ǐ",
        "巴" to "b ā",
        "天" to "t iān",
        "猫" to "m āo",
        "京" to "j īng",
        "东" to "d ōng",
        "爱" to "ài",
        "同" to "t óng",
        "学" to "x ué",
        "大" to "d à",
        "明" to "m íng",
        "宝" to "b ǎo",
        "贝" to "b èi",
        "嘿" to "h ēi",
        "嗨" to "h āi",
        "雅" to "y ǎ",
        "娜" to "n à",
        "娃" to "w á",
        "哈" to "h ā",
        "乔" to "q iáo",
        "布" to "b ù",
        "斯" to "s ī",

        // 数字
        "一" to "y ī",
        "二" to "è r",
        "三" to "s ān",
        "四" to "s ì",
        "五" to "w ǔ",
        "六" to "l iù",
        "七" to "q ī",
        "八" to "b ā",
        "九" to "j iǔ",
        "零" to "l íng",

        // 常用字
        "来" to "l ái",
        "了" to "l e",
        "在" to "z ài",
        "我" to "w ǒ",
        "的" to "d e",
        "是" to "sh ì",
        "不" to "b ù",
        "和" to "h é",
        "有" to "y ǒu",
        "人" to "r én",
        "这" to "zh è",
        "中" to "zh ōng",
        "他" to "t ā",
        "她" to "t ā",
        "它" to "t ā",
        "为" to "w èi",
        "上" to "sh àng",
        "个" to "g è",
        "国" to "g uó",
        "们" to "m en",
        "到" to "d ào",
        "说" to "sh uō",
        "时" to "sh í",
        "地" to "d ì",
        "也" to "y ě",
        "子" to "z ǐ",
        "得" to "d é",
        "去" to "q ù",
        "过" to "g uò",
        "发" to "f ā",
        "当" to "d āng",
        "没" to "m éi",
        "成" to "ch éng",
        "方" to "f āng",
        "多" to "d uō",
        "可" to "k ě",
        "对" to "d uì",
        "开" to "k āi",
        "能" to "n éng",
        "自" to "z ì",
        "心" to "x īn",
        "前" to "q ián",
        "所" to "s uǒ",
        "家" to "j iā",
        "只" to "zh ǐ",
        "想" to "x iǎng",
        "看" to "k àn",
        "生" to "sh ēng",
        "门" to "m én",
        "着" to "zh e",
        "长" to "zh ǎng",
        "与" to "y ǔ",
        "话" to "h uà",
        "叫" to "j iào",
        "起" to "q ǐ",
        "醒" to "x ǐng",
        "听" to "t īng",
        "见" to "j iàn",
        "请" to "q ǐng",
        "回" to "h uí",
        "答" to "d á",
        "知" to "zh ī",
        "道" to "d ào",
        "做" to "z uò",
        "给" to "g ěi",
        "让" to "r àng",
        "帮" to "b āng",
        "忙" to "m áng",
        "啦" to "l a",
        "啊" to "a",
        "吧" to "b a",
        "呢" to "n e",
        "吗" to "m a",
        "哦" to "ó",
        "嗯" to "èn",
        "喂" to "w èi",
        "哎" to "āi",
        "咿" to "y ī",

        // 动物
        "狗" to "g ǒu",
        "鱼" to "y ú",
        "鸟" to "n iǎo",
        "龙" to "l óng",
        "虎" to "h ǔ",
        "兔" to "t ù",
        "蛇" to "sh é",
        "马" to "m ǎ",
        "羊" to "y áng",
        "猴" to "h óu",
        "鸡" to "j ī",
        "猪" to "zh ū",
        "牛" to "n iú",

        // 科技/品牌相关
        "微" to "w ēi",
        "软" to "r uǎn",
        "谷" to "g ǔ",
        "歌" to "g ē",
        "苹" to "p íng",
        "果" to "g uǒ",
        "华" to "h uá",
        "百" to "b ǎi",
        "度" to "d ù",
        "腾" to "t éng",
        "讯" to "x ùn",
        "字" to "z ì",
        "节" to "j ié",
        "跳" to "t iào",
        "动" to "d òng",
        "网" to "w ǎng",
        "易" to "y ì",
        "拼" to "p īn",
        "快" to "k uài",
        "手" to "sh ǒu",
        "滴" to "d ī",
        "美" to "m ěi",
        "团" to "t uán",
        "饿" to "è",
        "么" to "m e",
        "智" to "zh ì",
        "助" to "zh ù",
        "精" to "j īng",
        "灵" to "l íng",
        "管" to "g uǎn",
        "红" to "h óng",
        "绿" to "l ǜ",
        "蓝" to "l án",
        "白" to "b ái",
        "黑" to "h ēi",
        "黄" to "h uáng",
        "紫" to "z ǐ",
    )

    /**
     * 将中文文本转换为 sherpa-onnx KWS 关键词格式。
     *
     * 输入: "小豆小豆"
     * 输出: "x iǎo d òu x iǎo d òu @小豆小豆"
     *
     * 如果遇到未映射的字符，跳过该字符并记录警告。
     */
    fun toKeywordTokens(text: String): String {
        val tokenParts = mutableListOf<String>()

        for (char in text) {
            if (char.isWhitespace()) continue
            val key = char.toString()
            val pinyin = charToPinyin[key]
            if (pinyin != null) {
                tokenParts.add(pinyin)
            } else {
                Log.w(TAG, "未映射的字符: $key，已跳过")
            }
        }

        if (tokenParts.isEmpty()) {
            Log.e(TAG, "关键词文本无法转换为拼音 token: $text")
            return ""
        }

        return "${tokenParts.joinToString(" ")} @$text"
    }

    private const val TAG = "PinyinTokenizer"
}
