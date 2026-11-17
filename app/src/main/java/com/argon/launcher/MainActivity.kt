package com.argon.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.argon.launcher.account.AccountRepository
import com.argon.launcher.account.ElyByAuth
import com.argon.launcher.account.MicrosoftAuth
import com.argon.launcher.controls.ControlLayoutRepository
import com.argon.launcher.controls.InputModeState
import com.argon.launcher.ui.AccountsScreen
import com.argon.launcher.ui.ControlsScreen
import com.argon.launcher.ui.ModsScreen
import com.argon.launcher.ui.theme.ArgonLauncherTheme

/** Must match the <data android:scheme=.../> in AndroidManifest.xml and the Ely.by app's registered redirect URI. */
private const val ELY_BY_REDIRECT_URI = "argonlauncher://elyby-callback"

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("accounts", "Hesaplar", Icons.Filled.Person),
    BottomTab("mods", "Modlar", Icons.Filled.Extension),
    BottomTab("controls", "Kontroller", Icons.Filled.SportsEsports)
)

class MainActivity : ComponentActivity() {

    // Shared by every screen, and by the dispatch*Event overrides below (which see every input
    // event before Compose does) and the touch control overlay that reacts to it. One instance
    // for the whole Activity is what makes "plug in a keyboard anywhere, controls hide" work.
    private val inputModeState = InputModeState()

    // The Ely.by sign-in finishes in a browser tab, which comes back to us through the
    // intent-filter in the manifest rather than as a normal UI event, so we stash the resulting
    // code here for the Accounts screen to pick up.
    private var pendingElyByCode by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleElyByRedirect(intent)

        val accountRepository = AccountRepository(applicationContext)
        val controlLayoutRepository = ControlLayoutRepository(applicationContext)
        val microsoftAuth = MicrosoftAuth()
        // Register your own app at https://ely.by/dev/applications/add and put its id/secret
        // here; MicrosoftAuth.CLIENT_ID similarly needs your own Azure AD app registration.
        val elyByAuth = ElyByAuth(
            clientId = "YOUR_ELY_BY_CLIENT_ID",
            clientSecret = "YOUR_ELY_BY_CLIENT_SECRET",
            redirectUri = ELY_BY_REDIRECT_URI
        )

        setContent {
            ArgonLauncherTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Argon Launcher") })
                    },
                    bottomBar = {
                        NavigationBar {
                            bottomTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "mods",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("accounts") {
                            AccountsScreen(
                                repository = accountRepository,
                                microsoftAuth = microsoftAuth,
                                elyByAuth = elyByAuth,
                                pendingElyByCode = pendingElyByCode,
                                onElyByCodeConsumed = { pendingElyByCode = null },
                                onOpenElyByLogin = { url ->
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            )
                        }
                        composable("mods") {
                            ModsScreen()
                        }
                        composable("controls") {
                            ControlsScreen(
                                inputModeState = inputModeState,
                                repository = controlLayoutRepository
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleElyByRedirect(intent)
    }

    private fun handleElyByRedirect(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "argonlauncher" && data.host == "elyby-callback") {
            pendingElyByCode = data.getQueryParameter("code")
        }
    }

    // These three overrides are the entire trick behind "touch controls auto-hide when a
    // keyboard/mouse/controller is used, and come back on tap": every input event, from any
    // source, passes through here before any View or Compose node gets a chance to consume it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        inputModeState.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        inputModeState.onGenericMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        inputModeState.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
}
