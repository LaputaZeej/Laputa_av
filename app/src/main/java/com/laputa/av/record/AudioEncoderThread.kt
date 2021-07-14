package com.laputa.av.record

import java.lang.ref.WeakReference

/**
 * Author by xpl, Date on 2021/7/9.
 */
class AudioEncoderThread( private val muxerThread: WeakReference<MediaMuxerThread>) : Thread() {
    private var isMudexReady = false

    override fun run() {
        super.run()
    }

    fun setMuxerReady(ready: Boolean) {
        isMudexReady = ready
    }

    fun addData(data: ByteArray) {

    }

    fun exit() {

    }

    companion object {
        private const val TAG = "AudioEncoderThread"
        private val lock = Any()
    }
}