package com.example.cricketscoring.navigation

import com.example.cricketscoring.model.CricketStyle
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Fixtures : AppRoute

    @Serializable
    data class StyleHub(val styleName: String) : AppRoute {
        fun style(): CricketStyle = CricketStyle.valueOf(styleName)
        companion object {
            fun of(style: CricketStyle) = StyleHub(style.name)
        }
    }

    @Serializable
    data class Wizard(val styleName: String) : AppRoute {
        fun style(): CricketStyle = CricketStyle.valueOf(styleName)
        companion object {
            fun of(style: CricketStyle) = Wizard(style.name)
        }
    }

    @Serializable
    data class Live(val matchId: String) : AppRoute

    @Serializable
    data class Scorecard(val matchId: String) : AppRoute

    @Serializable
    data object Teams : AppRoute

    @Serializable
    data class TeamEdit(val teamId: String? = null) : AppRoute

    @Serializable
    data object Players : AppRoute

    @Serializable
    data object Tournaments : AppRoute

    @Serializable
    data class TournamentDetail(val tournamentId: String) : AppRoute

    @Serializable
    data object Stats : AppRoute

    @Serializable
    data object More : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object RulesGuide : AppRoute
}

enum class AppTab(val route: AppRoute, val glyph: String, val label: String) {
    Home(AppRoute.Home, "HM", "Home"),
    Fixtures(AppRoute.Fixtures, "FX", "Fixtures"),
    Teams(AppRoute.Teams, "TM", "Teams"),
    Players(AppRoute.Players, "PL", "Players"),
    More(AppRoute.More, "···", "More"),
}

fun AppRoute.showsBottomBar(): Boolean = when (this) {
    AppRoute.Home,
    AppRoute.Fixtures,
    is AppRoute.StyleHub,
    AppRoute.Teams,
    is AppRoute.TeamEdit,
    AppRoute.Players,
    AppRoute.More,
    AppRoute.Tournaments,
    is AppRoute.TournamentDetail,
    AppRoute.Stats,
    AppRoute.Settings,
    AppRoute.RulesGuide,
    -> true
    is AppRoute.Wizard, is AppRoute.Live, is AppRoute.Scorecard -> false
}

fun AppRoute.toTab(): AppTab = when (this) {
    AppRoute.Home -> AppTab.Home
    AppRoute.Fixtures, is AppRoute.StyleHub -> AppTab.Fixtures
    AppRoute.Teams, is AppRoute.TeamEdit -> AppTab.Teams
    AppRoute.Players -> AppTab.Players
    else -> AppTab.More
}
