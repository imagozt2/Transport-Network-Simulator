package com.rmm.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rmm.app.R
import com.rmm.app.core.session.PassengerSession
import com.rmm.app.ui.screen.NavigationDestinationScreen
import com.rmm.app.ui.component.RMMTopAppBar
import com.rmm.app.ui.screen.account.AccountScreen
import com.rmm.app.ui.screen.journeys.JourneysScreen
import com.rmm.app.ui.screen.tickets.TicketsScreen

@Composable
fun RMMNavigation(
    session: PassengerSession,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    val selectedDestination = RMMTopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    } ?: RMMTopLevelDestination.HOME

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RMMTopAppBar(selectedDestination.labelResource) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                RMMTopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { androidx.compose.material3.Text(stringResource(destination.labelResource)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { contentPadding ->
        RMMNavHost(
            navController = navController,
            session = session,
            onLoggedOut = onLoggedOut,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun RMMNavHost(
    navController: NavHostController,
    session: PassengerSession,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = RMMTopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(RMMTopLevelDestination.HOME.route) {
            NavigationDestinationScreen(
                titleResource = R.string.home_title,
                descriptionResource = R.string.home_description,
            )
        }
        composable(RMMTopLevelDestination.TICKETS.route) {
            TicketsScreen(session = session)
        }
        composable(RMMTopLevelDestination.JOURNEYS.route) {
            JourneysScreen(session = session)
        }
        composable(RMMTopLevelDestination.ACCOUNT.route) {
            AccountScreen(
                session = session,
                onLoggedOut = onLoggedOut,
            )
        }
    }
}
