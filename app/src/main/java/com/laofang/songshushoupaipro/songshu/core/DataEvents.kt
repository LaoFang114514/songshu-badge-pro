package com.laofang.songshushoupaipro.songshu.core

import kotlinx.coroutines.flow.MutableStateFlow

/** 数据变更信号：MCP 等后台修改兽牌后通知界面刷新。 */
object DataEvents {
    val revision = MutableStateFlow(0)

    /** 每个兽牌最近一次被 MCP 变更工具写入的时间戳（epoch ms）。 */
    private val lastEdit = HashMap<String, Long>()

    /** 编辑保持窗口：窗口期内的兽牌视为"MCP 服务编辑中"。 */
    const val EDITING_WINDOW_MS = 5000L

    fun bump() {
        revision.value += 1
    }

    fun markEditing(badgeId: String) {
        synchronized(lastEdit) { lastEdit[badgeId] = System.currentTimeMillis() }
    }

    /** 该兽牌是否处于 MCP 编辑窗口内。 */
    fun isEditing(badgeId: String): Boolean {
        val t = synchronized(lastEdit) { lastEdit[badgeId] } ?: return false
        return System.currentTimeMillis() - t < EDITING_WINDOW_MS
    }
}