package com.janhelmich.deixis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.janhelmich.deixis.ui.DeixisApp
import com.janhelmich.deixis.ui.theme.DeixisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DeixisApplication).container
        setContent {
            DeixisTheme {
                DeixisApp(container)
            }
        }
    }
}
