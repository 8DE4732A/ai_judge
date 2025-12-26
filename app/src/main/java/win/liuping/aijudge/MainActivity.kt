package win.liuping.aijudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import win.liuping.aijudge.ui.ChatScreen
import win.liuping.aijudge.ui.SessionListDialog
import win.liuping.aijudge.ui.SettingsScreen
import win.liuping.aijudge.ui.SpeakerAliasDialog
import win.liuping.aijudge.ui.theme.AIJudgeTheme
import win.liuping.aijudge.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIJudgeTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val currentSession by viewModel.currentSession.collectAsState()
                val sessionList by viewModel.sessionList.collectAsState()
                val showSessionList by viewModel.showSessionList.collectAsState()
                val showSpeakerAliasDialog by viewModel.showSpeakerAliasDialog.collectAsState()

                val settings by viewModel.settings.collectAsState()
                val isListening by viewModel.isListening.collectAsState()
                val isJudging by viewModel.isJudging.collectAsState()
                val isOrganizing by viewModel.isOrganizing.collectAsState()
                val organizationResult by viewModel.organizationResult.collectAsState()
                val sttDownloadStatus by viewModel.sttDownloadStatus.collectAsState()
                val ttsDownloadStatus by viewModel.ttsDownloadStatus.collectAsState()
                val diarizationDownloadStatus by viewModel.diarizationDownloadStatus.collectAsState()
                val punctuationDownloadStatus by viewModel.punctuationDownloadStatus.collectAsState()

                val sttLoadStatus by viewModel.sttLoadStatus.collectAsState()
                val ttsLoadStatus by viewModel.ttsLoadStatus.collectAsState()
                val diarizationLoadStatus by viewModel.diarizationLoadStatus.collectAsState()
                val punctuationLoadStatus by viewModel.punctuationLoadStatus.collectAsState()

                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.toggleListening()
                    } else {
                        viewModel.addMessage(context.getString(R.string.mic_permission_denied), win.liuping.aijudge.data.model.Sender.SYSTEM)
                    }
                }

                // Session List Dialog
                if (showSessionList) {
                    SessionListDialog(
                        sessions = sessionList,
                        currentSessionId = currentSession.id,
                        onSessionSelect = { viewModel.loadSession(it) },
                        onNewSession = { viewModel.createNewSession() },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onDismiss = { viewModel.hideSessionList() }
                    )
                }

                // Speaker Alias Dialog
                if (showSpeakerAliasDialog) {
                    SpeakerAliasDialog(
                        speakers = viewModel.getAllSpeakersInSession(),
                        currentAliases = currentSession.speakerAliases,
                        onUpdateAlias = { speakerId, alias ->
                            viewModel.updateSpeakerAlias(speakerId, alias)
                        },
                        onDismiss = { viewModel.hideSpeakerAliasDialog() }
                    )
                }

                // Organization Result Dialog
                if (organizationResult != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearOrganizationResult() },
                        title = { Text(stringResource(R.string.dialog_title_minutes)) },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(organizationResult!!)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.clearOrganizationResult() }) {
                                Text(stringResource(R.string.btn_close))
                            }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentRoute) {
                                        "settings" -> stringResource(R.string.settings_title)
                                        else -> currentSession.getDisplayTitle().ifBlank {
                                            stringResource(R.string.app_name)
                                        }
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            navigationIcon = {
                                if (currentRoute == "settings") {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.cd_back)
                                        )
                                    }
                                } else {
                                    // Session list button
                                    IconButton(onClick = { viewModel.toggleSessionList() }) {
                                        Icon(
                                            imageVector = Icons.Filled.List,
                                            contentDescription = stringResource(R.string.session_list_title)
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (currentRoute != "settings") {
                                    // Speaker aliases button
                                    IconButton(onClick = { viewModel.showSpeakerAliasDialog() }) {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = stringResource(R.string.speaker_aliases_title)
                                        )
                                    }

                                    // Organize button
                                    IconButton(onClick = { viewModel.requestOrganization() }) {
                                        if (isOrganizing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_organize))
                                        }
                                    }

                                    // Settings button
                                    IconButton(onClick = {
                                        navController.navigate("settings") {
                                            launchSingleTop = true
                                        }
                                    }) {
                                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                                    }
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        if (currentRoute == "chat") {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                if (!isListening) {
                                    FloatingActionButton(
                                        onClick = { viewModel.requestJudge() },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ) {
                                        Text(if (isJudging) "..." else stringResource(R.string.btn_judge))
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

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
                                    Text(if (isListening) stringResource(R.string.btn_stop) else stringResource(R.string.btn_listen))
                                }
                            }
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
                                messages = currentSession.messages,
                                speakerAliases = currentSession.speakerAliases,
                                onMessageClick = { viewModel.reSpeakMessage(it) },
                                sttLoadStatus = sttLoadStatus,
                                ttsLoadStatus = ttsLoadStatus,
                                diarizationLoadStatus = diarizationLoadStatus,
                                punctuationLoadStatus = punctuationLoadStatus
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                currentSettings = settings,
                                onSave = { newSettings ->
                                    viewModel.updateSettings(newSettings)
                                    navController.popBackStack()
                                },
                                sttDownloadStatus = sttDownloadStatus,
                                ttsDownloadStatus = ttsDownloadStatus,
                                diarizationDownloadStatus = diarizationDownloadStatus,
                                punctuationDownloadStatus = punctuationDownloadStatus,
                                onDownloadStt = { viewModel.downloadSttModel() },
                                onDownloadTts = { viewModel.downloadTtsModel() },
                                onDownloadDiarization = { viewModel.downloadDiarizationModel() },
                                onDownloadPunctuation = { viewModel.downloadPunctuationModel() }
                            )
                        }
                    }
                }
            }
        }
    }
}
