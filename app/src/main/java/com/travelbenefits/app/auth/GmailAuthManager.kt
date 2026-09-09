package com.travelbenefits.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.travelbenefits.app.BuildConfig
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.remote.gmail.GmailApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Gmail sign-in with AppAuth (OAuth2 + PKCE) rather than Google Play
 * Services' sign-in libraries, on purpose: AppAuth has no Play Services
 * dependency, so this keeps working on sideloaded installs on devices
 * without Google Play Services (custom ROMs, de-Googled devices, etc).
 *
 * Requires an Android OAuth client registered in Google Cloud Console for
 * this app's applicationId + signing certificate SHA-1 (see README.md) -
 * that registration is what makes the "reversed client ID" redirect scheme
 * below resolve back to this app.
 */
@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePrefs: SecurePrefs,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )

    private val _isSignedIn = MutableStateFlow(loadAuthState() != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private fun loadAuthState(): AuthState? {
        val json = securePrefs.gmailAuthStateJson ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAuthState(state: AuthState) {
        securePrefs.gmailAuthStateJson = state.jsonSerializeString()
    }

    /**
     * This MUST be the same scheme registered in the manifest (the
     * `appAuthRedirectScheme` Gradle property baked in at build time) - see
     * the comment in app/build.gradle.kts. It is deliberately NOT derived
     * from the client ID typed into Settings at runtime: that value can't
     * affect the already-compiled manifest, so deriving it here would only
     * work by coincidence when the two happen to match.
     */
    fun redirectUri(): Uri = Uri.parse("${BuildConfig.APPAUTH_REDIRECT_SCHEME}:/oauth2redirect")

    /** Builds the Intent to launch (via an Activity's ActivityResultLauncher) to start Google sign-in. */
    fun createAuthIntent(clientId: String): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri(),
        )
            .setScopes(GmailApi.SCOPE_GMAIL_READONLY)
            // prompt=consent (via its own builder method - AppAuth rejects it
            // as an "additional" parameter since it's a reserved OAuth param)
            // plus access_type=offline so Google actually issues a refresh
            // token (otherwise you'd need to re-consent every ~1 hour).
            .setPrompt("consent")
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()

        val authService = AuthorizationService(context)
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Call from the Activity that receives the redirect Intent (see MainActivity's ActivityResultLauncher). */
    suspend fun handleRedirect(intent: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        if (response == null) {
            return Result.failure(exception ?: Exception("Google sign-in was cancelled."))
        }

        val authService = AuthorizationService(context)
        return try {
            val tokenResponse = suspendCancellableCoroutine<TokenResponse> { cont ->
                authService.performTokenRequest(response.createTokenExchangeRequest()) { resp, ex ->
                    if (resp != null) {
                        cont.resume(resp)
                    } else {
                        cont.resumeWithException(ex ?: Exception("Token exchange failed"))
                    }
                }
            }
            val newState = AuthState(response, exception)
            newState.update(tokenResponse, null)
            saveAuthState(newState)
            _isSignedIn.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            authService.dispose()
        }
    }

    /** Returns a valid access token, transparently refreshing it if it's expired. Throws if never signed in. */
    suspend fun getFreshAccessToken(): String {
        val state = loadAuthState() ?: throw IllegalStateException("Not signed in to Google. Connect Gmail in Settings first.")
        val authService = AuthorizationService(context)
        return try {
            suspendCancellableCoroutine { cont ->
                state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                    if (accessToken != null) {
                        saveAuthState(state)
                        cont.resume(accessToken)
                    } else {
                        cont.resumeWithException(ex ?: Exception("Failed to refresh Google access token"))
                    }
                }
            }
        } finally {
            authService.dispose()
        }
    }

    fun signOut() {
        securePrefs.clearGmailAuth()
        _isSignedIn.value = false
    }
}
