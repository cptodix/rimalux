package com.example.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PlusJakartaSansFontFamily

private data class MockupAppSpec(
    val tagKey: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color
) {
    val iconBrush: Brush = Brush.linearGradient(
        colors = listOf(gradientStart, gradientEnd),
        start = Offset(0f, 0f),
        end = Offset(120f, 120f)
    )
}

private val HomeGridRow1Apps = listOf(
    MockupAppSpec(
        tagKey = "calendar",
        labelRes = R.string.mockup_app_calendar,
        icon = Icons.Rounded.CalendarToday,
        gradientStart = Color(0xFF4285F4),
        gradientEnd = Color(0xFF1967D2)
    ),
    MockupAppSpec(
        tagKey = "photos",
        labelRes = R.string.mockup_app_photos,
        icon = Icons.Rounded.PhotoLibrary,
        gradientStart = Color(0xFFFF7043),
        gradientEnd = Color(0xFFFBC02D)
    ),
    MockupAppSpec(
        tagKey = "maps",
        labelRes = R.string.mockup_app_maps,
        icon = Icons.Rounded.Explore,
        gradientStart = Color(0xFF34A853),
        gradientEnd = Color(0xFF0F9D58)
    ),
    MockupAppSpec(
        tagKey = "clock",
        labelRes = R.string.mockup_app_clock,
        icon = Icons.Rounded.Schedule,
        gradientStart = Color(0xFF5C6BC0),
        gradientEnd = Color(0xFF3949AB)
    )
)

private val HomeGridRow2Apps = listOf(
    MockupAppSpec(
        tagKey = "mail",
        labelRes = R.string.mockup_app_mail,
        icon = Icons.Rounded.Mail,
        gradientStart = Color(0xFFEA4335),
        gradientEnd = Color(0xFFC5221F)
    ),
    MockupAppSpec(
        tagKey = "music",
        labelRes = R.string.mockup_app_music,
        icon = Icons.Rounded.MusicNote,
        gradientStart = Color(0xFFAB47BC),
        gradientEnd = Color(0xFF7B1FA2)
    ),
    MockupAppSpec(
        tagKey = "files",
        labelRes = R.string.mockup_app_files,
        icon = Icons.Rounded.Folder,
        gradientStart = Color(0xFF29B6F6),
        gradientEnd = Color(0xFF0288D1)
    ),
    MockupAppSpec(
        tagKey = "settings",
        labelRes = R.string.mockup_app_settings,
        icon = Icons.Rounded.Settings,
        gradientStart = Color(0xFF78909C),
        gradientEnd = Color(0xFF455A64)
    )
)

private val HomeDockApps = listOf(
    MockupAppSpec(
        tagKey = "phone",
        labelRes = R.string.mockup_app_phone,
        icon = Icons.Rounded.Call,
        gradientStart = Color(0xFF2ECC71),
        gradientEnd = Color(0xFF1B9E53)
    ),
    MockupAppSpec(
        tagKey = "messages",
        labelRes = R.string.mockup_app_messages,
        icon = Icons.Rounded.ChatBubble,
        gradientStart = Color(0xFF3B82F6),
        gradientEnd = Color(0xFF1D4ED8)
    ),
    MockupAppSpec(
        tagKey = "browser",
        labelRes = R.string.mockup_app_browser,
        icon = Icons.Rounded.Public,
        gradientStart = Color(0xFF06B6D4),
        gradientEnd = Color(0xFF2563EB)
    ),
    MockupAppSpec(
        tagKey = "camera",
        labelRes = R.string.mockup_app_camera,
        icon = Icons.Rounded.PhotoCamera,
        gradientStart = Color(0xFF525866),
        gradientEnd = Color(0xFF272B33)
    )
)

private val CrispLauncherShadow = Shadow(
    color = Color.Black.copy(alpha = 0.82f),
    offset = Offset(0f, 1.5f),
    blurRadius = 4.5f
)

/**
 * Touch-passthrough realistic Android Lock Screen preview overlay.
 * Uses pure Box/Column/Row primitives (never Material Surface) so all canvas gestures pass straight through.
 */
@Composable
fun LockScreenOverlayMockup(
    bottomClearance: Dp,
    modifier: Modifier = Modifier
) {
    val statusBarTop = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 10.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("mockup_lock_screen_overlay")
    ) {
        val usableHeight = maxHeight - statusBarTop - bottomClearance
        val showNotifications = usableHeight >= 370.dp

        // Top Status Bar + Lock Clock + Frosted Notification Cards
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(top = statusBarTop, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MockupAndroidStatusBar(
                leftText = stringResource(R.string.mockup_carrier),
                modifier = Modifier.testTag("mockup_lock_status_bar")
            )

            Spacer(modifier = Modifier.height(22.dp))

            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.86f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.mockup_clock_lock),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 68.sp,
                lineHeight = 72.sp,
                letterSpacing = (-2).sp,
                color = Color.White.copy(alpha = 0.96f),
                style = MaterialTheme.typography.displayLarge.copy(shadow = CrispLauncherShadow)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.mockup_date_short),
                    style = MaterialTheme.typography.titleSmall.copy(shadow = CrispLauncherShadow),
                    color = Color.White.copy(alpha = 0.92f)
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleSmall.copy(shadow = CrispLauncherShadow),
                    color = Color.White.copy(alpha = 0.72f)
                )
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.mockup_weather),
                    style = MaterialTheme.typography.labelLarge.copy(shadow = CrispLauncherShadow),
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            if (showNotifications) {
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = 368.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LockNotificationCard(
                        icon = Icons.Rounded.CalendarToday,
                        badgeColor = Color(0xFF4285F4),
                        title = stringResource(R.string.mockup_notif_1_title),
                        body = stringResource(R.string.mockup_notif_1_body),
                        time = stringResource(R.string.mockup_notif_1_time)
                    )
                    LockNotificationCard(
                        icon = Icons.Rounded.ChatBubble,
                        badgeColor = Color(0xFF34A853),
                        title = stringResource(R.string.mockup_notif_2_title),
                        body = stringResource(R.string.mockup_notif_2_body),
                        time = stringResource(R.string.mockup_notif_2_time)
                    )
                }
            }
        }

        // Bottom Lock Screen Quick Shortcuts (Flashlight · Fingerprint · Camera), strictly above preview_info_overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = bottomClearance)
                .testTag("mockup_lock_bottom_shortcuts"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LockQuickAffordanceCircle(icon = Icons.Rounded.FlashlightOn)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(24.dp)
                )
            }

            LockQuickAffordanceCircle(icon = Icons.Rounded.PhotoCamera)
        }
    }
}

/**
 * Touch-passthrough realistic Android Home Screen preview overlay.
 * Renders status bar, At-a-Glance date/weather header, frosted search bar,
 * colorful adaptive app icons with app name text labels underneath, page indicator dots,
 * and labeled favorite dock—all strictly clearing the active preview info pill at the bottom.
 */
@Composable
fun HomeGridOverlayMockup(
    bottomClearance: Dp,
    modifier: Modifier = Modifier
) {
    val statusBarTop = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 10.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("mockup_home_screen_overlay")
    ) {
        val usableHeight = maxHeight - statusBarTop - bottomClearance
        val showSearchPill = usableHeight >= 400.dp
        val showTwoAppRows = usableHeight >= 460.dp
        val showOneAppRow = usableHeight >= 340.dp

        // Top Launcher Header: Status Bar + At-a-Glance + Search Pill
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(top = statusBarTop, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MockupAndroidStatusBar(
                leftText = stringResource(R.string.mockup_clock_status),
                modifier = Modifier.testTag("mockup_home_status_bar")
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .testTag("mockup_home_at_a_glance"),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stringResource(R.string.mockup_date_short),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = CrispLauncherShadow
                    ),
                    color = Color.White.copy(alpha = 0.96f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.mockup_weather),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = CrispLauncherShadow
                        ),
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }

            if (showSearchPill) {
                MockupLauncherSearchBar(
                    modifier = Modifier.testTag("mockup_home_search_bar")
                )
            }
        }

        // Bottom Launcher Grid + Page Dots + Dock (strictly above preview_info_overlay via bottomClearance)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = bottomClearance)
                .testTag("mockup_home_bottom_cluster"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showTwoAppRows) {
                MockupAppIconRow(
                    apps = HomeGridRow1Apps,
                    modifier = Modifier.testTag("mockup_home_grid_row_1")
                )
            }

            if (showOneAppRow) {
                MockupAppIconRow(
                    apps = HomeGridRow2Apps,
                    modifier = Modifier.testTag("mockup_home_grid_row_2")
                )

                // Launcher Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.88f))
                    )
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.38f))
                        )
                    }
                }
            }

            MockupAppIconRow(
                apps = HomeDockApps,
                modifier = Modifier.testTag("mockup_home_dock_row")
            )
        }
    }
}

@Composable
private fun MockupAndroidStatusBar(
    leftText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = CrispLauncherShadow
            ),
            color = Color.White.copy(alpha = 0.94f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SignalCellular4Bar,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(13.dp)
            )
            Icon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = stringResource(R.string.mockup_battery_pct),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = CrispLauncherShadow
                ),
                color = Color.White.copy(alpha = 0.92f)
            )
            // Crisp vector battery capsule
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.86f), RoundedCornerShape(2.5.dp))
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.94f))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
                        .background(Color.White.copy(alpha = 0.86f))
                )
            }
        }
    }
}

@Composable
private fun MockupLauncherSearchBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(Color(0xFF141720).copy(alpha = 0.72f))
            .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.mockup_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.size(17.dp)
            )
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.80f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun MockupAppIconRow(
    apps: List<MockupAppSpec>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        apps.forEach { app ->
            MockupAppIconTile(app = app)
        }
    }
}

@Composable
private fun MockupAppIconTile(app: MockupAppSpec) {
    val appName = stringResource(app.labelRes)
    val iconShape = RoundedCornerShape(12.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .width(68.dp)
            .testTag("mockup_app_tile_${app.tagKey}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .shadow(elevation = 2.dp, shape = iconShape)
                .clip(iconShape)
                .background(app.iconBrush)
                .border(0.75.dp, Color.White.copy(alpha = 0.26f), iconShape)
                .testTag("mockup_app_icon_${app.tagKey}")
        ) {
            Icon(
                imageVector = app.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = appName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                shadow = CrispLauncherShadow
            ),
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("mockup_app_label_${app.tagKey}")
        )
    }
}

@Composable
private fun LockNotificationCard(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    body: String,
    time: String
) {
    val cardShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF10131B).copy(alpha = 0.74f))
            .border(0.6.dp, Color.White.copy(alpha = 0.15f), cardShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(badgeColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LockQuickAffordanceCircle(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF12151E).copy(alpha = 0.72f))
            .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(20.dp)
        )
    }
}
