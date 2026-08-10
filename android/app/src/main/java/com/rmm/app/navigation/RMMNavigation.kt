package com.rmm.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rmm.app.R
import com.rmm.app.ui.screen.NavigationDestinationScreen

@Composable
fun RMMNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
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
                    )
                }
            }
        },
    ) { contentPadding ->
        RMMNavHost(
            navController = navController,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun RMMNavHost(
    navController: NavHostController,
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
            NavigationDestinationScreen(
                titleResource = R.string.tickets_title,
                descriptionResource = R.string.tickets_description,
            )
        }
        composable(RMMTopLevelDestination.JOURNEYS.route) {
            NavigationDestinationScreen(
                titleResource = R.string.journeys_title,
                descriptionResource = R.string.journeys_description,
            )
        }
        composable(RMMTopLevelDestination.ACCOUNT.route) {
            NavigationDestinationScreen(
                titleResource = R.string.account_title,
                descriptionResource = R.string.account_description,
            )
        }
    }
}
