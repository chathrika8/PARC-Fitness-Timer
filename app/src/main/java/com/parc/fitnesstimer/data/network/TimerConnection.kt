package com.parc.fitnesstimer.data.network

import com.parc.fitnesstimer.domain.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TimerConnection {
    val connectionState: StateFlow<ConnectionState>
    val textFrames: SharedFlow<String>
    
    fun connect(urlOrAddress: String)
    fun disconnect()
    fun sendText(text: String): Boolean
    fun sendBinary(bytes: okio.ByteString): Boolean
}
