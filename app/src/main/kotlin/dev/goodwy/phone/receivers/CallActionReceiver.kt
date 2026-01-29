package dev.goodwy.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.goodwy.phone.activities.CallActivity
import dev.goodwy.phone.extensions.audioManager
import dev.goodwy.phone.extensions.config
import dev.goodwy.phone.helpers.ACCEPT_CALL
import dev.goodwy.phone.helpers.CallManager
import dev.goodwy.phone.helpers.CallNotificationManager
import dev.goodwy.phone.helpers.DECLINE_CALL
import dev.goodwy.phone.helpers.MICROPHONE_CALL
import dev.goodwy.phone.helpers.SPEAKER_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE_CALL -> CallManager.reject()
            ACCEPT_CALL -> {
                if (!context.config.keepCallsInPopUp) context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
                if (context.config.keepCallsInPopUp && context.config.turnOnSpeakerInPopup) {
                    CallManager.toggleSpeakerRoute(true)
                }
            }

            MICROPHONE_CALL -> {
//                val isMicrophoneMute = context.audioManager.isMicrophoneMute
//                CallManager.inCallService?.setMuted(!isMicrophoneMute)

                val audioManager = context.audioManager
                audioManager.isMicrophoneMute = !audioManager.isMicrophoneMute
                CallNotificationManager(context).updateNotification()
            }

            SPEAKER_CALL -> {
//                val callManager = CallManager
//                val currentRoute = callManager.getCallAudioRoute()
//                val newRoute = if (currentRoute == AudioRoute.SPEAKER) {
//                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
//                } else {
//                    CallAudioState.ROUTE_SPEAKER
//                }
//                callManager.setAudioRoute(newRoute)
//                CallNotificationManager(context).updateNotification()

                CallManager.toggleSpeakerRoute()
                CallNotificationManager(context).updateNotification()
            }
        }
    }
}
