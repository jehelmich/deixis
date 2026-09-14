package com.janhelmich.coeus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.janhelmich.coeus.ui.CoeusApp
import com.janhelmich.coeus.ui.theme.CoeusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CoeusApplication).container
        setContent {
            CoeusTheme {
                CoeusApp(container)
            }
        }
    }
}
