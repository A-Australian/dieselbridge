// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.protocol

import org.json.JSONArray
import org.json.JSONObject

/** A parsed inbound Gadgetbridge / Bangle.js message (see docs/ble-protocol.md). */
sealed interface GbMessage {
    data class Notify(
        val id: Long,
        val src: String?,
        val title: String?,
        val subject: String?,
        val body: String?,
        val sender: String?,
        /** `reply:true` (Gadgetbridge 0.80+) — the source notification has a RemoteInput. */
        val replyable: Boolean = false,
    ) : GbMessage

    data class NotifyDelete(val id: Long) : GbMessage

    /** `{"t":"find","n":<bool>}` — phone asked us to start/stop the find-my-watch alert. */
    data class Find(val active: Boolean) : GbMessage

    /** `{"t":"vibrate","n":<n>}` — phone asked for a single buzz. */
    data class Vibrate(val n: Int) : GbMessage

    /** `{"t":"call","cmd":<cmd>,"name":?,"number":?}` — incoming/outgoing call state change. */
    data class Call(val cmd: String, val name: String?, val number: String?) : GbMessage

    /** `{"t":"musicinfo",…}` — now-playing track metadata. */
    data class MusicInfo(
        val artist: String?,
        val album: String?,
        val track: String?,
        val durMs: Int,
        val trackCount: Int,
        val trackNr: Int,
    ) : GbMessage

    /** `{"t":"musicstate",…}` — playback state (play/pause), position, shuffle/repeat. */
    data class MusicState(val state: String, val position: Int, val shuffle: Int, val repeat: Int) : GbMessage

    /** `{"t":"canned_responses_sync","d":[{"text":…}]}` — reply suggestions from the phone. */
    data class CannedResponses(val list: List<String>) : GbMessage

    /** Any other `t` we don't handle yet (call, musicinfo, …). */
    data class Other(val type: String) : GbMessage
}

/**
 * Parses one inbound line from Gadgetbridge's Bangle.js JSON-over-NUS dialect.
 *
 * The line arrives as `GB({...})` (the leading 0x10 DLE byte is already stripped by
 * [org.aaustralian.dieselbridge.ble.NusGattServer]), or occasionally as raw `{...}`. Uses the built-in
 * `org.json` parser — no extra dependency.
 */
object GbProtocol {

    fun parseLine(line: String): GbMessage? {
        val json = extractJson(line) ?: return null
        return runCatching {
            val o = JSONObject(json)
            when (o.optString("t")) {
                "notify" -> GbMessage.Notify(
                    id = o.optLong("id"),
                    src = o.stringOrNull("src"),
                    title = o.stringOrNull("title"),
                    subject = o.stringOrNull("subject"),
                    body = o.stringOrNull("body"),
                    sender = o.stringOrNull("sender"),
                    replyable = o.optBoolean("reply"),
                )
                "notify-" -> GbMessage.NotifyDelete(o.optLong("id"))
                "find" -> GbMessage.Find(o.optBoolean("n"))
                "vibrate" -> GbMessage.Vibrate(o.optInt("n"))
                "call" -> GbMessage.Call(o.optString("cmd"), o.stringOrNull("name"), o.stringOrNull("number"))
                "musicinfo" -> GbMessage.MusicInfo(
                    o.stringOrNull("artist"),
                    o.stringOrNull("album"),
                    o.stringOrNull("track"),
                    o.optInt("dur"),
                    o.optInt("c"),
                    o.optInt("n"),
                )
                "musicstate" -> GbMessage.MusicState(
                    o.optString("state"),
                    o.optInt("position"),
                    o.optInt("shuffle"),
                    o.optInt("repeat"),
                )
                "canned_responses_sync" -> GbMessage.CannedResponses(parseCanned(o))
                else -> GbMessage.Other(o.optString("t"))
            }
        }.getOrNull()
    }

    /**
     * Encodes a watch->phone action (Bangle.js dialect): `{"t":"notify","id":<id>,"n":"<action>"}`
     * with an optional reply string. Gadgetbridge (BangleJSDeviceSupport.handleNotificationControl)
     * reads the action from `n`, the notification id from `id`, and the **reply text from `msg`**
     * (NOT `reply`); for REPLY it maps `id` -> the stored RemoteInput handle. Sent as raw
     * newline-terminated JSON (no GB()/0x10). See docs/ble-protocol.md.
     */
    fun encodeAction(id: Long, action: String, reply: String? = null): String =
        JSONObject().apply {
            put("t", "notify")
            put("id", id)
            put("n", action)
            if (reply != null) put("msg", reply)
        }.toString()

    /** Encodes a watch->phone battery status: `{"t":"status","bat":<pct>,"volt":<v>,"chg":<0|1>}`. */
    fun encodeStatus(bat: Int, volt: Double, chg: Int): String =
        JSONObject().apply {
            put("t", "status")
            put("bat", bat)
            put("volt", if (volt.isFinite()) volt else 0.0)
            put("chg", chg)
        }.toString()

    /** Encodes a watch->phone version report: `{"t":"ver","fw":"<fw>","hw":"<hw>"}`. */
    fun encodeVer(fw: String, hw: String): String =
        JSONObject().apply {
            put("t", "ver")
            put("fw", fw)
            put("hw", hw)
        }.toString()

    /** Encodes a watch->phone find-my-phone request: `{"t":"findPhone","n":<bool>}`. */
    fun encodeFindPhone(active: Boolean): String =
        JSONObject().apply {
            put("t", "findPhone")
            put("n", active)
        }.toString()

    /** Encodes a watch->phone call action: `{"t":"call","n":"<action>"}` (ACCEPT/REJECT/…). */
    fun encodeCall(action: String): String =
        JSONObject().apply {
            put("t", "call")
            put("n", action)
        }.toString()

    /** Encodes a watch->phone music control: `{"t":"music","n":"<cmd>"}` (play/pause/next/…). */
    fun encodeMusic(cmd: String): String =
        JSONObject().apply {
            put("t", "music")
            put("n", cmd)
        }.toString()

    /**
     * Encodes a watch->phone activity sample: `{"t":"act","ts":<ms>,"hrm":<bpm>,"stp":<steps>,"rt":true}`.
     * `hrm`/`stp` are omitted when unavailable (sensor not present, not on wrist, etc.) rather than
     * sent as 0, since 0 has a specific meaning downstream (Gadgetbridge shows 0 bpm as "no reading").
     * `rt` marks the sample as realtime/live, matching the Bangle.js protocol's live-data semantics
     * (see docs/ble-protocol.md) — Gadgetbridge does not persist `rt` samples to its activity DB.
     */
    fun encodeActivity(
        timestampMs: Long,
        heartRateBpm: Int? = null,
        steps: Int? = null,
        realtime: Boolean = true,
    ): String =
        JSONObject().apply {
            put("t", "act")
            put("ts", timestampMs)
            heartRateBpm?.let { put("hrm", it) }
            steps?.let { put("stp", it) }
            put("rt", realtime)
        }.toString()

    private fun parseCanned(o: JSONObject): List<String> {
        val d: JSONArray = o.optJSONArray("d") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until d.length()) {
            val entry = d.optJSONObject(i) ?: continue
            entry.stringOrNull("text")?.let { out.add(it) }
        }
        return out
    }

    private fun extractJson(line: String): String? {
        val s = line.trim()
        return when {
            s.startsWith("GB(") && s.endsWith(")") -> s.substring(3, s.length - 1)
            s.startsWith("{") -> s
            else -> null
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }
}
