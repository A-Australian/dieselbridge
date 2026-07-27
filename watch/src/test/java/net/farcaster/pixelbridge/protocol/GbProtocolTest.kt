// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbProtocolTest {

    @Test
    fun parsesGbWrappedNotify() {
        val msg = GbProtocol.parseLine(
            """GB({"t":"notify","id":42,"src":"Signal","title":"Alice","body":"hi"})""",
        )
        assertTrue(msg is GbMessage.Notify)
        msg as GbMessage.Notify
        assertEquals(42L, msg.id)
        assertEquals("Signal", msg.src)
        assertEquals("Alice", msg.title)
        assertEquals("hi", msg.body)
    }

    @Test
    fun parsesRawJsonNotify() {
        assertTrue(GbProtocol.parseLine("""{"t":"notify","id":1,"title":"X"}""") is GbMessage.Notify)
    }

    @Test
    fun parsesNotifyDelete() {
        assertEquals(GbMessage.NotifyDelete(7L), GbProtocol.parseLine("""GB({"t":"notify-","id":7})"""))
    }

    @Test
    fun unknownTypeIsOther() {
        assertEquals(GbMessage.Other("is_gps_active"), GbProtocol.parseLine("""{"t":"is_gps_active"}"""))
    }

    @Test
    fun nonJsonReturnsNull() {
        assertNull(GbProtocol.parseLine("setTime(123);E.setTimeZone(2.0)"))
        assertNull(GbProtocol.parseLine(""))
    }

    @Test
    fun emptyStringFieldsBecomeNull() {
        val msg = GbProtocol.parseLine(
            """{"t":"notify","id":1,"title":"T","subject":"","sender":""}""",
        ) as GbMessage.Notify
        assertEquals("T", msg.title)
        assertNull(msg.subject)
        assertNull(msg.sender)
    }

    @Test
    fun notifyReplyTrueMarksReplyable() {
        val msg = GbProtocol.parseLine(
            """{"t":"notify","id":1,"title":"Alice","body":"hi","reply":true}""",
        ) as GbMessage.Notify
        assertTrue(msg.replyable)
    }

    @Test
    fun notifyWithoutReplyIsNotReplyable() {
        val msg = GbProtocol.parseLine("""{"t":"notify","id":1,"title":"X"}""") as GbMessage.Notify
        assertFalse(msg.replyable)
    }

    @Test
    fun encodeDismissHasNoMsg() {
        val json = JSONObject(GbProtocol.encodeAction(42L, "DISMISS"))
        assertEquals("notify", json.getString("t"))
        assertEquals(42L, json.getLong("id"))
        assertEquals("DISMISS", json.getString("n"))
        assertFalse(json.has("msg"))
    }

    @Test
    fun encodeReplyUsesMsgFieldNotReply() {
        // Regression guard: Gadgetbridge reads the reply text from "msg" (handleNotificationControl).
        val json = JSONObject(GbProtocol.encodeAction(42L, "REPLY", "hello"))
        assertEquals("REPLY", json.getString("n"))
        assertEquals("hello", json.getString("msg"))
        assertFalse(json.has("reply"))
    }

    @Test
    fun parsesFindStart() {
        assertEquals(GbMessage.Find(true), GbProtocol.parseLine("""GB({"t":"find","n":true})"""))
    }

    @Test
    fun parsesFindStop() {
        assertEquals(GbMessage.Find(false), GbProtocol.parseLine("""GB({"t":"find","n":false})"""))
    }

    @Test
    fun parsesVibrate() {
        assertEquals(GbMessage.Vibrate(100), GbProtocol.parseLine("""GB({"t":"vibrate","n":100})"""))
    }

    @Test
    fun encodeStatusRoundTrips() {
        val json = JSONObject(GbProtocol.encodeStatus(87, 3.912, 1))
        assertEquals("status", json.getString("t"))
        assertEquals(87, json.getInt("bat"))
        assertTrue(json.get("chg") is Int)
        assertEquals(1, json.getInt("chg"))
        assertEquals(3.912, json.getDouble("volt"), 1e-6)
    }

    @Test
    fun encodeVerHasFwHw() {
        val json = JSONObject(GbProtocol.encodeVer("0.1.1", "Pixel Watch"))
        assertEquals("ver", json.getString("t"))
        assertEquals("0.1.1", json.getString("fw"))
        assertEquals("Pixel Watch", json.getString("hw"))
    }

    @Test
    fun encodeFindPhoneUsesBooleanN() {
        val on = JSONObject(GbProtocol.encodeFindPhone(true))
        assertEquals("findPhone", on.getString("t"))
        assertTrue(on.get("n") is Boolean)
        assertTrue(on.getBoolean("n"))

        val off = JSONObject(GbProtocol.encodeFindPhone(false))
        assertTrue(off.get("n") is Boolean)
        assertFalse(off.getBoolean("n"))
    }

    @Test
    fun parsesCallIncoming() {
        val msg = GbProtocol.parseLine(
            """GB({"t":"call","cmd":"incoming","name":"Alice","number":"+15551234"})""",
        )
        assertTrue(msg is GbMessage.Call)
        msg as GbMessage.Call
        assertEquals("incoming", msg.cmd)
        assertEquals("Alice", msg.name)
        assertEquals("+15551234", msg.number)
    }

    @Test
    fun parsesCallEmptyBecomesNull() {
        val msg = GbProtocol.parseLine(
            """{"t":"call","cmd":"end","name":"","number":""}""",
        ) as GbMessage.Call
        assertEquals("end", msg.cmd)
        assertNull(msg.name)
        assertNull(msg.number)
    }

    @Test
    fun encodeCallAcceptUsesN() {
        val json = JSONObject(GbProtocol.encodeCall("ACCEPT"))
        assertEquals("call", json.getString("t"))
        assertEquals("ACCEPT", json.getString("n"))
    }

    @Test
    fun parsesMusicInfo() {
        val msg = GbProtocol.parseLine(
            """GB({"t":"musicinfo","artist":"Radiohead","album":"OK Computer","track":"Airbag","dur":284,"c":12,"n":1})""",
        ) as GbMessage.MusicInfo
        assertEquals("Radiohead", msg.artist)
        assertEquals("OK Computer", msg.album)
        assertEquals("Airbag", msg.track)
        assertEquals(284, msg.durMs)
        assertEquals(12, msg.trackCount)
        assertEquals(1, msg.trackNr)
    }

    @Test
    fun parsesMusicState() {
        val msg = GbProtocol.parseLine(
            """GB({"t":"musicstate","state":"play","position":42,"shuffle":1,"repeat":2})""",
        ) as GbMessage.MusicState
        assertEquals("play", msg.state)
        assertEquals(42, msg.position)
        assertEquals(1, msg.shuffle)
        assertEquals(2, msg.repeat)
    }

    @Test
    fun encodeMusicNextRoundTrips() {
        val json = JSONObject(GbProtocol.encodeMusic("next"))
        assertEquals("music", json.getString("t"))
        assertEquals("next", json.getString("n"))
    }

    @Test
    fun encodeMusicIsLowercase() {
        val n = JSONObject(GbProtocol.encodeMusic("previous")).getString("n")
        assertEquals(n.lowercase(), n)
        assertEquals("previous", n)
    }

    @Test
    fun parsesCannedResponsesSync() {
        val msg = GbProtocol.parseLine(
            """GB({"t":"canned_responses_sync","type":"generic","d":[{"text":"OK","disp":"OK"},{"text":"On my way","disp":"On my way"}]})""",
        ) as GbMessage.CannedResponses
        assertEquals(listOf("OK", "On my way"), msg.list)
    }

    @Test
    fun cannedResponsesEmptyWhenNoD() {
        val msg = GbProtocol.parseLine("""{"t":"canned_responses_sync"}""") as GbMessage.CannedResponses
        assertTrue(msg.list.isEmpty())
    }

    @Test
    fun cannedResponsesSkipsBlank() {
        val msg = GbProtocol.parseLine(
            """{"t":"canned_responses_sync","d":[{"text":"Yes"},{"text":""},{"disp":"no text here"}]}""",
        ) as GbMessage.CannedResponses
        assertEquals(listOf("Yes"), msg.list)
    }
}
