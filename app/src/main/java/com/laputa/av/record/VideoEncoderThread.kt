package com.laputa.av.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.laputa.av.ext.logger
import java.lang.ref.WeakReference
import java.util.*

/**
 * Author by xpl, Date on 2021/7/9.
 */
class VideoEncoderThread(
    private val muxerThread: WeakReference<MediaMuxerThread>,
    private val width: Int,
    private val height: Int
) : Thread() {
    private val lock = Object()

    private var frameBytes: Vector<ByteArray> = Vector()
    private lateinit var mFrameData: ByteArray

    private var mMediaCodec: MediaCodec? = null // Android硬编解码器
    private var mMediaCodecInfo: MediaCodecInfo? = null //
    private lateinit var mBufferInfo: MediaCodec.BufferInfo  //  编解码Buffer相关信息
    private var mMediaFormat: MediaFormat? = null

    @Volatile
    private var isStart: Boolean = false

    @Volatile
    private var isExit: Boolean = false

    @Volatile
    private var isMudexReady = false

    init {
        prepare()
    }

    private fun prepare() {
        logger("prepare", TAG)
        mFrameData = ByteArray(this.width * this.height * 3 / 2)
        mBufferInfo = MediaCodec.BufferInfo()
        val mime = Config.V_MIME_TYPE
        mMediaCodecInfo = Config.selectCodec(mime)
        if (mMediaCodecInfo == null) {
            logger("prepare error : Unable to find an appropriate codec for ${mime}", TAG)
            return
        }
        mMediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, Config.V_BIT_RATE) // 比特率
            setInteger(MediaFormat.KEY_FRAME_RATE, Config.V_FRAME_RATE) // 帧率
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Config.V_I_FRAME_INTERVAL) // GOF
        }
        logger("prepare ok", TAG)
    }

    private fun startCodec() {
        if (mMediaCodecInfo == null) {
            logger("mMediaCodecInfo is null", TAG)
            return
        }

        if (mMediaFormat == null) {
            logger("mMediaFormat is null", TAG)
            return
        }
        mMediaCodec = MediaCodec.createByCodecName(mMediaCodecInfo!!.name).also {
            it.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
            isStart = true
            logger(">>> startCodec", TAG)
        }
    }

    private fun stopCodec() {
        mMediaCodec?.stop()
        mMediaCodec?.release()
        mMediaCodec = null
        isStart = false
        logger("stopCodec <<<", TAG)
    }

    fun setMuxerReady(ready: Boolean) {
        synchronized(lock) {
            logger("video setMuxerReady:$ready ${Thread.currentThread().id}", TAG)
            isMudexReady = ready
            lock.notifyAll()
        }
    }

    override fun run() {
        super.run()
        while (!isExit) {
            if (!isStart) {
                stopCodec()
                if (!isMudexReady) {
                    try {
                        logger("video 等待混合器准备...", TAG)
                        lock.wait()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                if (isMudexReady) {
                    try {
                        startCodec()
                    } catch (e: Throwable) {
                        isStart = false
                        Thread.sleep(100)
                        e.printStackTrace()
                    }
                }
            } else if (!frameBytes.isEmpty()) {
                val data = this.frameBytes.removeAt(0)
                logger("-->解码视频数据：${data.size}", TAG)
                try {
                    encodeFrame(data)
                } catch (e: Throwable) {
                    logger("-->解码视频数据：${data.size} 失败 error = ${e.message}", TAG)
                    e.printStackTrace()
                }
            }
        }
        logger("video 录制线程退出", TAG)
    }

    private fun encodeFrame(data: ByteArray) {
        logger("encodeFrame... ${data.size}", TAG)

        Config.nv21toI420SemiPlanar(data, mFrameData, width, height)
        mMediaCodec?.let { codec ->
            val inputBuffers = codec.inputBuffers
            var outputBuffers = codec.outputBuffers

            // 送数据去codec
            val inputBuffIndex = codec.dequeueInputBuffer(Config.V_TIME_OUT_ENCODE)
            logger("encodeFrame::inputBuffIndex = $inputBuffIndex", TAG)
            when {
                inputBuffIndex >= 0 -> {
                    val tInputBuffer = inputBuffers[inputBuffIndex] // 拿到碗
                    tInputBuffer.clear() // 擦干净
                    tInputBuffer.put(mFrameData) // 盛饭
                    codec.queueInputBuffer(
                        inputBuffIndex,
                        0,
                        mFrameData.size,
                        System.nanoTime() / 1000L,
                        0
                    ) // 送去热一下
                }
                else -> {
                    logger("input buffer not available", TAG)
                }
            }

            // 拿数据codec
            var outputBufferIndex =
                codec.dequeueOutputBuffer(mBufferInfo, Config.V_TIME_OUT_ENCODE)
            logger("encodeFrame::outputBufferIndex = $outputBufferIndex", TAG)
            do {
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputBuffers = codec.outputBuffers
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // TODO
                        logger("解码视频数据 INFO_OUTPUT_FORMAT_CHANGED",TAG)
                    }
                    outputBufferIndex < 0 -> {
                        logger("outputBufferIndex < 0", TAG)
                    }
                    else -> {
                        logger("perform encoding ... ", TAG)
                        val tOutputBuffer = outputBuffers[outputBufferIndex] // 拿到盛满饭的碗
                        if (tOutputBuffer == null) {
                            throw RuntimeException("tOutputBuffer is null")
                        }
                        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            mBufferInfo.size = 0
                        }
                        if (mBufferInfo.size != 0) {
                            // todo format changed
                            tOutputBuffer.position(mBufferInfo.offset) // 拿到热饭
                            tOutputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)
                            logger("    -->解码视频数据 success",TAG)
                            muxerThread.get()?.let { mt ->
                                logger("    -->解码视频数据 add",TAG)
                                if (mt.isMuxerStart|| true) {

                                    mt.addMuxerData( // 倒到自己的碗里
                                        MuxerData(
                                            MediaMuxerThread.TRACK_VIDEO,
                                            tOutputBuffer,
                                            mBufferInfo
                                        )
                                    )
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false) // 空出碗来
                    }

                }
                // 再看看有没有
                outputBufferIndex = codec.dequeueOutputBuffer(mBufferInfo, Config.V_TIME_OUT_ENCODE)
            } while (outputBufferIndex >= 0)
        }
    }

    fun addData(data: ByteArray) {
        if (isMudexReady) {
            frameBytes.add(data)
        }
    }

    fun exit() {
        isExit = true
    }

    companion object {
        private const val TAG = "VideoEncoderThread"
        private val lock = Any()
    }
}