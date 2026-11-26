// 松鼠兽牌 PRO 网页兽牌版本
// Copyright (C) 2026  laofang
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.laofang.songshushoupaipro.songshu.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 网络检测：TCP 连接测延迟（App 内无法发原始 ICMP 包，用 TCP 握手耗时近似 ping）。
 * 设置页「浏览器检测」和 MCP 的 net_ping 工具共用。
 */
object NetProbe {

    data class PingResult(val host: String, val port: Int, val ok: Boolean, val ms: Long, val error: String = "")

    /** 单目标 TCP ping。 */
    fun ping(host: String, port: Int, timeoutMs: Int = 3000): PingResult {
        val h = host.trim()
        if (h.isBlank()) return PingResult(h, port, false, 0, "地址为空")
        return try {
            val start = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress(h, port), timeoutMs)
            val ms = System.currentTimeMillis() - start
            try { s.close() } catch (_: Exception) {}
            PingResult(h, port, true, ms)
        } catch (e: Exception) {
            PingResult(h, port, false, 0, e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    /** 默认检测组：网关旁路、公网连通、常用站点。 */
    fun pingDefaults(): List<PingResult> = listOf(
        ping("223.5.5.5", 53),          // 阿里 DNS
        ping("119.29.29.29", 53),       // 腾讯 DNS
        ping("www.baidu.com", 443),
        ping("connect.rom.miui.com", 80) // 常用连通性探测点
    )

    fun toJson(results: List<PingResult>): JSONObject {
        val arr = JSONArray()
        results.forEach { r ->
            arr.put(JSONObject().apply {
                put("host", r.host)
                put("port", r.port)
                put("ok", r.ok)
                if (r.ok) put("ms", r.ms) else put("error", r.error)
            })
        }
        val okCount = results.count { it.ok }
        return JSONObject().apply {
            put("results", arr)
            put("reachable", okCount)
            put("total", results.size)
        }
    }
}
