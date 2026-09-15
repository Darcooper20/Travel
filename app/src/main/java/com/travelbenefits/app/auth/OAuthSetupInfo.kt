package com.travelbenefits.app.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.travelbenefits.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three values Google Cloud Console asks for when registering the Android
 * OAuth client, read from the running app rather than from documentation that
 * can drift: the package name (which carries the debug suffix), the signing
 * certificate fingerprint, and the redirect scheme compiled into the manifest.
 *
 * The last one is the trap. Google sends the sign-in result to a custom scheme
 * that must be the reversed form of your client ID, and Android resolves that
 * scheme from the manifest, which is fixed at build time. So a build whose
 * scheme does not match your client can never finish sign-in, no matter how
 * correct the registration is. [mismatchFor] detects exactly that, so the app
 * can say so instead of letting the browser fail.
 */
@Singleton
class OAuthSetupInfo @Inject constructor(@ApplicationContext private val context: Context) {

    /** Register this as the package name. Note the .debug suffix on debug builds. */
    val packageName: String = BuildConfig.APPLICATION_ID

    /** The custom scheme this build listens on, baked into the manifest. */
    val redirectScheme: String = BuildConfig.APPAUTH_REDIRECT_SCHEME

    /** SHA-1 of the certificate this APK was signed with, formatted as Google expects. */
    val signingSha1: String? by lazy { runCatching { readSha1() }.getOrNull() }

    /**
     * The scheme a given client ID requires: "123-abc.apps.googleusercontent.com"
     * needs "com.googleusercontent.apps.123-abc". Null when the ID is not a
     * Google client ID at all.
     */
    fun requiredSchemeFor(clientId: String): String? = schemeForClientId(clientId)

    /**
     * The scheme this client ID needs, when this build does not listen on it.
     * Null means the build and the client agree, or the ID is unusable anyway.
     */
    fun mismatchFor(clientId: String): String? =
        requiredSchemeFor(clientId)?.takeIf { !it.equals(redirectScheme, ignoreCase = true) }

    companion object {
        private const val CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"

        /** Pure form of [requiredSchemeFor], so the reversal rule can be tested without a Context. */
        fun schemeForClientId(clientId: String?): String? {
            val trimmed = clientId?.trim().orEmpty()
            if (!trimmed.endsWith(CLIENT_ID_SUFFIX, ignoreCase = true)) return null
            val id = trimmed.dropLast(CLIENT_ID_SUFFIX.length)
            if (id.isBlank() || id.contains('/') || id.contains(':')) return null
            return "com.googleusercontent.apps.$id"
        }
    }

    private fun readSha1(): String? {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return null
            (signing.apkContentsSigners ?: return null).firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
