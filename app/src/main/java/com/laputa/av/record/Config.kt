package com.laputa.av.record

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder

/**
 * Author by xpl, Date on 2021/7/9.
 */
object Config {
    const val S_DEGREES = 90
    const val S_WIDTH = 1920
    const val S_HEIGHT = 1080

    // V编码相关参数
    const val V_MIME_TYPE = "video/avc" // H.264 Advanced Video
    const val V_FRAME_RATE = 25 // 帧率
    const val V_I_FRAME_INTERVAL = 10 // I帧间隔（GOP）
    const val V_TIME_OUT_ENCODE = 10000L // 编码超时时间
    private const val V_COMPRESS_RATIO = 256
    val V_BIT_RATE: Int
        get() = S_WIDTH * S_HEIGHT * 3 * 8 * V_FRAME_RATE / V_COMPRESS_RATIO // bit rate CameraWrapper.

    // A编码相关参数
    const val A_SAMPLES_PER_FRAME = 1024
    const val A_FRAMES_PER_BUFFER = 25
    const val A_TIMEOUT_USEC = 10000L
    const val A_MIME_TYPE = "audio/mp4a-latm"
    const val A_SAMPLE_RATE = 16000
    const val A_BIT_RATE = 64000
    val AUDIO_SOURCES = arrayOf(MediaRecorder.AudioSource.DEFAULT)


    @JvmStatic
    fun selectCodec(mimeType: String = V_MIME_TYPE): MediaCodecInfo? {
        val codecCount = MediaCodecList.getCodecCount()
        (0 until codecCount).forEach { index ->
            val codecInfo = MediaCodecList.getCodecInfoAt(index)
            if (codecInfo.isEncoder) {
                codecInfo.supportedTypes.forEach { type ->
                    if (type == mimeType) {
                        return codecInfo
                    }
                }
            }
        }
        return null
    }

    // 将原始的N21数据转为I420
    @JvmStatic
    fun nv21toI420SemiPlanar(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        val size = width * height
        System.arraycopy(src, 0, dst, 0, size)
        (size until dst.size step 2).forEach { index ->
            dst[index] = src[index + 1]
            dst[index + 1] = src[index]
        }
    }
}