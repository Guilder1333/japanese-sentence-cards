package com.griboedov.sentencecards.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.griboedov.sentencecards.R
import com.griboedov.sentencecards.ui.dictionary.DictionaryScreen
import com.griboedov.sentencecards.ui.importsentences.ImportSentencesScreen
import com.griboedov.sentencecards.ui.review.ReviewScreen
import com.griboedov.sentencecards.ui.words.WordBrowserScreen
import kotlinx.coroutines.launch

private sealed class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Review : Destination("review", "Review", Icons.Filled.Style)
    data object Import : Destination("import", "Import sentences", Icons.Filled.FileUpload)
    // "Words to learn": words already tracked in the internal database (imported or added from
    // the dictionary), as opposed to Dictionary, which browses the full bundled JMdict data.
    data object Words : Destination("words", "Words to learn", Icons.Filled.School)
    data object Dictionary : Destination("dictionary", "Dictionary", Icons.AutoMirrored.Filled.MenuBook)
}

private val destinations = listOf(Destination.Review, Destination.Import, Destination.Words, Destination.Dictionary)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentLabel = destinations.firstOrNull { d ->
        currentDestination?.hierarchy?.any { it.route == d.route } == true
    }?.label ?: stringResource(R.string.app_name)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                )
                destinations.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentLabel) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Review.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Review.route) { ReviewScreen() }
                composable(Destination.Import.route) { ImportSentencesScreen() }
                composable(Destination.Words.route) { WordBrowserScreen() }
                composable(Destination.Dictionary.route) { DictionaryScreen() }
            }
        }
    }
}
