package com.laputa.av.record

import android.media.MediaCodec
import android.media.MediaMuxer
import com.laputa.av.ext.logger
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*

/**
 * Author by xpl, Date on 2021/7/9.
 */
object MediaMuxerThread : Thread() {
    private const val TAG = "MediaMuxerThread"
    private val lock = Object()
    const val TRACK_VIDEO = 0
    const val TRACK_AUDIO = 1

    private lateinit var mAudioEncoderThread: AudioEncoderThread
    private lateinit var mVideoEncoderThread: VideoEncoderThread

    private var isExit = false

    @JvmStatic
    var isVideoTrackAdd = true

    @JvmStatic
    private var isAudioTrackAdd = true

    private lateinit var mFileUtils: FileUtils

    private var mMediaMuxer: MediaMuxer? = null
    private var muxerDatas: Vector<MuxerData> = Vector()

    override fun run() {
        super.run()
        initMuxer()
    }

    private fun initMuxer() {
        logger("initMuxer", TAG)
        muxerDatas = Vector()
        mFileUtils = FileUtils()
        mAudioEncoderThread = AudioEncoderThread(WeakReference<MediaMuxerThread>(this))
            .also {
                it.start()
            }
        mVideoEncoderThread = VideoEncoderThread(
            WeakReference<MediaMuxerThread>(this),
            Config.S_WIDTH,
            Config.S_HEIGHT
        ).also {
            it.start()
        }

        try {
            readyStart()
        } catch (e: Throwable) {
            e.printStackTrace()
            logger("initMuxer error : ${e.message}", TAG)
        }
    }

    private fun readyStart() {
        mFileUtils.requestSwapFile(true)
        readyStart(mFileUtils.nextFileName)
    }


    private fun readyStart(filePath: String) {
        isExit = false
        isVideoTrackAdd = false
        isAudioTrackAdd = false
        muxerDatas.clear()
        mMediaMuxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mAudioEncoderThread.setMuxerReady(true)
        mVideoEncoderThread.setMuxerReady(true)
        logger("readyStart save to $filePath", TAG)

    }

    @JvmStatic
    fun startMuxer() {
        logger("startMuxer", TAG)
        start()
    }

    @JvmStatic
    fun stopMuxer() {
        logger("stopMuxer", TAG)
        exit()
        try {
            this.join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun addVideoFrameData(data: ByteArray) {
        this.mVideoEncoderThread.addData(data)
    }

    @JvmStatic
    fun addMuxerData(data: MuxerData) {
        logger("addMuxerData", TAG)
        if (!isMuxerStart) {
            return
        }
        muxerDatas.add(data)
        logger("addMuxerData size = ${muxerDatas.size}", TAG)
        synchronized(lock) {
            lock.notify()
        }
    }

    @JvmStatic
    val isMuxerStart: Boolean
        get() = isAudioTrackAdd && isVideoTrackAdd

    private fun exit() {
        mVideoEncoderThread.exit()
        try {
            mVideoEncoderThread.join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        mAudioEncoderThread.exit()
        try {
            mAudioEncoderThread.join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        isExit = true
        synchronized(lock) {
            lock.notify()
        }
    }


}

class MuxerData(val trackIndex: Int, val buffer: ByteBuffer, val bufferInfo: MediaCodec.BufferInfo)