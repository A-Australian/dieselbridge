// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.aaustralian.dieselbridge.notify.NotificationRouter
import org.aaustralian.dieselbridge.notify.WatchNotifier

/**
 * DEBUG-ONLY: injects a simulated notification straight into the real pipeline
 * ([NotificationRouter]) — no phone, Gadgetbridge, or BLE needed. Ideal for screenshots and UI tests
 * on the **emulator**, which has no Bluetooth. Only compiled into debug builds (src/debug).
 *
 * Convenience form:
 *   adb shell am broadcast -a org.aaustralian.dieselbridge.INJECT \
 *     -n org.aaustralian.dieselbridge/.debug.DebugInjectReceiver \
 *     --es app Signal --es title Alice --es body "Coffee?" --el id 42
 *
 * Raw Bangle.js line:  --es line 'GB({"t":"notify","id":7,"src":"X","title":"Hi","body":"yo"})'
 * Dismiss:             --el del 42
 * Incoming call:       --es call incoming --es name Alice --es number 555-1234
 * Canned replies:      --es canned "On my way,Call you later,OK"
 */
class DebugInjectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = when {
            intent.getStringExtra("line") != null -> intent.getStringExtra("line")!!
            intent.hasExtra("del") -> JSONObject().apply {
                put("t", "notify-")
                put("id", intent.getLongExtra("del", 0L))
            }.toString()
            intent.getStringExtra("call") != null -> JSONObject().apply {
                put("t", "call")
                put("cmd", intent.getStringExtra("call")!!)
                intent.getStringExtra("name")?.let { put("name", it) }
                intent.getStringExtra("number")?.let { put("number", it) }
            }.toString()
            intent.hasExtra("canned") -> JSONObject().apply {
                put("t", "canned_responses_sync")
                put("d", JSONArray().apply {
                    intent.getStringExtra("canned").orEmpty().split(",").forEach { text ->
                        put(JSONObject().apply { put("text", text) })
                    }
                })
            }.toString()
            else -> JSONObject().apply {
                put("t", "notify")
                put("id", intent.getLongExtra("id", System.currentTimeMillis()))
                intent.getStringExtra("app")?.let { put("src", it) }
                intent.getStringExtra("title")?.let { put("title", it) }
                intent.getStringExtra("body")?.let { put("body", it) }
            }.toString()
        }
        Log.i(TAG, "inject: $line")
        NotificationRouter(context, WatchNotifier(context)).handle(line)
    }

    private companion object {
        const val TAG = "DebugInject"
    }
}
