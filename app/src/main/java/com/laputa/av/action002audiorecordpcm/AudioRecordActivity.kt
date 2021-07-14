package com.laputa.av.action002audiorecordpcm

import android.media.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.laputa.av.R
import com.laputa.av.ext.logger
import java.io.*
import java.lang.StringBuilder
import java.nio.ByteBuffer

/**
 * Android 音视频开发(二)：使用 AudioRecord 采集音频PCM并保存到文件
 * https://www.cnblogs.com/renhui/p/7457321.html
 *
 * 实现Android录音的流程为：

构造一个AudioRecord对象，其中需要的最小录音缓存buffer大小可以通过getMinBufferSize方法得到。如果buffer容量过小，将导致对象构造的失败。
初始化一个buffer，该buffer大于等于AudioRecord对象用于写声音数据的buffer大小。
开始录音
创建一个数据流，一边从AudioRecord中读取声音数据到初始化的buffer，一边将buffer中数据导入数据流。
关闭数据流
停止录音

https://www.cnblogs.com/renhui/p/7463287.html
 */
class AudioRecordActivity : AppCompatActivity() {
    private var audioRecord: AudioRecord? = null
    private var mAudioTrack: AudioTrack? = null
    private var starting = false
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_record)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            0x99
        )

        findViewById<View>(R.id.btn_start).setOnClickListener {
            startRecord()
        }

        findViewById<View>(R.id.btn_stop).setOnClickListener {
            stopRecord()

        }

        findViewById<View>(R.id.btn_play).setOnClickListener {
            play()
        }

        findViewById<View>(R.id.btn_play_static).setOnClickListener {
            play(true)
        }

        findViewById<View>(R.id.btn_delete).setOnClickListener {
            delete()
        }
        findViewById<View>(R.id.tv_info).setOnClickListener {
            Thread {
                showMediaInfo()
            }.start()

        }
    }

    // 从MP4文件中提取视频并生成新的视频文件
    private fun showMediaInfo() {
//        val file: File = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), FILE_NAME)

        val file: File = File("/sdcard/apk/a1.mp4")
        if (!file.exists()) {
            return
        }
        val output = applicationContext.filesDir.absolutePath + "/a2.mp4"
        var inputStream: FileInputStream? = null
        try {
            val mediaExtractor = MediaExtractor()
            inputStream = FileInputStream(file)
            val fd = inputStream.fd
//            mediaExtractor.setDataSource(fd)
            mediaExtractor.setDataSource(file.absolutePath)
            val trackCount = mediaExtractor.trackCount
            val sb = StringBuilder()
            sb.append(file.absolutePath).append("\n")
            sb.append("trackCount = $trackCount").append("\n")
            var mediaMuxer: MediaMuxer? = null
            var frameRate = 0
            var trackIndex = 0
            (0 until trackCount).forEach { index ->
                val trackFormat = mediaExtractor.getTrackFormat(index)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                sb.append("     $index  ")
                sb.append("mime=").append(mime).append("\n")
                if (mime?.startsWith("video/") == true) {
                    frameRate = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                    sb.append(",frameRate=$frameRate")
                    mediaExtractor.selectTrack(index)
                    // path:输出文件的名称  format:输出文件的格式；当前只支持MP4格式；
                    mediaMuxer =
                        MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also {
                            // 添加通道；我们更多的是使用MediaCodec.getOutpurForma()或
                            // Extractor.getTrackFormat(int index)来获取MediaFormat;也可以自己创建；
                            trackIndex = it.addTrack(trackFormat)
                            it.start()

                        }
                }
            }
            runOnUiThread {
                updateInfo(sb.toString())
            }
            mediaMuxer?.let { mm ->
                val bufferInfo = MediaCodec.BufferInfo()
                bufferInfo.presentationTimeUs = 0
                val buffer = ByteBuffer.allocate(500 * 1024)
                var sampleSize = 0
                var flag = true
                while (flag) {
                    // 把指定通道中的数据按偏移量读取到ByteBuffer中；
                    sampleSize = mediaExtractor.readSampleData(buffer, 0)
                    if (sampleSize > 0) {
                        logger("extractor ... ","")
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                        bufferInfo.presentationTimeUs += 1000 * 1000 / frameRate
                        // 把ByteBuffer中的数据写入到在构造器设置的文件中；
                        mm.writeSampleData(trackIndex, buffer, bufferInfo)
                        // 读取下一帧数据
                        mediaExtractor.advance()
                    } else {
                        flag = false
                    }
                }
                mm.stop()
                mm.release()
            }
            mediaExtractor.release()

            runOnUiThread {
                updateInfo(sb.append("\n 合成结束！$output").toString())
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            runOnUiThread {
                updateInfo("showMediaInfo error = ${e.message} ,path = ${file.absolutePath}")
            }
        } finally {
            inputStream?.close()
        }


    }

    private fun delete() {
        stopRecord()
        stopPlay()
        val file: File = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), FILE_NAME)
        logger("File :[${file.absolutePath} ] ", "AudioRecord")
        if (file.exists()) {
            file.delete()
        }
    }

    private fun play(static: Boolean = false) {
        if (playing) {
            logger("playing ...", "AudioRecord")
            return
        }
        val file: File = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), FILE_NAME)
        logger("File :[${file.absolutePath} ] ", "AudioRecord")
        if (!file.exists()) {
            Toast.makeText(this, "不存在", Toast.LENGTH_SHORT).show()
            return
        }
        if (static) {
            playInModeStatic(file)
        } else {
            playInModeStream(file)
        }

    }

    private fun stopPlay() {
        playing = false
        mAudioTrack?.stop()
        mAudioTrack?.release()
    }

    /**
     * 播放，使用stream模式
     */
    private fun playInModeStream(file: File) {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val minBufferSize =
            AudioTrack.getMinBufferSize(SAMPLE_RATE_IN_HZ, channelConfig, AUDIO_FORMAT)
        mAudioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_IN_HZ)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(channelConfig)
                .build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also {
            it.play()
        }
        playing = true
        Thread {
            var inputStream: FileInputStream? = null
            try {
                val buff = ByteArray(minBufferSize)
                inputStream = FileInputStream(file)
                while (playing && inputStream.available() > 0) {
                    val count = inputStream.read(buff)
                    if (count == AudioTrack.ERROR_INVALID_OPERATION ||
                        count == AudioTrack.ERROR_BAD_VALUE
                    ) {
                        // nothing
                    } else {
                        if (count != 0 && count != -1) {
                            mAudioTrack?.write(buff, 0, count)
                        }
                    }
                }
                playing = false
                logger("play end", "AudioRecord")
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
            }
        }.start()
    }

    /**
     * 播放，static模式，需要将音频数据一次性write到AudioTrack的内部缓冲区
     */
    private fun playInModeStatic(file: File) {
        playing = true
        Thread {
            val out: ByteArrayOutputStream = ByteArrayOutputStream()
            var inputStream: FileInputStream? = null
            try {
                logger("start loading...", "AudioRecord")
                inputStream = FileInputStream(file)
                var has = true
                while (has) {
                    val b = inputStream.read()
                    if (b != -1) {
                        out.write(b)
                    } else {
                        has = false
                    }
                }
                logger("start play...", "AudioRecord")
                val data = out.toByteArray()
                mAudioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_IN_HZ)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    data.size,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                ).also {

                    // todo 如何回调播放结束
                    // it.setNotificationMarkerPosition(data.size/ AudioTrack.getMinBufferSize(SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT))
                    //it.setPositionNotificationPeriod(2)
                    // https://stackoverflow.com/questions/12323735/audiotrack-how-to-know-when-a-sound-begins-ends
                    it.setPlaybackPositionUpdateListener(object :
                        AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack?) {
                            logger("onMarkerReached play end", "AudioRecord")
                            playing = false
                        }

                        override fun onPeriodicNotification(track: AudioTrack?) {
                            logger("onPeriodicNotification", "AudioRecord")
                        }

                    })
                    it.write(data, 0, data.size)
                    it.play()

                    it.playState

                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                out.close()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecord()
        stopPlay()
    }

    private fun stopRecord() {
        starting = false
        audioRecord?.stop()
        audioRecord?.release()
    }

    private fun startRecord() {
        if (starting) {
            Toast.makeText(this, "已经在录音", Toast.LENGTH_SHORT).show()
            return
        }
        val minBufferSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        this.audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT, minBufferSize
        )
        val buffer = ByteArray(minBufferSize)
        val file: File = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), FILE_NAME)
        logger("File :[${file.absolutePath} ] ", "AudioRecord")
        if (file.exists()) {
            file.delete()
        } else {
            if (!file.mkdirs()) {
                logger("create file fail ：[${file.absolutePath} ] ", "AudioRecord")
                return
            }
            file.createNewFile()
        }
        audioRecord?.startRecording()
        starting = true
        logger("start...", "AudioRecord")
        Thread {
            var fileOutputStream: FileOutputStream? = null
            try {
                fileOutputStream = FileOutputStream(file)
                while (starting && audioRecord != null) {
                    val read = audioRecord?.read(buffer, 0, minBufferSize)
                    logger("read:$read", "AudioRecord")
                    if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                        fileOutputStream.write(buffer)
                    }
                }
                fileOutputStream.flush()
                starting = false
                logger("end", "AudioRecord")
                runOnUiThread {
                    Toast.makeText(this, "已停止录音", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                fileOutputStream?.close()
            }
        }.start()
    }

    private fun updateInfo(msg: String) {
        findViewById<TextView>(R.id.tv_info).text = msg
    }

    companion object {
        // 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
        private const val SAMPLE_RATE_IN_HZ = 44100

        // 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        // 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val FILE_NAME = "laputa_a.pcm"
    }
}