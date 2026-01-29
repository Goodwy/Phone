package dev.goodwy.phone.models

sealed class Events {
    data object RefreshCallLog : Events()
	
    class StateChanged(val isEnabled: Boolean)

    class CameraUnavailable

    class StopStroboscope

    class StopSOS
}
