package app.brain.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.brain.ui.analysis.AnalysisChatScreen
import app.brain.ui.analysis.AnalysisScreen
import app.brain.ui.detail.RecordDetailScreen
import app.brain.ui.imports.ImportScreen
import app.brain.ui.list.CardRecordsScreen
import app.brain.ui.list.RecordsListMode
import app.brain.ui.list.RecordsListScreen
import app.brain.ui.record.RecordEditorScreen
import app.brain.ui.settings.SettingsScreen
import app.brain.ui.suggestions.SuggestionScreen
import app.brain.ui.trash.TrashScreen
import app.brain.data.db.entity.CategoryEntity

object Routes {
    const val TABS = "tabs"
    const val EDITOR = "editor"
    const val EDITOR_EDIT = "editor/{recordId}"
    const val TIMELINE = "timeline"
    const val FAVORITES = "favorites"
    const val DETAIL = "detail/{recordId}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val SUGGESTIONS = "suggestions"
    const val IMPORT = "import"
    const val ANALYSIS = "analysis"
    const val ANALYSIS_CHAT = "analysis/{sessionId}"
    const val CARD = "card/{dimension}?categoryId={categoryId}"

    fun detail(id: String) = "detail/$id"
    fun edit(id: String) = "editor/$id"
    fun analysisChat(id: String) = "analysis/$id"
    fun card(dimension: String) = "card/$dimension"

    /** 跳到某个一级分类卡片，并预选一个二级分类（AI 分类完成后跳对应内容类型用）。 */
    fun cardWithCategory(dimension: String, categoryId: String) = "card/$dimension?categoryId=$categoryId"
}

@Composable
fun BrainNav(quickRecord: Boolean = false) {
    val navController = rememberNavController()

    LaunchedEffect(quickRecord) {
        if (quickRecord) {
            navController.navigate(Routes.EDITOR)
        }
    }

    NavHost(navController = navController, startDestination = Routes.TABS) {
        composable(Routes.TABS) {
            MainTabsScreen(
                onOpenEditor = { navController.navigate(Routes.EDITOR) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenSuggestions = { navController.navigate(Routes.SUGGESTIONS) },
                onOpenImport = { navController.navigate(Routes.IMPORT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAnalysis = { navController.navigate(Routes.ANALYSIS) },
                onOpenCard = { id -> navController.navigate(Routes.card(id)) },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.EDITOR) {
            RecordEditorScreen(
                onBack = { navController.popBackStack() },
                onNewRecordOrganized = { categoryId ->
                    navController.popBackStack()
                    if (categoryId != null) {
                        navController.navigate(Routes.cardWithCategory(CategoryEntity.DIM_TYPE, categoryId))
                    } else {
                        navController.navigate(Routes.card("all"))
                    }
                },
            )
        }
        composable(
            route = Routes.EDITOR_EDIT,
            arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) {
            RecordEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TIMELINE) {
            RecordsListScreen(
                mode = RecordsListMode.TIMELINE,
                title = "时间线",
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.FAVORITES) {
            RecordsListScreen(
                mode = RecordsListMode.FAVORITES,
                title = "收藏",
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("recordId").orEmpty()
            RecordDetailScreen(
                recordId = id,
                onBack = { navController.popBackStack() },
                onEdit = { recordId -> navController.navigate(Routes.edit(recordId)) },
                onOpenAnalysis = { sessionId -> navController.navigate(Routes.analysisChat(sessionId)) },
            )
        }
        composable(
            route = Routes.CARD,
            arguments = listOf(
                navArgument("dimension") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val dim = entry.arguments?.getString("dimension").orEmpty()
            CardRecordsScreen(
                dimension = dim,
                onBack = { navController.popBackStack() },
                onOpenDetail = { recordId -> navController.navigate(Routes.detail(recordId)) },
                onOpenEditor = { navController.navigate(Routes.EDITOR) },
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUGGESTIONS) {
            SuggestionScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT) {
            ImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANALYSIS) {
            AnalysisScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { sessionId -> navController.navigate(Routes.analysisChat(sessionId)) },
            )
        }
        composable(
            route = Routes.ANALYSIS_CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) {
            AnalysisChatScreen(onBack = { navController.popBackStack() })
        }
    }
}
