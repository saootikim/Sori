/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.sori.SoriRelease
import com.metrolist.music.sori.SoriReleaseNotifyKey
import com.metrolist.music.sori.SoriReleaseRadarViewModel
import com.metrolist.music.sori.SoriReleaseWork
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

const val SORI_RELEASES_ROUTE = "sori_releases"

fun albumRoute(release: SoriRelease) = "album/${release.release.id}"

@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sori_chart_new),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "Artist · 2026" under a release. */
@Composable
private fun releaseSubtitle(release: SoriRelease) = listOfNotNull(release.release.artistName, release.release.year?.toString()).joinToString(" · ")

/** Home card: square cover (NEW badge when it just appeared), title and artist. */
@Composable
fun ReleaseCard(
    release: SoriRelease,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(6.dp),
    ) {
        Box {
            AsyncImage(
                model = release.release.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp)),
            )
            if (release.isNew) NewBadge(Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = release.release.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = releaseSubtitle(release),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** All releases on the radar, with the notification switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoriReleaseRadarScreen(
    navController: NavController,
    viewModel: SoriReleaseRadarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val radar by viewModel.radar.collectAsStateWithLifecycle()
    val (notify, setNotify) = rememberPreference(SoriReleaseNotifyKey, defaultValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sori_releases_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .asPaddingValues(),
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) {
            item(key = "notify") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                setNotify(!notify)
                                scope.launch { SoriReleaseWork.sync(context) }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.sori_releases_notify), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.sori_releases_notify_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notify,
                        onCheckedChange = {
                            setNotify(it)
                            scope.launch { SoriReleaseWork.sync(context) }
                        },
                    )
                }
            }
            when {
                radar == null ->
                    item(key = "loading") {
                        ShimmerHost { repeat(6) { ListItemPlaceHolder() } }
                    }

                radar.orEmpty().isEmpty() ->
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.sori_releases_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }

                else ->
                    items(radar.orEmpty(), key = { "release_${it.release.id}" }) { release ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate(albumRoute(release)) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            AsyncImage(
                                model = release.release.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = release.release.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = releaseSubtitle(release),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (release.isNew) NewBadge()
                        }
                    }
            }
        }
    }
}
