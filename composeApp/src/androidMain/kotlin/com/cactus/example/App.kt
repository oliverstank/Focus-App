package com.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }

    NothingTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    ProcessingStatusBar()
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Text(
                                    "NOTIFICATIONS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Text(
                                    "FOCUS MODE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            when (selectedTab) {
                0 -> NotificationSummaryScreen(Modifier.padding(paddingValues))
                1 -> FocusModeScreen()
            }
        }
    }
}

@Composable
fun ProcessingStatusBar() {
    val context = LocalContext.current
    val processingState by FocusModeRepository.processingState.collectAsState()
    val pendingNotifications by FocusModeRepository.pendingNotifications.collectAsState()
    var timeRemaining by remember { mutableStateOf(0L) }

    // Update countdown every second and trigger processing when timer hits 0
    LaunchedEffect(processingState.nextScanTime) {
        while (true) {
            val remaining = max(0, processingState.nextScanTime - System.currentTimeMillis())
            timeRemaining = remaining

            // Auto-trigger processing when timer hits 0
            if (remaining == 0L &&
                (processingState.status == ProcessingStatus.IDLE ||
                 processingState.status == ProcessingStatus.COMPLETE)) {
                // Trigger worker immediately
                val workRequest = OneTimeWorkRequestBuilder<NotificationProcessingWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }

            delay(1000)
        }
    }

    val minutes = (timeRemaining / 1000 / 60).toInt()
    val seconds = ((timeRemaining / 1000) % 60).toInt()

    val backgroundColor = when (processingState.status) {
        ProcessingStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        ProcessingStatus.SCANNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ProcessingStatus.FILTERING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        ProcessingStatus.ANALYZING_WITH_LLM -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        ProcessingStatus.COMPLETE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }

    val textColor = when (processingState.status) {
        ProcessingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        ProcessingStatus.SCANNING -> MaterialTheme.colorScheme.primary
        ProcessingStatus.FILTERING -> MaterialTheme.colorScheme.secondary
        ProcessingStatus.ANALYZING_WITH_LLM -> MaterialTheme.colorScheme.tertiary
        ProcessingStatus.COMPLETE -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = processingState.message,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    letterSpacing = 0.5.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (processingState.status == ProcessingStatus.IDLE || processingState.status == ProcessingStatus.COMPLETE) {
                        Text(
                            text = "Next scan in ${String.format("%d:%02d", minutes, seconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    if (pendingNotifications.isNotEmpty()) {
                        Text(
                            text = "• ${pendingNotifications.size} pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Manual trigger button
                if (processingState.status == ProcessingStatus.IDLE || processingState.status == ProcessingStatus.COMPLETE) {
                    IconButton(
                        onClick = {
                            // Trigger worker immediately
                            val workRequest = OneTimeWorkRequestBuilder<NotificationProcessingWorker>().build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Process now",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (processingState.status != ProcessingStatus.IDLE && processingState.status != ProcessingStatus.COMPLETE) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = textColor,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSummaryScreen(modifier: Modifier = Modifier, viewModel: NotificationViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val notifications by viewModel.todayNotifications.collectAsState()
    val importantNotifications by FocusModeRepository.importantNotifications.collectAsState()
    val unimportantNotifications by FocusModeRepository.unimportantNotifications.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "NOTIFICATIONS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
            ) {
                // Summary section with Nothing Phone design
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI SUMMARY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Button(
                                onClick = { viewModel.summarizeNotifications() },
                                enabled = uiState !is NotificationViewModel.UiState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "GENERATE",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        when (val state = uiState) {
                            is NotificationViewModel.UiState.Idle -> {
                                Text(
                                    text = "Tap refresh to generate an AI-powered summary of your notifications",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                            is NotificationViewModel.UiState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is NotificationViewModel.UiState.Success -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = state.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            is NotificationViewModel.UiState.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // High Alert Notifications header
                Text(
                    text = "HIGH ALERT NOTIFICATIONS FOR TODAY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Important and Ignored counts
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Important notifications count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "IMPORTANT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "${importantNotifications.size}",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        // Ignored notifications count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "IGNORED",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${unimportantNotifications.size}",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Clear alerts button
                    if (importantNotifications.isNotEmpty() || unimportantNotifications.isNotEmpty()) {
                        Button(
                            onClick = {
                                FocusModeRepository.clearCategorizedNotifications()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear alerts",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CLEAR ALL ALERTS",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show important notifications
                if (importantNotifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NO IMPORTANT NOTIFICATIONS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your important notifications will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = importantNotifications,
                            key = { it.notification.id }
                        ) { categorized ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                        FocusModeRepository.removeImportantNotification(categorized.notification.id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.error)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                content = {
                                    ImportantNotificationCard(
                                        notification = categorized.notification,
                                        onUnlock = {
                                            // Temporarily unlock the app for 5 minutes
                                            FocusModeRepository.addTemporaryUnlock(categorized.notification.packageName)

                                            // Launch the app
                                            try {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(
                                                    categorized.notification.packageName
                                                )
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("NotificationScreen", "Failed to launch app", e)
                                            }
                                        }
                                    )
                                },
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun ImportantNotificationCard(notification: NotificationData, onUnlock: () -> Unit) {
    var isUnlocked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isUnlocked) {
                onUnlock()
                isUnlocked = true
            }
            .background(
                if (isUnlocked)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
            .border(
                width = 2.dp,
                color = if (isUnlocked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with app name and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isUnlocked)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                    )
                    Text(
                        text = notification.packageName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnlocked)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isUnlocked)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isUnlocked) "UNLOCKED 5 MIN" else "TAP TO UNLOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnlocked)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (notification.title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (notification.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUnlocked)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(Date(notification.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUnlocked)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun NotificationCard(notification: NotificationData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with app name and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = notification.packageName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(notification.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (notification.title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (notification.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
