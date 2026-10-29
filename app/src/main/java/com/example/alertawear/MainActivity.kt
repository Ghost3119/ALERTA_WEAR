package com.example.alertawear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alertawear.data.AlertEventBus
import com.example.alertawear.data.FusedPhoneLocationProvider
import com.example.alertawear.data.RealLocationAlertGateway
import com.example.alertawear.data.RealWatchConnectionMonitor
import com.example.alertawear.data.SharedPrefsAlertStorage
import com.example.alertawear.model.AlertOrigin
import com.example.alertawear.ui.MobileAlertViewModel
import com.example.alertawear.ui.screens.AlertaWearMobileApp
import com.example.alertawear.ui.theme.AlertaWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContext = applicationContext
        val storage = SharedPrefsAlertStorage(appContext)
        val locationProvider = FusedPhoneLocationProvider(appContext)
        val gateway = RealLocationAlertGateway(locationProvider)
        val watchMonitor = RealWatchConnectionMonitor(appContext)

        setContent {
            AlertaWearTheme {
                val viewModel: MobileAlertViewModel = viewModel(
                    factory = MobileAlertViewModel.Factory(
                        storage = storage,
                        gateway = gateway,
                        watchConnectionMonitor = watchMonitor,
                        locationProvider = locationProvider
                    )
                )
                val uiState by viewModel.uiState.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    viewModel.onLocationPermissionResult(granted)
                }

                val requestPermissionsAction = {
                    val perms = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(perms)
                }

                // Solicitar permisos de ubicación y notificaciones al iniciar si no se han concedido
                LaunchedEffect(Unit) {
                    if (!locationProvider.hasForegroundLocationPermission()) {
                        requestPermissionsAction()
                    }
                }

                AlertaWearMobileApp(
                    uiState = uiState,
                    onNavigate = viewModel::navigateTo,
                    onRefreshStatus = viewModel::refreshDeviceAndLocationStatus,
                    onRequestLocationPermission = requestPermissionsAction,
                    onDraftNameChange = viewModel::onDraftNameChanged,
                    onDraftPhoneChange = viewModel::onDraftPhoneChanged,
                    onSaveContact = viewModel::saveContact,
                    onSosLongPressed = {
                        viewModel.startSosCountdown(AlertOrigin.PHONE_LOCAL)
                    },
                    onCancelCountdown = viewModel::cancelCountdown,
                    onCancelActiveAlert = viewModel::cancelActiveAlert
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlertEventBus.setUiInForeground(true)
    }

    override fun onPause() {
        AlertEventBus.setUiInForeground(false)
        super.onPause()
    }
}
