package com.iqoo.launcher
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
class MainActivity : Activity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(TextView(this).apply { setText(R.string.scaffold_ready); textSize=18f }) } }
