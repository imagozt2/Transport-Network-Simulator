package com.rmm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rmm.app.navigation.RMMNavigation
import com.rmm.app.ui.theme.RMMAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RMMAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RMMApp()
                }
            }
        }
    }
}

@Composable
fun RMMApp(modifier: Modifier = Modifier) {
    RMMNavigation(modifier = modifier.fillMaxSize())
}

@Preview(showBackground = true)
@Composable
private fun RMMAppPreview() {
    RMMAppTheme {
        RMMApp()
    }
}

