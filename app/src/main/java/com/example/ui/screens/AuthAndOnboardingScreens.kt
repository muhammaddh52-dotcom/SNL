package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.PendingRegistrationDraft
import com.example.auth.PendingRegistrationStore
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.auth.sendFirebasePasswordReset
import com.example.data.model.UiLanguage
import com.example.data.model.UserProfile
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricCapabilityStatus
import com.example.ui.theme.SnlApproveGreen
import com.example.ui.theme.SnlGreenAccept
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlNavyLight
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlYellow
import com.example.util.InvoiceAndReportPdfGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class AuthScreenTab {
    LOGIN,
    REGISTER_DEALER_DISTRIBUTOR
}

@Composable
fun SnlAuthScreen(
    language: UiLanguage,
    onToggleLanguage: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var activeTab by rememberSaveable { mutableStateOf(AuthScreenTab.LOGIN) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmPasswordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Dealer / Distributor Registration fields
    var selectedRole by rememberSaveable { mutableStateOf("DEALER") }
    var fullNameInput by rememberSaveable { mutableStateOf("") }
    var businessNameInput by rememberSaveable { mutableStateOf("") }
    var phoneInput by rememberSaveable { mutableStateOf("+92 3") }
    var cityInput by rememberSaveable { mutableStateOf("Karachi") }
    var addressInput by rememberSaveable { mutableStateOf("") }

    // Forgot Password dialog state
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var forgotPasswordEmail by rememberSaveable { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val biometricCapability = remember { BiometricAuthHelper.checkBiometricCapability(context) }
    val lastVerifiedBiometricEmail = remember { BiometricAuthHelper.getLastVerifiedEmail(context) }

    LaunchedEffect(Unit) {
        if (emailInput.isBlank() && lastVerifiedBiometricEmail.isNotBlank()) {
            emailInput = lastVerifiedBiometricEmail
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    val isUr = language == UiLanguage.URDU

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SnlNavy, SnlNavyLight, Color(0xFF071629))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onToggleLanguage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("auth_language_toggle")
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Switch Language")
                    Spacer(Modifier.width(6.dp))
                    Text(if (isUr) "English" else "اردو (Urdu)")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Official Original SNL Logo Card (Preserving exact design, proportions & colors)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier.size(144.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_snl_logo),
                    contentDescription = "SAIFI NATIONAL LUBRICANTS Official Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "SAIFI NATIONAL LUBRICANTS",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isUr) {
                    "ایس این ایل لبریکنٹس — آفیشل ڈسٹری بیوٹر اور ڈیلر آرڈرنگ پلیٹ فارم (پاکستان)"
                } else {
                    "SNL LUBRICANTS — Official B2B Distributor & Dealer Ordering Platform"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SnlYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "POWER. PROTECTION. PERFORMANCE. | KARACHI, PAKISTAN",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Mode Switcher: Login vs. Register (Dealers / Distributors)
                    TabRow(
                        selectedTabIndex = activeTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = SnlNavy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == AuthScreenTab.LOGIN,
                            onClick = {
                                activeTab = AuthScreenTab.LOGIN
                                errorMessage = null
                                infoMessage = null
                            },
                            text = {
                                Text(
                                    text = if (isUr) "لاگ ان (Login)" else "Login",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier.testTag("tab_login")
                        )
                        Tab(
                            selected = activeTab == AuthScreenTab.REGISTER_DEALER_DISTRIBUTOR,
                            onClick = {
                                activeTab = AuthScreenTab.REGISTER_DEALER_DISTRIBUTOR
                                errorMessage = null
                                infoMessage = null
                            },
                            text = {
                                Text(
                                    text = if (isUr) "نیا ڈیلر / ڈسٹری بیوٹر (Register)" else "Register Dealer / Dist.",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier.testTag("tab_register")
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Icon(
                        imageVector = if (activeTab == AuthScreenTab.LOGIN) Icons.Default.Lock else Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = SnlRed,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (activeTab == AuthScreenTab.LOGIN) {
                            if (isUr) "محفوظ کارپوریٹ لاگ ان" else "Authorized B2B Login"
                        } else {
                            if (isUr) "ڈیلر اور ڈسٹری بیوٹر رجسٹریشن" else "New Dealer & Distributor Registration"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (activeTab == AuthScreenTab.LOGIN) {
                            if (isUr) {
                                "اپنا ای میل اور پاس ورڈ درج کریں یا گوگل کے ذریعے تصدیق شدہ لاگ ان کریں۔"
                            } else {
                                "Enter your registered email & password or authenticate with Google to access authorized tier pricing."
                            }
                        } else {
                            if (isUr) {
                                "نئے ڈیلر یا ڈسٹری بیوٹر اکاؤنٹ کے لیے تفصیلات درج کریں۔ ایڈمن کی منظوری کے بعد مخصوص قیمتیں فعال ہو جائیں گی۔"
                            } else {
                                "Register your dealership or distribution business. Accounts are activated upon SNL Admin approval."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("auth_error_banner")
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (infoMessage != null) {
                        Surface(
                            color = SnlApproveGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("auth_info_banner")
                        ) {
                            Text(
                                text = infoMessage.orEmpty(),
                                color = SnlApproveGreen,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Email Input Field (shared across Login & Register)
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            errorMessage = null
                        },
                        label = {
                            Text(if (isUr) "ای میل ایڈریس (Email Address)" else "Email Address *")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    // Password Input Field (shared across Login & Register)
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorMessage = null
                        },
                        label = {
                            Text(if (isUr) "پاس ورڈ (Password)" else "Password *")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = "Password")
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("toggle_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide Password" else "Show Password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (activeTab == AuthScreenTab.LOGIN) ImeAction.Done else ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    if (activeTab == AuthScreenTab.LOGIN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    forgotPasswordEmail = emailInput
                                    showForgotPasswordDialog = true
                                },
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Icon(
                                    Icons.Default.LockReset,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = SnlNavy
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isUr) "پاس ورڈ بھول گئے؟ (Forgot Password?)" else "Forgot Password?",
                                    color = SnlNavy,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Primary Login Button interfacing with Firebase Authentication via CredentialManager
                        Button(
                            onClick = {
                                if (emailInput.isBlank() || !emailInput.contains("@")) {
                                    errorMessage = if (isUr) {
                                        "براہ کرم درست ای میل ایڈریس درج کریں۔"
                                    } else {
                                        "Please enter a valid email address."
                                    }
                                    return@Button
                                }
                                if (passwordInput.length < 6) {
                                    errorMessage = if (isUr) {
                                        "پاس ورڈ کم از کم 6 حروف کا ہونا چاہیے۔"
                                    } else {
                                        "Password must be at least 6 characters."
                                    }
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        BiometricAuthHelper.saveLastVerifiedEmail(context, emailInput.trim())
                                        onAuthSuccess()
                                    },
                                    onAuthError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLoading = false
                                    }
                                )
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isUr) "لاگ ان کریں (Sign In)" else "Sign In",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Direct Google Sign-In Button
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        BiometricAuthHelper.saveLastVerifiedEmail(
                                            context,
                                            Firebase.auth.currentUser?.email ?: emailInput.trim()
                                        )
                                        onAuthSuccess()
                                    },
                                    onAuthError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLoading = false
                                    }
                                )
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            Text(
                                text = if (isUr) "Sign in with Google / گوگل کے ساتھ سائن ان کریں" else "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // BiometricPrompt Authentication Button (Fingerprint & Facial Recognition)
                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                BiometricAuthHelper.authenticate(
                                    context = context,
                                    title = if (isUr) {
                                        "ایس این ایل آرڈرنگ پلیٹ فارم بائیو میٹرک لاگ ان"
                                    } else {
                                        "SNL Ordering Platform Biometric Login"
                                    },
                                    subtitle = if (isUr) {
                                        "فنگر پرنٹ یا چہرے کی شناخت (BiometricPrompt)"
                                    } else {
                                        "Fingerprint or Facial Recognition"
                                    },
                                    description = if (isUr) {
                                        "محفوظ ڈیلر/ڈسٹری بیوٹر رسائی کے لیے اپنے فنگر پرنٹ یا چہرے کی تصدیق کریں۔"
                                    } else {
                                        "Verify your fingerprint or face via Android BiometricPrompt to securely access the ordering platform."
                                    },
                                    negativeButtonText = if (isUr) "پاس ورڈ استعمال کریں" else "Use Password",
                                    onSuccess = {
                                        val activeFirebaseUser = Firebase.auth.currentUser
                                        if (activeFirebaseUser != null) {
                                            BiometricAuthHelper.saveLastVerifiedEmail(
                                                context,
                                                activeFirebaseUser.email.orEmpty()
                                            )
                                            infoMessage = if (isUr) {
                                                "بائیو میٹرک تصدیق کامیاب! آرڈرنگ پلیٹ فارم کھل رہا ہے..."
                                            } else {
                                                "Biometric authentication succeeded! Opening ordering platform..."
                                            }
                                            onAuthSuccess()
                                        } else {
                                            infoMessage = if (isUr) {
                                                "بائیو میٹرک شناخت کی تصدیق ہو گئی! اب کلاؤڈ سیشن مربوط کیا جا رہا ہے..."
                                            } else {
                                                "Biometric identity verified! Linking authorized cloud session..."
                                            }
                                            isLoading = true
                                            onGoogleSignInClicked(
                                                context = context,
                                                credentialManager = credentialManager,
                                                onAuthSuccess = {
                                                    isLoading = false
                                                    BiometricAuthHelper.saveLastVerifiedEmail(
                                                        context,
                                                        Firebase.auth.currentUser?.email ?: emailInput.trim()
                                                    )
                                                    onAuthSuccess()
                                                },
                                                onAuthError = { err ->
                                                    isLoading = false
                                                    errorMessage = err
                                                },
                                                scope = scope,
                                                onAuthCancelled = {
                                                    isLoading = false
                                                }
                                            )
                                        }
                                    },
                                    onError = { _, errMsg ->
                                        errorMessage = errMsg
                                    },
                                    onFailed = {
                                        errorMessage = if (isUr) {
                                            "بائیو میٹرک شناخت مماثل نہیں ہوئی، دوبارہ کوشش کریں۔"
                                        } else {
                                            "Biometric not recognized. Please try again."
                                        }
                                    }
                                )
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("biometric_auth_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Authentication",
                                tint = SnlNavy,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Facial Recognition Authentication",
                                tint = SnlRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isUr) {
                                    "فنگر پرنٹ / فیس آئی ڈی سے لاگ ان کریں"
                                } else {
                                    "Sign In with Fingerprint / Face"
                                },
                                fontWeight = FontWeight.Bold,
                                color = SnlNavy
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = BiometricAuthHelper.getCapabilityDescription(biometricCapability, isUr),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (biometricCapability == BiometricCapabilityStatus.AVAILABLE) {
                                SnlApproveGreen
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("biometric_capability_status_text")
                        )

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = if (isUr) {
                                "کیا آپ نئے ڈیلر یا ڈسٹری بیوٹر ہیں؟"
                            } else {
                                "New Dealer or Distributor Partner?"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        // Prominent 'Register' button for new dealers/distributors
                        OutlinedButton(
                            onClick = {
                                activeTab = AuthScreenTab.REGISTER_DEALER_DISTRIBUTOR
                                errorMessage = null
                                infoMessage = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("register_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SnlRed)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isUr) "نیا ڈیلر / ڈسٹری بیوٹر رجسٹر کریں (Register)" else "Register as Dealer / Distributor",
                                fontWeight = FontWeight.Bold,
                                color = SnlRed
                            )
                        }
                    } else {
                        // REGISTER_DEALER_DISTRIBUTOR mode fields
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it
                                errorMessage = null
                            },
                            label = {
                                Text(if (isUr) "پاس ورڈ کی تصدیق کریں (Confirm Password)" else "Confirm Password *")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = "Confirm Password")
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_password_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = if (isUr) "اکاؤنٹ کی قسم منتخب کریں:" else "Select Partner Role:",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            FilterChip(
                                selected = selectedRole == "DEALER",
                                onClick = { selectedRole = "DEALER" },
                                label = { Text(if (isUr) "ڈیلر (Dealer)" else "Authorized Dealer") },
                                modifier = Modifier.testTag("auth_role_chip_dealer")
                            )
                            FilterChip(
                                selected = selectedRole == "DISTRIBUTOR",
                                onClick = { selectedRole = "DISTRIBUTOR" },
                                label = { Text(if (isUr) "ڈسٹری بیوٹر (Distributor)" else "Distributor") },
                                modifier = Modifier.testTag("auth_role_chip_distributor")
                            )
                        }

                        OutlinedTextField(
                            value = fullNameInput,
                            onValueChange = { fullNameInput = it },
                            label = { Text(if (isUr) "پورا نام (Full Name)" else "Contact Person Full Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reg_full_name_input")
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = businessNameInput,
                            onValueChange = { businessNameInput = it },
                            label = { Text(if (isUr) "کاروبار کا نام (Business Name)" else "Business / Dealership Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reg_business_name_input")
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text(if (isUr) "فون / واٹس ایپ نمبر (Phone)" else "Phone / WhatsApp Number *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reg_phone_input")
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            label = { Text(if (isUr) "شہر (City)" else "City (e.g. Karachi, Lahore, Multan) *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reg_city_input")
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text(if (isUr) "مکمل ڈلیوری پتہ (Delivery Address)" else "Complete Delivery Address *") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_reg_address_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || !emailInput.contains("@")) {
                                    errorMessage = if (isUr) {
                                        "براہ کرم درست ای میل ایڈریس درج کریں۔"
                                    } else {
                                        "Please enter a valid email address."
                                    }
                                    return@Button
                                }
                                if (passwordInput.length < 6) {
                                    errorMessage = if (isUr) {
                                        "پاس ورڈ کم از کم 6 حروف کا ہونا چاہیے۔"
                                    } else {
                                        "Password must be at least 6 characters."
                                    }
                                    return@Button
                                }
                                if (confirmPasswordInput.isNotBlank() && passwordInput != confirmPasswordInput) {
                                    errorMessage = if (isUr) {
                                        "پاس ورڈ اور تصدیقی پاس ورڈ یکساں نہیں ہیں۔"
                                    } else {
                                        "Passwords do not match."
                                    }
                                    return@Button
                                }
                                if (fullNameInput.isBlank() || businessNameInput.isBlank() || addressInput.isBlank()) {
                                    errorMessage = if (isUr) {
                                        "براہ کرم نام، کاروبار کا نام اور مکمل پتہ درج کریں۔"
                                    } else {
                                        "Please fill in your Full Name, Business Name, and Delivery Address."
                                    }
                                    return@Button
                                }

                                // Save draft so ProfileOnboardingScreen automatically applies the registration upon Firebase Auth
                                PendingRegistrationStore.saveDraft(
                                    PendingRegistrationDraft(
                                        email = emailInput.trim(),
                                        fullName = fullNameInput.trim(),
                                        businessName = businessNameInput.trim(),
                                        phone = phoneInput.trim(),
                                        city = cityInput.trim().ifBlank { "Karachi" },
                                        address = addressInput.trim(),
                                        role = selectedRole
                                    )
                                )

                                isLoading = true
                                errorMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoading = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLoading = false
                                    }
                                )
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SnlGreenAccept,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_register_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isUr) {
                                        "رجسٹر کریں اور اکاؤنٹ کی تصدیق کریں"
                                    } else {
                                        "Register Dealer / Distributor Account"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                activeTab = AuthScreenTab.LOGIN
                                errorMessage = null
                            },
                            modifier = Modifier.testTag("back_to_login_button")
                        ) {
                            Text(
                                text = if (isUr) "پہلے سے اکاؤنٹ موجود ہے؟ لاگ ان کریں" else "Already have an account? Back to Login",
                                color = SnlNavy,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = SnlYellow, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SNL Helpline Karachi: +92 310 100 52 53",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Forgot Password Dialog interfacing with Firebase Authentication
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            icon = {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = SnlRed, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = if (isUr) "پاس ورڈ ری سیٹ کریں (Forgot Password)" else "Reset Your SNL Account Password",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isUr) {
                            "اپنا رجسٹرڈ ای میل ایڈریس درج کریں۔ فائر بیس تصدیقی سسٹم آپ کو پاس ورڈ ری سیٹ کرنے کا لنک بھیجے گا۔"
                        } else {
                            "Enter your registered dealer, distributor, or staff email address to receive a Firebase Authentication password reset link."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { forgotPasswordEmail = it },
                        label = {
                            Text(if (isUr) "رجسٹرڈ ای میل (Registered Email)" else "Registered Email Address")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )
                    OutlinedButton(
                        onClick = {
                            InvoiceAndReportPdfGenerator.openWhatsAppClickToChat(
                                context = context,
                                phoneNumber = "+923101005253",
                                message = "Assalam-o-Alaikum SNL Admin, I need assistance resetting account access for email: ${forgotPasswordEmail.ifBlank { "My Dealer/Distributor Account" }}"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_whatsapp_support_button")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isUr) "واٹس ایپ ایڈمن سپورٹ (+92 310 100 52 53)" else "Contact SNL Admin via WhatsApp")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendFirebasePasswordReset(
                            email = forgotPasswordEmail,
                            scope = scope,
                            onSuccess = { msg ->
                                infoMessage = msg
                                errorMessage = null
                                showForgotPasswordDialog = false
                            },
                            onError = { err ->
                                errorMessage = err
                                showForgotPasswordDialog = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SnlRed, contentColor = Color.White),
                    modifier = Modifier.testTag("send_reset_email_button")
                ) {
                    Text(if (isUr) "ری سیٹ لنک بھیجیں" else "Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    modifier = Modifier.testTag("cancel_forgot_password_button")
                ) {
                    Text(if (isUr) "منسوخ کریں" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileOnboardingScreen(
    currentEmail: String,
    currentDisplayName: String,
    language: UiLanguage,
    isBusy: Boolean,
    onInitializeSuperAdmin: () -> Unit,
    onSubmitSelfRegistration: (
        fullName: String,
        businessName: String,
        phone: String,
        email: String,
        city: String,
        address: String,
        role: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val isUr = language == UiLanguage.URDU
    val pendingDraft by PendingRegistrationStore.draft.collectAsStateWithLifecycle()

    var fullName by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.fullName?.ifBlank { currentDisplayName } ?: currentDisplayName.ifBlank { "" })
    }
    var businessName by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.businessName.orEmpty())
    }
    var phone by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.phone?.ifBlank { "+92 3" } ?: "+92 3")
    }
    var email by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.email?.ifBlank { currentEmail } ?: currentEmail)
    }
    var city by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.city?.ifBlank { "Karachi" } ?: "Karachi")
    }
    var address by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.address.orEmpty())
    }
    var selectedRole by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.role ?: "DEALER")
    }

    // If the user filled out the Dealer/Distributor Registration tab on the Auth screen, auto-submit once authenticated
    LaunchedEffect(pendingDraft) {
        val draft = pendingDraft
        if (draft != null && draft.fullName.isNotBlank() && draft.businessName.isNotBlank() && draft.address.isNotBlank()) {
            PendingRegistrationStore.clearDraft()
            onSubmitSelfRegistration(
                draft.fullName,
                draft.businessName,
                draft.phone,
                draft.email.ifBlank { currentEmail },
                draft.city.ifBlank { "Karachi" },
                draft.address,
                draft.role
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.img_snl_logo),
                    contentDescription = "SNL Logo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(4.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SNL LUBRICANTS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentEmail.ifBlank { "Authenticated Session" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("onboarding_sign_out_btn")) {
                Text(if (isUr) "لاگ آؤٹ" else "Sign Out")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Option 1: Super Admin Setup Card (for Company Owner / Administrator)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = SnlNavy),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = SnlYellow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUr) "کمپنی سپر ایڈمنسٹریٹر سیٹ اپ (SNL HQ)" else "SNL Super Admin Initialization (Company HQ)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isUr) {
                        "سیفی نیشنل لبریکنٹس کے سپر ایڈمن ڈیش بورڈ کو فعال کریں اور منسلک پرائس لسٹ (59 مصنوعات) کو خودکار طور پر لوڈ کریں۔"
                    } else {
                        "Activate the complete Super Admin Control Center and import all 59 authentic SNL products from your attached Sole Distributor Pakistan price list (03-11-26)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onInitializeSuperAdmin,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = SnlRed, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("init_super_admin_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isUr) "سپر ایڈمن اور 59 مصنوعات لوڈ کریں" else "Activate Super Admin & Import 59 SNL Products",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Option 2: Dealer / Distributor Self-Registration (Method B)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SnlRed)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUr) "ڈیلر / ڈسٹری بیوٹر رجسٹریشن فارم" else "Dealer & Distributor Self-Registration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isUr) {
                        "نئے اکاؤنٹس ایڈمن کی منظوری (PENDING_APPROVAL) کے بعد قیمتیں دیکھنے اور آرڈر دینے کے اہل ہوں گے۔"
                    } else {
                        "New self-registered accounts start with PENDING_APPROVAL status until verified by the SNL Administrator."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = if (isUr) "اکاؤنٹ کی قسم منتخب کریں:" else "Select Account Type:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    FilterChip(
                        selected = selectedRole == "DEALER",
                        onClick = { selectedRole = "DEALER" },
                        label = { Text(if (isUr) "ڈیلر (Dealer)" else "Authorized Dealer") },
                        modifier = Modifier.testTag("role_chip_dealer")
                    )
                    FilterChip(
                        selected = selectedRole == "DISTRIBUTOR",
                        onClick = { selectedRole = "DISTRIBUTOR" },
                        label = { Text(if (isUr) "ڈسٹری بیوٹر (Distributor)" else "Distributor") },
                        modifier = Modifier.testTag("role_chip_distributor")
                    )
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(if (isUr) "پورا نام (Full Name)" else "Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_full_name_input"),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text(if (isUr) "کاروبار کا نام (Business Name)" else "Business / Dealership Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_business_name_input"),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isUr) "فون نمبر (Phone Number)" else "Phone / WhatsApp Number *") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_phone_input"),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(if (isUr) "شہر (City)" else "City (e.g. Karachi, Lahore, Multan) *") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_city_input"),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (isUr) "مکمل پتہ (Complete Delivery Address)" else "Complete Delivery Address *") },
                    modifier = Modifier.fillMaxWidth().testTag("reg_address_input"),
                    minLines = 2
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmitSelfRegistration(
                            fullName.ifBlank { "SNL Partner" },
                            businessName.ifBlank { "Auto Traders" },
                            phone.ifBlank { "+923000000000" },
                            email,
                            city.ifBlank { "Karachi" },
                            address.ifBlank { "Main Auto Market, $city" },
                            selectedRole
                        )
                    },
                    enabled = !isBusy && fullName.isNotBlank() && businessName.isNotBlank() && address.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SnlGreenAccept, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_registration_button")
                ) {
                    Icon(Icons.Default.Business, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUr) "رجسٹریشن جمع کروائیں (Submit for Approval)" else "Submit Registration for Admin Approval",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    profile: UserProfile,
    language: UiLanguage,
    onPromoteToSuperAdminForTesting: () -> Unit,
    onSignOut: () -> Unit
) {
    val isUr = language == UiLanguage.URDU
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_snl_logo),
                    contentDescription = "SNL Logo",
                    modifier = Modifier.size(90.dp)
                )
                Spacer(Modifier.height(14.dp))
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = SnlRed,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isUr) "اکاؤنٹ منظوری کا منتظر ہے" else "Account Pending Admin Approval",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${profile.businessName} (${profile.fullName}) — ${profile.role}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SnlRed
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isUr) {
                        "آپ کی رجسٹریشن موصول ہو چکی ہے۔ کمپنی ایڈمنسٹریٹر کی منظوری کے بعد آپ اپنی مخصوص قیمتیں دیکھ سکیں گے اور آرڈر دے سکیں گے۔"
                    } else {
                        "Your registration is currently in PENDING_APPROVAL status. Pending accounts cannot view restricted dealer/distributor pricing or place orders until approved by the SNL Administrator."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onPromoteToSuperAdminForTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = SnlNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("switch_to_admin_from_pending_btn")
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isUr) "ایڈمن موڈ کھولیں (Approve / Manage)" else "Switch to Super Admin to Approve Account")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isUr) "لاگ آؤٹ" else "Sign Out")
                }
            }
        }
    }
}
