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
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import net.farcaster.pixelbridge.data.NotificationStore
import net.farcaster.pixelbridge.data.WatchNotification
import net.farcaster.pixelbridge.ui.MainActivity

/**
 * A glanceable "recent notifications" digest tile: a count header plus the latest few notifications
 * (sender · snippet), with an edge button that opens the app for the full list and per-notification
 * actions. Reads the process-wide [NotificationStore] synchronously; the foreground service pushes
 * fresh tile updates via [TileService.getUpdater] whenever the notification list changes.
 *
 * Deliberately shows ONLY notifications — battery lives in Gadgetbridge's device card and Find-phone
 * lives in the app, so the tile focuses on the one thing worth a glance: what's waiting.
 */
class PixelBridgeTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val items = NotificationStore.items.value
        val layout = buildLayout(this, requestParams.deviceConfiguration, items)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return immediateFuture(tile, "onTileRequest")
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        // Text-only layout — no inline images/fonts.
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return immediateFuture(resources, "onTileResourcesRequest")
    }

    /**
     * Title = a count summary; mainSlot = a Column of up to [MAX_ROWS] recent notification lines
     * (a Column of text elements renders fine — unlike button groups, text has intrinsic height);
     * bottomSlot = an "Open" edge button whose [ActionBuilders.LaunchAction] starts [MainActivity].
     */
    private fun buildLayout(
        context: Context,
        device: DeviceParameters,
        items: List<WatchNotification>,
    ): LayoutElement {
        val header = when (items.size) {
            0 -> "No notifications"
            1 -> "1 notification"
            else -> "${items.size} notifications"
        }
        val recent = items.take(MAX_ROWS)

        val openClickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        return materialScope(context, device) {
            primaryLayout(
                titleSlot = { text(header.layoutString) },
                mainSlot = {
                    LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .apply {
                            if (recent.isEmpty()) {
                                addContent(text("You're all caught up".layoutString))
                            } else {
                                recent.forEach { addContent(text(digestLine(it).layoutString)) }
                            }
                        }
                        .build()
                },
                bottomSlot = {
                    textEdgeButton(onClick = openClickable) { text("Open".layoutString) }
                },
            )
        }
    }

    private companion object {
        private const val RESOURCES_VERSION = "1"

        /** Recent notifications shown on the tile; the rest are reachable via Open. */
        private const val MAX_ROWS = 3

        /** Max chars per digest line so it stays one line and never wraps/overflows the round edge. */
        private const val MAX_LINE = 30

        /** "Sender · snippet" for one notification, truncated to a single line. */
        private fun digestLine(n: WatchNotification): String {
            val head = n.title ?: n.sender ?: n.app ?: "Notification"
            val body = n.body?.takeIf { it.isNotBlank() }
            val line = if (body != null) "$head · $body" else head
            return if (line.length > MAX_LINE) line.take(MAX_LINE - 1).trimEnd() + "…" else line
        }

        private fun <T> immediateFuture(value: T, tag: String): ListenableFuture<T> =
            CallbackToFutureAdapter.getFuture { completer ->
                completer.set(value)
                tag
            }
    }
}
