package com.laputa.av.record

import android.media.*
import com.laputa.av.ext.logger
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*

/**
 * Author by xpl, Date on 2021/7/9.
 */
class AudioEncoderThread(private val muxerThread: WeakReference<MediaMuxerThread>) : Thread() {
    private val lock = Object()

    @Volatile
    private var isStart: Boolean = false

    @Volatile
    private var isExit: Boolean = false

    @Volatile
    private var isMudexReady = false

    private var mMediaCodec: MediaCodec? = null // API >= 16(Android4.1.2)
    private var mAudioRecord: AudioRecord? = null
    private var mBufferInfo: MediaCodec.BufferInfo =
        MediaCodec.BufferInfo() // API >= 16(Android4.1.2)
    private var mMediaFormat: MediaFormat? = null

    private var prevOutputPTSUs: Long = 0

    init {
        prepared()
    }

    private fun prepared() {
        val codecInfo: MediaCodecInfo? = Config.selectCodec(Config.A_MIME_TYPE)
        if (codecInfo == null) {
            logger("Unable to find an appropriate codec for ${Config.A_MIME_TYPE}", TAG)
            return
        }
        logger("selected codec:  ${codecInfo.name}", TAG)
        mMediaFormat =
            MediaFormat.createAudioFormat(Config.A_MIME_TYPE, Config.A_SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, Config.A_BIT_RATE)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, Config.A_SAMPLE_RATE)
            }
        logger("mMediaFormat:  ${mMediaFormat}", TAG)
    }

    private fun startMediacodec() {
        if (mMediaCodec != null) return
        mMediaCodec = MediaCodec.createEncoderByType(Config.A_MIME_TYPE)
            .also { mc ->
                mc.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                mc.start()
            }

        prepareAudioRecord()
        isStart = true
    }

    private fun stopMediaCodec() {
        mAudioRecord?.stop()
        mAudioRecord?.release()
        mAudioRecord = null
        try {
            Thread.sleep(100)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        mMediaCodec?.stop()
        mMediaCodec?.release()
        mMediaCodec = null
        isStart = false
        logger("停止录制", TAG)
    }

    @Synchronized
     fun reStart() {
        isStart = false
        isMudexReady = false
    }

    private fun prepareAudioRecord() {
        mAudioRecord?.stop()
        mAudioRecord?.release()
        mAudioRecord = null
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                Config.A_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            var bufferSize = Config.A_SAMPLES_PER_FRAME * Config.A_FRAMES_PER_BUFFER
            if (bufferSize < minBufferSize) {
                bufferSize =
                    (1 + minBufferSize / Config.A_SAMPLES_PER_FRAME) * Config.A_SAMPLES_PER_FRAME * 2
            }
            mAudioRecord = null
            for (source in Config.AUDIO_SOURCES) {
                try {
                    mAudioRecord = AudioRecord(
                        (source), Config.A_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    if (mAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        mAudioRecord = null
                    }
                    if (mAudioRecord != null) {
                        mAudioRecord?.startRecording()
                        logger("startRecording", TAG)
                        return
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            logger("prepareAudioRecord e = $e", TAG)
        }
    }

    fun setMuxerReady(ready: Boolean) {
        synchronized(lock) {
            logger("setMuxerReady:$ready @${Thread.currentThread()}", TAG)
            isMudexReady = ready
            lock.notifyAll()
        }
    }

    override fun run() {
        super.run()
        val buffer = ByteBuffer.allocateDirect(Config.A_SAMPLES_PER_FRAME)
        var readBytes = 0
        while (!isExit) {
            /*启动或者重启*/
            if (!isStart) {
                stopMediaCodec()
                logger("running... @${Thread.currentThread()}", TAG)
                if (!isMudexReady) {
                    synchronized(lock) {
                        try {
                            logger("audio -- 等待混合器准备...", TAG)
                            lock.wait()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }
                if (isMudexReady) {
                    try {
                        startMediacodec()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        isStart = false
                        try {
                            Thread.sleep(100)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }
            } else if (mAudioRecord != null) {
                buffer.clear()
                mAudioRecord?.let { ar ->
                    readBytes = ar.read(buffer, Config.A_SAMPLES_PER_FRAME)
                    if (readBytes > 0) {
                        buffer.position(readBytes)
                        buffer.flip()
                        logger("解码音频数据:$readBytes", TAG)
                        try {
                            enCode(buffer, readBytes, getPTSUs())
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            logger("解码音频(Audio)数据 失败", TAG)
                        }
                    }

                }
            }

        }
        logger("Audio 录制线程 退出...", TAG)
    }

    private fun enCode(buffer: ByteBuffer?, length: Int, ptsUs: Long) {
        if (isExit) return
        mMediaCodec?.let { mc ->
            val inputBuffers = mc.inputBuffers
            val dequeueInputBufferIndex = mc.dequeueInputBuffer(Config.A_TIMEOUT_USEC)
            when {
                /*向编码器输入数据*/
                dequeueInputBufferIndex >= 0 -> {

                    val inputBuffer = inputBuffers[dequeueInputBufferIndex]
                    inputBuffer.clear()
                    if (buffer != null) {
                        inputBuffer.put(buffer)
                    }
                    if (length <= 0) {
                        logger("send BUFFER_FLAG_END_OF_STREAM", TAG)
                        mc.queueInputBuffer(
                            dequeueInputBufferIndex,
                            0,
                            0,
                            ptsUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    } else {
                        mc.queueInputBuffer(dequeueInputBufferIndex, 0, length, ptsUs, 0)
                    }
                }
                dequeueInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // wait for MediaCodec encoder is ready to encode
                    // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                    // will wait for maximum TIMEOUT_USEC(10msec) on each call
                }
            }

            /*获取解码后的数据*/
            val muxer = muxerThread.get()
            if (muxer == null) {
                logger("muxerThread is null", TAG)
                return
            }
            var outputBuffers = mc.outputBuffers
            var encoderStatus = 0
            do {
                encoderStatus = mc.dequeueOutputBuffer(mBufferInfo, Config.A_TIMEOUT_USEC)
                when {
                    encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        //
                    }
                    encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        outputBuffers = mc.outputBuffers
                    }
                    encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = mc.getOutputFormat()
                        muxer.addTrackIndex(MediaMuxerThread.TRACK_AUDIO, outputFormat)
                    }

                    encoderStatus < 0 -> {
                        logger("encoderStatus < 0", TAG)
                    }
                    else -> {
                        val encodedData = outputBuffers[encoderStatus]
                        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            mBufferInfo.size = 0
                        }
                        if (mBufferInfo.size!=0 && muxer!=null && muxer.isMuxerStart){
                            mBufferInfo.presentationTimeUs = getPTSUs()
                            muxer.addMuxerData(
                                MuxerData(MediaMuxerThread.TRACK_AUDIO,encodedData,mBufferInfo)
                            )
                            prevOutputPTSUs = mBufferInfo.presentationTimeUs
                        }
                        mc.releaseOutputBuffer(encoderStatus,false)
                    }
                }
            } while (encoderStatus >= 0)

        }
    }

    private fun getPTSUs(): Long {
        val now = System.nanoTime() / 1000L
        return if (now < prevOutputPTSUs) {
            prevOutputPTSUs - now + now
        } else {
            now
        }
    }

    fun addData(data: ByteArray) {

    }

    fun exit() {
        isExit = true
    }

    companion object {
        private const val TAG = "AudioEncoderThread"
    }
}