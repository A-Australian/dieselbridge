// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import org.aaustralian.dieselbridge.notify.CallController

/**
 * No-UI trampoline for the call notification's action buttons. Wear notification actions cannot
 * target a BroadcastReceiver, so [CallController]'s notification points its Answer / Decline / End
 * actions here; this activity reads [EXTRA_ACTION], routes it to the matching [CallController]
 * command, and immediately finishes. It runs under a translucent, no-title theme (declared in the
 * manifest) so nothing flashes on screen.
 */
class CallActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            "ACCEPT" -> CallController.answer(this)
            "REJECT" -> CallController.reject(this)
            "END" -> CallController.end(this)
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "pb_call_action"
    }
}
