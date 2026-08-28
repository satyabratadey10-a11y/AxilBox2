package com.axilbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import com.axilbox.app.ui.navigation.AxilBoxNavHost
import com.axilbox.app.ui.theme.AxilBoxTheme
import com.axilbox.app.ui.viewmodel.InstanceViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: InstanceViewModel by viewModels {
        val app = application as AxilBoxApplication
        InstanceViewModel.Factory(
            app.repository,
            app.systemResourceProvider,
            app.engineProvisioner,
            app.qemuProcessRunner
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AxilBoxTheme {
                val navController = rememberNavController()
                AxilBoxNavHost(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}
