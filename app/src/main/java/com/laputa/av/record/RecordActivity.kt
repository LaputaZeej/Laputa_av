package com.laputa.av.record

import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.laputa.av.R
import com.laputa.av.ext.extSetText
import com.laputa.av.ext.logger

class RecordActivity : AppCompatActivity() {

    private var mSurfaceHolder: SurfaceHolder? = null
    private var mCamera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            0x99
        )

        updateInfo("录制音视频 注意权限 \n RECORD_AUDIO、WRITE_EXTERNAL_STORAGE、READ_EXTERNAL_STORAGE")

        findViewById<View>(R.id.btn_start).setOnClickListener {
            startCamera()
            MediaMuxerThread.startMuxer()
        }

        findViewById<View>(R.id.btn_stop).setOnClickListener {
            stopCamera()
            MediaMuxerThread.stopMuxer()
        }

        findViewById<View>(R.id.btn_play).setOnClickListener {


        }

        findViewById<View>(R.id.btn_delete).setOnClickListener {


        }

        findViewById<View>(R.id.tv_info).setOnClickListener {


        }

        initCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }

    private fun initCamera() {
        val surfaceView = findViewById<SurfaceView>(R.id.surface_view)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback2 {
            override fun surfaceCreated(holder: SurfaceHolder) {
                logger("surfaceCreated", "initCamera ${holder.hashCode()}")
                mSurfaceHolder = holder
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                logger("surfaceChanged", "initCamera")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                logger("surfaceDestroyed", "initCamera ${holder.hashCode()}")
            }

            override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
                logger("surfaceRedrawNeeded", "initCamera ${holder.hashCode()}")
            }
        })
    }

    private fun startCamera() {
        logger("startCamera", "RecordActivity")
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK).apply {
                setDisplayOrientation(Config.S_DEGREES)
                parameters.setPreviewSize(Config.S_WIDTH, Config.S_HEIGHT)
                setPreviewDisplay(mSurfaceHolder)
                setPreviewCallback { data, camera ->
                    //logger("onPreviewFrame ${data.size}", "startCamera")
                    if (data!=null){
                        MediaMuxerThread.addVideoFrameData(data)
                    }
                }
            }.also {
                it.startPreview()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            logger("startCamera error ${e.message}", "RecordActivity")
        }
    }

    private fun stopCamera() {
        logger("stopCamera", "RecordActivity")
        mCamera?.setPreviewCallback(null)
        mCamera?.stopPreview()
        mCamera = null
    }

    private fun updateInfo(msg: String, ui: Boolean = true) {
        findViewById<TextView>(R.id.tv_info).extSetText(msg, ui)
    }

    companion object{

    }
}