package com.laputa.av.record

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.laputa.av.ext.logger
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*

/**
 * Author by xpl, Date on 2021/7/9.
 */
class MediaMuxerThread : Thread() {

    companion object{
        private const val TAG = "MediaMuxerThread"
        const val TRACK_VIDEO = 0
        const val TRACK_AUDIO = 1
    }

    private val lock = Object()

    private lateinit var mAudioEncoderThread: AudioEncoderThread
    private lateinit var mVideoEncoderThread: VideoEncoderThread
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private var isExit = false

    var isVideoTrackAdd = false

    private var isAudioTrackAdd = false

    val isMuxerStart: Boolean
        get() = isAudioTrackAdd && isVideoTrackAdd

    private lateinit var mFileUtils: FileUtils

    private var mMediaMuxer: MediaMuxer? = null
    private var muxerDatas: Vector<MuxerData> = Vector()

    override fun run() {
        super.run()
        initMuxer()
        while (!isExit) {
            if (isMuxerStart) {
                if (muxerDatas.isEmpty()) {
                    synchronized(lock) {
                        try {
                            logger("等待音视频数据 ...", TAG)
                            lock.wait()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    if (mFileUtils.requestSwapFile()) {
                        // 需要切换文件
                        val nextFileName = mFileUtils.nextFileName
                        logger("正在重启混音器nextFileName=$nextFileName", TAG)
                        reStart(nextFileName)
                    } else {
                        val remove = muxerDatas.removeAt(0)
                        var track = 0
                        var type = ""
                        when (remove.trackIndex) {
                            TRACK_VIDEO -> {
                                track = videoTrackIndex
                                type = "视频"
                            }
                            TRACK_AUDIO -> {
                                track = audioTrackIndex
                                type = "音频"
                            }
                        }
                        logger("写入混合数据 $type...size = ${remove.bufferInfo.size}", TAG)
                        try {
                            mMediaMuxer?.writeSampleData(track, remove.buffer, remove.bufferInfo)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            logger("写入混合数据失败 ...${e.toString()}", TAG)
                        }
                    }
                }
            } else {
                synchronized(lock) {
                    try {
                        logger("等待音视轨添加 ...", TAG)
                        lock.wait()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        logger("等待音视轨添加异常", TAG)
                    }
                }
            }
        }
        readyStop()
        logger("混合器退出!", TAG)
    }

    fun startMuxer() {
        logger("startMuxer", TAG)
        start()
    }

    fun stopMuxer() {
        logger("stopMuxer", TAG)
        exit()
        try {
            this.join()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // 解码前的原始数据
    fun addVideoFrameData(data: ByteArray) {
        this.mVideoEncoderThread.addData(data)
    }

    // 解码后的数据（音频视频）
    fun addMuxerData(data: MuxerData) {
        logger(
            "addMuxerData isMuxerStart=$isMuxerStart isAudioTrackAdd=$isAudioTrackAdd isVideoTrackAdd=$isVideoTrackAdd",
            TAG
        )
        if (!isMuxerStart) {
            return
        }
        muxerDatas.add(data)
        logger("addMuxerData size = ${muxerDatas.size}", TAG)
        synchronized(lock) {
            lock.notify()
        }
    }

    @Synchronized
    fun addTrackIndex(index: Int, mediaFormat: MediaFormat) {
        if (isMuxerStart) {
            return
        }
        /* 如果已经添加了，就不做处理了 */
        if ((index == TRACK_AUDIO && isAudioTrackAdd) || (index == TRACK_VIDEO && isVideoTrackAdd)) {
            logger("addTrackIndex 已经添加", TAG)
            return
        }
        mMediaMuxer?.let { mm ->
            var track = 0
            try {
                track = mm.addTrack(mediaFormat)
            } catch (e: Throwable) {
                e.printStackTrace()
                logger("addTrackIndex ${e}", TAG)
                return
            }
            when (index) {
                TRACK_VIDEO -> {
                    videoTrackIndex = track
//                    isVideoTrackAdd = true
                    logger("添加视频轨完成", TAG)
                }

                TRACK_AUDIO -> {
                    audioTrackIndex = track
                    isAudioTrackAdd = true
                    logger("添加音频轨完成", TAG)
                }

            }
            requestStart()

        }
    }

    private fun reStart() {
        mFileUtils.requestSwapFile(true)
        reStart(mFileUtils.nextFileName)
    }

    private fun reStart(filePath: String) {
        reStartAudioVideo()
        readyStop()
        try {
            readyStart(filePath)
        } catch (e: Throwable) {
            logger("readyStart(filePath, true)重启混合器失败 尝试再次重启!${e.toString()}", TAG)
            e.printStackTrace()
            reStart()
            return
        }
        logger("重启混合器完成", TAG)
    }

    private fun readyStop() {
        try {
            mMediaMuxer?.stop()
            mMediaMuxer?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        mMediaMuxer = null

    }

    private fun reStartAudioVideo() {
        isAudioTrackAdd = false
        audioTrackIndex = -1
        mAudioEncoderThread.reStart()
        isVideoTrackAdd = false
        videoTrackIndex = -1
        mVideoEncoderThread.reStart()
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

    private fun requestStart() {
        synchronized(lock) {
            if (isMuxerStart) {
                mMediaMuxer?.start()
                logger("requestStart启动混合器..开始等待数据输入...", TAG)
                lock.notify()
            }
        }
    }

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