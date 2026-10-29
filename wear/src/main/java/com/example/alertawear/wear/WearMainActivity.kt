package com.example.alertawear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alertawear.wear.data.RealWearPhoneBridge
import com.example.alertawear.wear.ui.AlertaWearWatchApp
import com.example.alertawear.wear.ui.WearAlertViewModel

class WearMainActivity : ComponentActivity() {
    private var viewModelRef: WearAlertViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bridge = RealWearPhoneBridge(applicationContext)

        setContent {
            val viewModel: WearAlertViewModel = viewModel(
                factory = WearAlertViewModel.Factory(bridge)
            )
            viewModelRef = viewModel
            val uiState by viewModel.uiState.collectAsState()

            AlertaWearWatchApp(
                uiState = uiState,
                onSosLongPressed = viewModel::onSosLongPressed,
                onCancelCountdown = viewModel::cancelCountdown,
                onRetryConnection = viewModel::retryFromNoConnection,
                onRefreshConnection = viewModel::refreshConnectionStatus,
                onReturnToHome = viewModel::returnToHome
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModelRef?.refreshConnectionStatus()
    }
}
