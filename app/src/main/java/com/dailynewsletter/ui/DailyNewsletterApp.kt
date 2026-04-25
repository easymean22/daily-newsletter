package com.dailynewsletter.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailynewsletter.ui.keyword.KeywordScreen
import com.dailynewsletter.ui.newsletter.NewsletterScreen
import com.dailynewsletter.ui.settings.SettingsScreen
import com.dailynewsletter.ui.theme.DailyNewsletterTheme
import com.dailynewsletter.ui.topics.TopicsScreen

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Keywords("keywords", "키워드", Icons.Default.Bookmark),
    Topics("topics", "주제", Icons.Default.Article),
    Newsletter("newsletter", "뉴스레터", Icons.Default.Newspaper),
    Settings("settings", "설정", Icons.Default.Settings)
}

@Composable
fun DailyNewsletterApp() {
    DailyNewsletterTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Keywords.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Keywords.route) { KeywordScreen() }
                composable(Screen.Topics.route) { TopicsScreen() }
                composable(Screen.Newsletter.route) { NewsletterScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}
