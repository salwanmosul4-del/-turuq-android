package iq.turuq.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import iq.turuq.app.ui.TuruqRoot
import iq.turuq.core.TuruqAppProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as TuruqAppProvider).turuq
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                var granted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    granted = it[Manifest.permission.ACCESS_FINE_LOCATION] == true
                }
                LaunchedEffect(granted) {
                    if (granted) { app.location.start(); app.startPolling() }
                    else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                TuruqRoot(app, onNavStateChanged = { on ->
                    val i = Intent(this, NavForegroundService::class.java)
                    if (on) ContextCompat.startForegroundService(this, i) else stopService(i)
                })
            }
        }
    }
}
