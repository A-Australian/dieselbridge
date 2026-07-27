// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import net.farcaster.pixelbridge.data.MusicStore
import net.farcaster.pixelbridge.data.NotificationActions
import net.farcaster.pixelbridge.data.NowPlaying

/**
 * A glanceable now-playing tile: the current track/artist plus a three-button transport
 * (previous · play/pause · next). Reads the process-wide [MusicStore] snapshot synchronously —
 * the foreground service pushes fresh tile updates via [TileService.getUpdater] (see
 * PixelBridgeService) whenever the now-playing state changes.
 *
 * Each transport button carries a [ActionBuilders.LoadAction]; when the user taps one the platform
 * re-requests the tile with [lastClickableId][androidx.wear.protolayout.StateBuilders.State.getLastClickableId]
 * set to the button's id, which we detect and turn into the matching [NotificationActions.music]
 * command. The play/pause command is chosen from the last-known playing state.
 */
class MusicTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val np = MusicStore.state.value
        val playing = np?.playing == true

        // A tap on a transport button comes back as a fresh tile request tagged with its id.
        when (requestParams.currentState.lastClickableId) {
            CLICK_PREV -> NotificationActions.music(NotificationActions.MUSIC_PREVIOUS)
            CLICK_PLAYPAUSE ->
                NotificationActions.music(
                    if (playing) NotificationActions.MUSIC_PAUSE else NotificationActions.MUSIC_PLAY,
                )
            CLICK_NEXT -> NotificationActions.music(NotificationActions.MUSIC_NEXT)
            CLICK_VOLDOWN -> NotificationActions.music(NotificationActions.MUSIC_VOLUMEDOWN)
            CLICK_VOLUP -> NotificationActions.music(NotificationActions.MUSIC_VOLUMEUP)
        }

        val layout = buildLayout(this, requestParams.deviceConfiguration, np)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()

        return immediateFuture(tile, "onTileRequest")
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        // No inline images/fonts — the Material3 layout draws everything from built-in resources.
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return immediateFuture(resources, "onTileResourcesRequest")
    }

    /**
     * Material3 (protolayout-material3) layout: the track title, the artist, and a bottom transport
     * [buttonGroup] of three [textButton]s (previous / play-pause / next), each firing its own
     * [ActionBuilders.LoadAction].
     *
     * FALLBACK: if the buttonGroup/textButton DSL is ever unavailable on the resolved
     * protolayout-material3 version, this bottom slot can degrade to a single
     * `textEdgeButton(onClick = playPauseClickable) { text((if (playing) "⏸" else "▶").layoutString) }`
     * — a glanceable now-playing + play/pause tile is acceptable. Read the playing state first so the
     * label and the command sent stay in sync.
     */
    private fun buildLayout(
        context: Context,
        device: DeviceParameters,
        np: NowPlaying?,
    ): LayoutElement {
        // Title shows track + artist (single line, ellipsized by the slot) so the whole mainSlot is
        // free for the transport buttons — a 3-button row plus a text column overflowed the round edge.
        val artist = np?.artist
        val track = np?.track ?: "Nothing playing"
        val title = if (artist.isNullOrBlank()) track else "$track · $artist"
        val playing = np?.playing == true

        val prevClickable = loadClickable(CLICK_PREV)
        val playPauseClickable = loadClickable(CLICK_PLAYPAUSE)
        val nextClickable = loadClickable(CLICK_NEXT)
        val volDownClickable = loadClickable(CLICK_VOLDOWN)
        val volUpClickable = loadClickable(CLICK_VOLUP)

        return materialScope(context, device) {
            primaryLayout(
                // Track·artist title; mainSlot stacks two button rows — transport (⏮ ⏸ ⏭) and volume
                // (🔉 🔊). The Column and each buttonGroup are height=expand so the two rows split the
                // main area vertically; without an explicit height the nested groups collapse to zero
                // (the earlier "no buttons" bug) and 5-in-one-row clipped the round edge.
                titleSlot = { text(title.layoutString) },
                mainSlot = {
                    LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        // Centre each row so the 2-button volume row sits centred under the 3-button
                        // transport row instead of clustering on the left.
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(
                            buttonGroup(height = DimensionBuilders.expand()) {
                                buttonGroupItem {
                                    textButton(onClick = prevClickable, labelContent = { text("⏮".layoutString) })
                                }
                                buttonGroupItem {
                                    textButton(onClick = playPauseClickable, labelContent = { text((if (playing) "⏸" else "▶").layoutString) })
                                }
                                buttonGroupItem {
                                    textButton(onClick = nextClickable, labelContent = { text("⏭".layoutString) })
                                }
                            },
                        )
                        .addContent(
                            // width=wrap so this 2-button row shrinks to its content and the Column's
                            // centre alignment centres it under the (full-width) transport row, instead
                            // of the buttonGroup filling the width and left-packing its two buttons.
                            buttonGroup(
                                width = DimensionBuilders.wrap(),
                                height = DimensionBuilders.expand(),
                            ) {
                                buttonGroupItem {
                                    textButton(onClick = volDownClickable, labelContent = { text("🔉".layoutString) })
                                }
                                buttonGroupItem {
                                    textButton(onClick = volUpClickable, labelContent = { text("🔊".layoutString) })
                                }
                            },
                        )
                        .build()
                },
            )
        }
    }

    private companion object {
        private const val RESOURCES_VERSION = "1"

        /** Clickable ids echoed back in the tile's state after a transport button is tapped. */
        private const val CLICK_PREV = "pb_music_prev"
        private const val CLICK_PLAYPAUSE = "pb_music_pp"
        private const val CLICK_NEXT = "pb_music_next"
        private const val CLICK_VOLDOWN = "pb_music_voldown"
        private const val CLICK_VOLUP = "pb_music_volup"

        private fun loadClickable(id: String): ModifiersBuilders.Clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build()

        private fun <T> immediateFuture(value: T, tag: String): ListenableFuture<T> =
            CallbackToFutureAdapter.getFuture { completer ->
                completer.set(value)
                tag
            }
    }
}
