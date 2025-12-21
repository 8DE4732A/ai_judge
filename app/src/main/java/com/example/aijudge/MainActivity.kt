package com.example.aijudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aijudge.ui.ChatScreen
import com.example.aijudge.ui.SettingsScreen
import com.example.aijudge.ui.theme.AIJudgeTheme
import com.example.aijudge.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIJudgeTheme {
                val navController = rememberNavController()
                val messages by viewModel.messages.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val isListening by viewModel.isListening.collectAsState()

                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.toggleListening()
                    } else {
                        viewModel.addMessage("Microphone permission denied", com.example.aijudge.data.model.Sender.SYSTEM)
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            actions = {
                                IconButton(onClick = { navController.navigate("settings") }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        // Only show FAB on Chat screen (home)
                         FloatingActionButton(
                            onClick = { 
                                if (isListening) {
                                    viewModel.toggleListening()
                                } else {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.toggleListening()
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        ) {
                            Text(if (isListening) "STOP" else "LISTEN")
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "chat",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("chat") {
                            ChatScreen(
                                messages = messages
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                currentSettings = settings,
                                onSave = { newSettings ->
                                    viewModel.updateSettings(newSettings)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
