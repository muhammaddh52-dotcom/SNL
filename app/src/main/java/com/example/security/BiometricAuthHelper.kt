package com.example.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.model.UiLanguage
import com.example.ui.theme.SnlApproveGreen
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlNavyLight
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlYellow

/**
 * Status of hardware biometric sensors (Fingerprint & Facial Recognition) on the current device.
 */
enum class BiometricCapabilityStatus {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    UNSUPPORTED
}

/**
 * Helper encapsulating the AndroidX [BiometricPrompt] and [BiometricManager] APIs for secure
 * fingerprint and facial recognition authentication in the SNL Lubricants ordering platform.
 */
object BiometricAuthHelper {

    private const val PREFS_NAME = "snl_biometric_security_prefs"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_quick_access_enabled"
    private const val KEY_LAST_VERIFIED_EMAIL = "biometric_last_verified_email"

    /**
     * Resolves the hosting [FragmentActivity] from any Compose [Context].
     */
    fun findFragmentActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Checks device support for Fingerprint, Facial Recognition, or Device Credential via [BiometricManager].
     */
    fun checkBiometricCapability(context: Context): BiometricCapabilityStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators =
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapabilityStatus.HW_UNAVAILABLE
            else -> {
                // Fallback check for biometric-only sensors on older API levels
                when (biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.AVAILABLE
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NONE_ENROLLED
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus.NO_HARDWARE
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapabilityStatus.HW_UNAVAILABLE
                    else -> BiometricCapabilityStatus.UNSUPPORTED
                }
            }
        }
    }

    fun isBiometricQuickAccessEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricQuickAccessEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun saveLastVerifiedEmail(context: Context, email: String) {
        if (email.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_VERIFIED_EMAIL, email)
            .apply()
    }

    fun getLastVerifiedEmail(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_VERIFIED_EMAIL, "")
            .orEmpty()
    }

    fun getCapabilityDescription(status: BiometricCapabilityStatus, isUrdu: Boolean): String {
        return when (status) {
            BiometricCapabilityStatus.AVAILABLE -> if (isUrdu) {
                "فنگر پرنٹ اور چہرے کی شناخت (BiometricPrompt) فعال اور تیار ہے"
            } else {
                "Fingerprint & Facial Recognition (BiometricPrompt) Ready"
            }
            BiometricCapabilityStatus.NONE_ENROLLED -> if (isUrdu) {
                "ڈیوائس میں کوئی فنگر پرنٹ یا فیس آئی ڈی رجسٹرڈ نہیں ہے (سیٹنگز میں شامل کریں)"
            } else {
                "No fingerprint or face enrolled in device settings"
            }
            BiometricCapabilityStatus.NO_HARDWARE -> if (isUrdu) {
                "اس ڈیوائس پر بائیو میٹرک سینسر دستیاب نہیں ہے"
            } else {
                "Biometric hardware sensor not detected on this device"
            }
            BiometricCapabilityStatus.HW_UNAVAILABLE -> if (isUrdu) {
                "بائیو میٹرک سینسر اس وقت مصروف ہے، دوبارہ کوشش کریں"
            } else {
                "Biometric hardware is temporarily unavailable"
            }
            BiometricCapabilityStatus.UNSUPPORTED -> if (isUrdu) {
                "بائیو میٹرک تصدیق اس ڈیوائس پر سپورٹ نہیں ہے"
            } else {
                "Biometric authentication is not supported on this device"
            }
        }
    }

    /**
     * Launches the native Android [BiometricPrompt] dialog for Fingerprint or Facial Recognition.
     */
    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        description: String,
        negativeButtonText: String = "Use Account Credentials",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError(
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                "BiometricPrompt requires an active FragmentActivity host."
            )
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val biometricManager = BiometricManager.from(context)

        val combinedAuthenticators =
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        val biometricOnlyAuthenticators =
            Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setConfirmationRequired(false)

        if (biometricManager.canAuthenticate(combinedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            promptInfoBuilder.setAllowedAuthenticators(combinedAuthenticators)
        } else {
            promptInfoBuilder
                .setAllowedAuthenticators(biometricOnlyAuthenticators)
                .setNegativeButtonText(negativeButtonText)
        }

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onError(
                BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                e.localizedMessage ?: "Unable to launch BiometricPrompt."
            )
        }
    }
}

/**
 * Full-screen Biometric Security Gate overlay that protects the authenticated SNL B2B ordering platform
 * until the user verifies their identity via Fingerprint or Facial Recognition (`BiometricPrompt`).
 */
@Composable
fun BiometricSecurityGateOverlay(
    language: UiLanguage,
    userEmail: String,
    businessName: String,
    onUnlocked: () -> Unit,
    onSignOutFallback: () -> Unit
) {
    val context = LocalContext.current
    val isUr = language == UiLanguage.URDU
    val capabilityStatus = remember { BiometricAuthHelper.checkBiometricCapability(context) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var authStatusMessage by remember { mutableStateOf<String?>(null) }

    val triggerBiometricPrompt = {
        authErrorMessage = null
        authStatusMessage = null
        BiometricAuthHelper.authenticate(
            context = context,
            title = if (isUr) "ایس این ایل آرڈرنگ پلیٹ فارم ان لاک کریں" else "SNL B2B Platform Biometric Unlock",
            subtitle = if (isUr) "فنگر پرنٹ یا چہرے کی شناخت (Face ID)" else "Fingerprint or Facial Recognition",
            description = if (isUr) {
                "اپنے ڈیلر/ڈسٹری بیوٹر اکاؤنٹ اور مخصوص قیمتوں تک محفوظ رسائی کے لیے تصدیق کریں۔"
            } else {
                "Authenticate with your fingerprint or face to access authorized B2B tier prices and ordering."
            },
            negativeButtonText = if (isUr) "لاگ آؤٹ" else "Cancel",
            onSuccess = {
                BiometricAuthHelper.saveLastVerifiedEmail(context, userEmail)
                authStatusMessage = if (isUr) "بائیو میٹرک تصدیق کامیاب!" else "Biometric verification succeeded!"
                onUnlocked()
            },
            onError = { _, errMsg ->
                authErrorMessage = errMsg
            },
            onFailed = {
                authErrorMessage = if (isUr) {
                    "بائیو میٹرک شناخت مماثل نہیں ہوئی۔ دوبارہ کوشش کریں۔"
                } else {
                    "Biometric not recognized. Please try again."
                }
            }
        )
    }

    // Automatically trigger the BiometricPrompt when the lock screen appears if hardware is available
    LaunchedEffect(Unit) {
        if (capabilityStatus == BiometricCapabilityStatus.AVAILABLE) {
            triggerBiometricPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SnlNavy, SnlNavyLight, Color(0xFF071629))
                )
            )
            .padding(24.dp)
            .testTag("biometric_lock_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SnlNavy.copy(alpha = 0.1f),
                    modifier = Modifier.size(84.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint Authentication",
                            tint = SnlRed,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Facial Recognition Authentication",
                            tint = SnlNavy,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = if (isUr) "بائیو میٹرک سیکیورٹی لاک" else "Biometric Security Lock",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (businessName.isNotBlank()) "$businessName ($userEmail)" else userEmail,
                    style = MaterialTheme.typography.labelLarge,
                    color = SnlRed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isUr) {
                        "ایس این ایل نیشنل لبریکنٹس پلیٹ فارم تک رسائی کے لیے اپنے فنگر پرنٹ یا چہرے کی شناخت (BiometricPrompt) سے تصدیق کریں۔"
                    } else {
                        "Authenticate using your fingerprint or facial recognition via Android BiometricPrompt to access the SNL ordering platform."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (capabilityStatus == BiometricCapabilityStatus.AVAILABLE) SnlApproveGreen else SnlYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = BiometricAuthHelper.getCapabilityDescription(capabilityStatus, isUr),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (authErrorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_lock_error_banner")
                    ) {
                        Text(
                            text = authErrorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (authStatusMessage != null) {
                    Surface(
                        color = SnlApproveGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authStatusMessage.orEmpty(),
                            color = SnlApproveGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { triggerBiometricPrompt() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnlNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("biometric_unlock_prompt_button")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUr) {
                            "فنگر پرنٹ / فیس آئی ڈی سے ان لاک کریں"
                        } else {
                            "Unlock with Fingerprint / Face"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (capabilityStatus != BiometricCapabilityStatus.AVAILABLE) {
                    OutlinedButton(
                        onClick = onUnlocked,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("biometric_bypass_when_unavailable_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isUr) {
                                "تصدیق شدہ سیشن کے ساتھ جاری رکھیں"
                            } else {
                                "Continue with Verified Session"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOutFallback,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("biometric_lock_sign_out_button")
                ) {
                    Text(
                        text = if (isUr) "اکاؤنٹ تبدیل کریں / سائن آؤٹ" else "Switch Account / Sign Out",
                        color = SnlRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
