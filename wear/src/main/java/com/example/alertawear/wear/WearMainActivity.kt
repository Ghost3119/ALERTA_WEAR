package com.example.alertawear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alertawear.wear.ui.AlertaWearWatchApp
import com.example.alertawear.wear.ui.WearAlertViewModel

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WearAlertViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            AlertaWearWatchApp(
                uiState = uiState,
                onSosLongPressed = viewModel::onSosLongPressed,
                onCancelCountdown = viewModel::cancelCountdown,
                onRetryConnection = viewModel::retryFromNoConnection,
                onToggleDemoConnectionMode = viewModel::toggleDemoConnectionMode,
                onNavigateToScreen = viewModel::navigateToScreen
            )
        }
    }
}
