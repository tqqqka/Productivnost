package com.tihon.productivnost.ui

sealed interface Route {
    data object Home : Route
    data object Sessions : Route
    data object Garden : Route
    data object More : Route

    data class PlantDetail(val id: Long) : Route
    data object AllowedApps : Route
    data object Limits : Route
    data object Tasks : Route
    data object Settings : Route
    data object Cleanup : Route
    data object Access : Route
}

val Route.isTab: Boolean
    get() = this is Route.Home || this is Route.Sessions || this is Route.Garden || this is Route.More

val tabs = listOf(Route.Home, Route.Sessions, Route.Garden, Route.More)
