package app.brain.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.ui.archive.ArchiveScreen
import app.brain.ui.home.ContainHomeScreen
import kotlinx.coroutines.launch

/** 主界面：底部两个页签「收容 / 收容所」，侧边抽屉入口放在收容所页。 */
@Composable
fun MainTabsScreen(
    onOpenEditor: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: MainTabsViewModel = hiltViewModel(),
) {
    val suggestionCount by viewModel.suggestionCount.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "脑内收容所",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp),
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("收藏") },
                    icon = { Icon(Icons.Filled.Star, null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenFavorites()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("回收站") },
                    icon = { Icon(Icons.Filled.Delete, null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenTrash()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("整理建议")
                            if (suggestionCount > 0) {
                                Text(
                                    text = suggestionCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSuggestions()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("深度分析") },
                    icon = { Icon(Icons.Filled.Send, null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenAnalysis()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("批量导入") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenImport()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("设置") },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("外观") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenAppearance()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        label = { Text("收容") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("收容所") },
                    )
                }
            },
        ) { innerPadding ->
            when (selectedTab) {
                0 -> ContainHomeScreen(
                    onOpenEditor = onOpenEditor,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                else -> ArchiveScreen(
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onOpenCard = onOpenCard,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}
