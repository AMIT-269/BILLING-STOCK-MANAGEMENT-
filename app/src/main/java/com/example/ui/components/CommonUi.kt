package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.SyncStatus
import com.example.ui.theme.*
import com.example.util.ImageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelleryTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    logoBase64: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    onSyncClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val logoBitmap = remember(logoBase64) {
        ImageHelper.base64ToBitmap(logoBase64)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "Shop Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, GoldPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cloud Sync Indicator
            if (onSyncClick != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when (syncStatus) {
                        SyncStatus.SYNCED -> CashGreenLight
                        SyncStatus.SYNCING -> GoldLight
                        SyncStatus.ERROR -> DebitRedLight
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSyncClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = when (syncStatus) {
                                SyncStatus.SYNCED -> Icons.Default.CloudDone
                                SyncStatus.SYNCING -> Icons.Default.Sync
                                SyncStatus.ERROR -> Icons.Default.CloudOff
                                else -> Icons.Default.CloudQueue
                            },
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp),
                            tint = when (syncStatus) {
                                SyncStatus.SYNCED -> CashGreen
                                SyncStatus.SYNCING -> GoldDark
                                SyncStatus.ERROR -> DebitRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (syncStatus) {
                                SyncStatus.SYNCED -> "Cloud ✓"
                                SyncStatus.SYNCING -> "Sync..."
                                SyncStatus.ERROR -> "Offline"
                                else -> "Cloud"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (syncStatus) {
                                SyncStatus.SYNCED -> CashGreen
                                SyncStatus.SYNCING -> GoldDark
                                SyncStatus.ERROR -> DebitRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            actions()
        }
    }
}
