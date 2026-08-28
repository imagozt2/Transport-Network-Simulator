package com.rmm.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.vector.ImageVector
import com.rmm.app.R

enum class RMMTopLevelDestination(
    val route: String,
    @StringRes val labelResource: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.navigation_home, Icons.Default.Home),
    TICKETS("tickets", R.string.navigation_tickets, Icons.Default.List),
    JOURNEYS("journeys", R.string.navigation_journeys, Icons.Default.Place),
    ACCOUNT("account", R.string.navigation_account, Icons.Default.AccountCircle),
}
