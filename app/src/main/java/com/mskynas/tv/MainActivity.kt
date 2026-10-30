package com.mskynas.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Session
import com.mskynas.tv.ui.AppNav
import com.mskynas.tv.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Plex.init(Session(applicationContext))
        setContent {
            AppTheme {
                AppNav()
            }
        }
    }
}
