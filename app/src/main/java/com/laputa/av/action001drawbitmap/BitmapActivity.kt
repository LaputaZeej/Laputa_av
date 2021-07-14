package com.laputa.av.action001drawbitmap

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageView
import com.laputa.av.R

class BitmapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bitmap)

        val bitmap = BitmapFactory.decodeStream(  applicationContext.assets.open("a.jpg"))

        findViewById<ImageView>(R.id.iv).setImageBitmap(bitmap)
        findViewById<SurfaceView>(R.id.surface).holder.addCallback(object :SurfaceHolder.Callback2{
            override fun surfaceCreated(holder: SurfaceHolder) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                }
                val canvas = holder.lockCanvas()
                canvas.drawBitmap(bitmap,0f,0f,paint)
                holder.unlockCanvasAndPost(canvas)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

            override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
            }

        })
    }


}