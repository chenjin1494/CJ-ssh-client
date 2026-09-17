package io.github.chenjin.androidsshclient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.chenjin.androidsshclient.core.ui.theme.AndroidSshTheme
import io.github.chenjin.androidsshclient.feature.connections.ConnectionsScreen
import io.github.chenjin.androidsshclient.feature.settings.SettingsScreen
import io.github.chenjin.androidsshclient.feature.terminal.TerminalScreen
import io.github.chenjin.androidsshclient.feature.tools.ToolsScreen

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("connections", "连接", Icons.Outlined.Computer),
    Destination("terminal", "终端", Icons.Outlined.Terminal),
    Destination("tools", "工具", Icons.Outlined.Workspaces),
    Destination("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun AndroidSshApp(viewModel: MainViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prompt by viewModel.hostPrompt.collectAsStateWithLifecycle()
    AndroidSshTheme(settings) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "connections"
        val navigate: (String) -> Unit = { target ->
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Row(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        ) {
            PersistentNavigationRail(route = route, onNavigate = navigate)
            Box(Modifier.weight(1f).fillMaxSize()) { AppNavHost(navController, navigate) }
        }
        prompt?.let { request ->
            AlertDialog(
                onDismissRequest = { viewModel.answerHostKey(false) },
                icon = { Icon(Icons.Outlined.Warning, null, Modifier.size(30.dp)) },
                title = { Text(if (request.changed) "主机密钥已更改" else "确认主机密钥") },
                text = {
                    Text(
                        if (request.changed) "${request.host}:${request.port} 的密钥与已保存记录不一致。为防止中间人攻击，连接已阻断。\n\n算法：${request.algorithm}\n指纹：${request.fingerprint}"
                        else "首次连接 ${request.host}:${request.port}。请通过可信渠道核对指纹后确认。\n\n算法：${request.algorithm}\n指纹：${request.fingerprint}",
                    )
                },
                confirmButton = {
                    if (!request.changed) Button(onClick = { viewModel.answerHostKey(true) }) { Text("信任并连接") }
                    else Button(onClick = { viewModel.answerHostKey(false) }) { Text("关闭") }
                },
                dismissButton = { if (!request.changed) TextButton(onClick = { viewModel.answerHostKey(false) }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun PersistentNavigationRail(route: String, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier.width(76.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            destinations.forEach { item ->
                NavigationRailItem(
                    selected = route == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, item.label) },
                    label = { Text(item.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: androidx.navigation.NavHostController, navigate: (String) -> Unit) {
    NavHost(navController, startDestination = "connections") {
        composable("connections") { ConnectionsScreen(onOpenTerminal = { navigate("terminal") }) }
        composable("terminal") { TerminalScreen(onAddConnection = { navigate("connections") }) }
        composable("tools") { ToolsScreen() }
        composable("settings") { SettingsScreen() }
    }
}
