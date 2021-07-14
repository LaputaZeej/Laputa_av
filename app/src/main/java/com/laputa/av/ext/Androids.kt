package com.laputa.av.ext

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.laputa.av.R

/**
 * Author by xpl, Date on 2021/7/7.
 */

inline fun <reified T : Activity> Activity.extStartActivity(bundle: Bundle? = null) {
    startActivity(Intent(this, T::class.java).apply {
        bundle?.let {
            this.putExtras(it)
        }
    })
}

inline fun <reified T : Activity> Activity.extStartActivityForResult(
    code: Int,
    bundle: Bundle? = null
) {
    startActivityForResult(Intent(this, T::class.java).apply {
        bundle?.let {
            this.putExtras(it)
        }
    }, code)
}

inline fun <reified T : Fragment> newInstance(bundle: Bundle? = null): Fragment {
    return T::class.java.newInstance().apply {
        bundle?.let {
            arguments = it
        }
    }
}

fun logger(msg: String, subTag: String, tag: String = "__av") {
    Log.i("${tag}_${subTag}", msg)
}

fun TextView.extSetText(msg: String, ui: Boolean = true) {
    if (ui) {
        text = msg
    } else {
        this.post {
            text = msg
        }
    }
}