package com.laputa.av

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.laputa.av.action001drawbitmap.BitmapActivity
import com.laputa.av.action002audiorecordpcm.AudioRecordActivity
import com.laputa.av.ext.extStartActivity
import com.laputa.av.record.RecordActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.action_01).setOnClickListener {
            extStartActivity<BitmapActivity>()
        }
        findViewById<View>(R.id.action_02).setOnClickListener {
            extStartActivity<AudioRecordActivity>()
        }

        findViewById<View>(R.id.action_03).setOnClickListener {
            extStartActivity<RecordActivity>()
        }

    }
}