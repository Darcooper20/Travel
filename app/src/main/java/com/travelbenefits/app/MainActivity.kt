package com.travelbenefits.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.ui.navigation.AppNavHost
import com.travelbenefits.app.ui.theme.TravelBenefitsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var gmailAuthManager: GmailAuthManager

    private val gmailAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        lifecycleScope.launch { gmailAuthManager.handleRedirect(data) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TravelBenefitsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(onLaunchGmailAuth = { intent -> gmailAuthLauncher.launch(intent) })
                }
            }
        }
    }
}
