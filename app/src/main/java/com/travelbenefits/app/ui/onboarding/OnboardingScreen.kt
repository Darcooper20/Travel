package com.travelbenefits.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Three-step guided setup shown on first launch (and again from Settings).
 * It never asks for a key or a login: the point is to make clear that the
 * app is useful with nothing but manual entries, and that every connection
 * is optional, read-only and explained before it is offered.
 */
object OnboardingText {
    const val TITLE_1 = "Welcome to your loyalty wallet"
    const val TITLE_2 = "How do you want to start?"
    const val TITLE_3 = "What stays private"
    const val BUTTON_FINISH = "Open the app"
    const val BUTTON_SKIP = "Skip setup"
}

@Composable
fun OnboardingScreen(onFinished: (OnboardingChoice) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var choice by remember { androidx.compose.runtime.mutableStateOf(OnboardingChoice.MANUAL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("onboarding"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        when (step) {
            0 -> {
                Text(OnboardingText.TITLE_1, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "One place for hotel and airline programs, shop rewards, the credit cards you carry, the credits and certificates they come with, and the trips you have booked. " +
                        "It works out which card to use for a purchase, what is about to expire, and what you expected to earn versus what actually arrived.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("What it never does", style = MaterialTheme.typography.titleMedium)
                Bullet("Never transfers points, books travel or changes anything in your accounts. Every suggestion ends with you doing it on the program's own site.")
                Bullet("Never sends your data to a cloud service of ours - there is none. Data lives on this phone; export a backup when you want a copy.")
                Bullet("Never shows an estimate as a fact: rules carry an \"as of\" date and a source, AI research is labelled, and unknown values are shown as unknown rather than as zero.")
            }
            1 -> {
                Text(OnboardingText.TITLE_2, style = MaterialTheme.typography.headlineSmall)
                Text("Pick one now; the other routes are always available in Settings.", style = MaterialTheme.typography.bodyMedium)
                ChoiceCard(
                    selected = choice == OnboardingChoice.MANUAL,
                    title = "Add cards and programs by hand",
                    body = "Pick your cards from the built-in catalog and type in balances and member numbers. No account, key or connection needed. Recommended first step.",
                ) { choice = OnboardingChoice.MANUAL }
                ChoiceCard(
                    selected = choice == OnboardingChoice.IMPORT,
                    title = "Import a backup",
                    body = "Restore a JSON export made by this app on another phone or an earlier install.",
                ) { choice = OnboardingChoice.IMPORT }
                ChoiceCard(
                    selected = choice == OnboardingChoice.CONNECT,
                    title = "Set up optional connections",
                    body = "Gmail (read-only, needs your own Google OAuth client and an Anthropic API key, pay-as-you-go), bank transactions through Plaid (needs a small worker you host; free developer tier covers a few accounts), and seats.aero award inventory (paid Pro key). Each is explained on the Settings screen before you connect it.",
                ) { choice = OnboardingChoice.CONNECT }
            }
            else -> {
                Text(OnboardingText.TITLE_3, style = MaterialTheme.typography.headlineSmall)
                Bullet("API keys and OAuth tokens are stored in Android's encrypted preferences and are excluded from backups, exports and logs.")
                Bullet("Gmail access is read-only. Only the emails matching loyalty, booking, shop and card-statement senders are read, and only the extracted fields are kept.")
                Bullet("Email and web content is treated as untrusted input: it is fenced as data when sent to the model, so text inside an email cannot change what the app does.")
                Bullet("You can disconnect any source, clear the scan history or delete everything from Settings at any time.")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Back") }
            if (step < 2) Button(onClick = { step++ }) { Text("Next") }
            else Button(onClick = { onFinished(choice) }) { Text(OnboardingText.BUTTON_FINISH) }
        }
        TextButton(onClick = { onFinished(OnboardingChoice.SKIP) }) { Text(OnboardingText.BUTTON_SKIP) }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ChoiceCard(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else androidx.compose.material3.CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((if (selected) "◉ " else "○ ") + title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
