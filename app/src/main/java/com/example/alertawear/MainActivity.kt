package com.example.alertawear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alertawear.data.SharedPrefsAlertStorage
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.ui.MobileAlertViewModel
import com.example.alertawear.ui.screens.AlertaWearMobileApp
import com.example.alertawear.ui.theme.AlertaWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = SharedPrefsAlertStorage(applicationContext)

        setContent {
            AlertaWearTheme {
                val viewModel: MobileAlertViewModel = viewModel(
                    factory = MobileAlertViewModel.Factory(storage)
                )
                val uiState by viewModel.uiState.collectAsState()

                AlertaWearMobileApp(
                    uiState = uiState,
                    onNavigate = viewModel::navigateTo,
                    onDraftNameChange = viewModel::onDraftNameChanged,
                    onDraftPhoneChange = viewModel::onDraftPhoneChanged,
                    onSaveContact = viewModel::saveContact,
                    onFillSampleContact = viewModel::fillSampleContact,
                    onSosLongPressed = {
                        viewModel.startSosCountdown(AlertOrigin.PHONE_LOCAL)
                    },
                    onCancelCountdown = viewModel::cancelCountdown,
                    onSimulateWatchAlert = {
                        viewModel.triggerAlertPreparation(AlertOrigin.WATCH_SIMULATED)
                    },
                    onSimulateErrorAlert = viewModel::simulateErrorState,
                    onCancelActiveAlert = viewModel::cancelActiveAlert
                )
            }
        }
    }
}
