package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.FirebaseRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserPreferences
import com.example.ui.PulseViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.PulseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PulseAppRoot()
    }
  }
}

@Composable
fun PulseAppRoot() {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val preferencesRepository = remember(appContext) { PreferencesRepository(appContext) }
  val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
    initialValue = UserPreferences(),
  )
  val systemDark = isSystemInDarkTheme()
  val isDark = prefs.darkMode ?: systemDark

  val auth = remember { Firebase.auth }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  val credentialManager = remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    withFrameNanos { }
    delay(250)
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  PulseTheme(darkTheme = isDark) {
    val user = currentUser
    if (user == null) {
      AuthScreen(onAuthSuccess = {})
    } else {
      val uid = user.uid
      key(uid) {
        val pulseViewModel: PulseViewModel = viewModel(
          key = uid,
          factory = viewModelFactory {
            initializer {
              val app = checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
              PulseViewModel(
                firebaseRepository = FirebaseRepository.getInstance(app),
                preferencesRepository = PreferencesRepository(app),
                currentUserId = uid,
              )
            }
          },
        )
        DashboardScreen(
          viewModel = pulseViewModel,
          userEmail = user.email,
          isDarkTheme = isDark,
        )
      }
    }
  }
}
