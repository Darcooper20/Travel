package com.travelbenefits.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.travelbenefits.app.data.local.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** What the user picked on the "how do you want to start" step; the nav host opens the matching screen. */
enum class OnboardingChoice { MANUAL, IMPORT, CONNECT, SKIP }

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val appPrefs: AppPrefs) : ViewModel() {
    val onboardingDone: StateFlow<Boolean> = appPrefs.onboardingDone
    fun finish() = appPrefs.setOnboardingDone(true)
}
