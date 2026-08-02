// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.data

/**
 * Bridges UI action taps (dismiss / reply) to the BLE controller, which sends them to
 * Gadgetbridge over the NUS TX characteristic. The controller/service installs [handler]; the
 * Compose UI calls the helpers. Process-wide singleton (same pattern as NotificationStore).
 */
object NotificationActions {
    /** (id, action, reply?) -> transmit over BLE. Installed by the service. */
    @Volatile
    var handler: ((id: Long, action: String, reply: String?) -> Unit)? = null

    /** (active) -> ask the phone to ring (findPhone). Installed by the service. */
    @Volatile
    var findPhoneHandler: ((active: Boolean) -> Unit)? = null

    /** (action) -> answer/reject/ignore/end an incoming call. Installed by the service. */
    @Volatile
    var callHandler: ((action: String) -> Unit)? = null

    /** (cmd) -> control phone music playback. Installed by the service. */
    @Volatile
    var musicHandler: ((cmd: String) -> Unit)? = null

    fun dismiss(id: Long) = handler?.invoke(id, ACTION_DISMISS, null)
    fun reply(id: Long, text: String) = handler?.invoke(id, ACTION_REPLY, text)
    fun dismissAll(id: Long = 0L) = handler?.invoke(id, ACTION_DISMISS_ALL, null)
    fun findPhone(active: Boolean = true) = findPhoneHandler?.invoke(active)
    fun call(action: String) = callHandler?.invoke(action)
    fun music(cmd: String) = musicHandler?.invoke(cmd)

    const val ACTION_DISMISS = "DISMISS"
    const val ACTION_REPLY = "REPLY"
    const val ACTION_DISMISS_ALL = "DISMISS_ALL"

    const val ACTION_ACCEPT = "ACCEPT"
    const val ACTION_REJECT = "REJECT"
    const val ACTION_IGNORE = "IGNORE"
    const val ACTION_END = "END"

    const val MUSIC_PLAY = "play"
    const val MUSIC_PAUSE = "pause"
    const val MUSIC_NEXT = "next"
    const val MUSIC_PREVIOUS = "previous"
    const val MUSIC_VOLUMEUP = "volumeup"
    const val MUSIC_VOLUMEDOWN = "volumedown"
}
