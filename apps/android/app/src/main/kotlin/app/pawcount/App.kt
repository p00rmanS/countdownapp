package app.pawcount

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

/*
 * App.kt - the activity, the view model (shared state + simple back stack) and the navigation root.
 *
 * Navigation is just a list of Route values; the top one is shown. Back pops. There is no framework router
 * because there are only six screens.
 */

sealed interface Route {
    data object Welcome : Route
    data object Home : Route
    data object Memories : Route
    data object Settings : Route
    data class Detail(val id: String) : Route
    data class Flow(val editId: String?) : Route
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    val art = Art(app)
    val fonts = PawFonts(app)
    val data get() = repo.data
    val stack = mutableStateListOf<Route>(if (repo.data.value.onboarded || repo.data.value.countdowns.isNotEmpty()) Route.Home else Route.Welcome)
    var toast by mutableStateOf<String?>(null)
    /** the companion picked during the welcome screens, so the create flow can start with it */
    var welcomeBreed by mutableStateOf("golden")
    var welcomeBreedChosen by mutableStateOf(false)
    /** bumps every time something should rain confetti (the overlay in PawApp watches this) */
    var confettiBurst by mutableStateOf(0)
    fun confetti() { confettiBurst++ }

    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else stack[0] = Route.Home }
    fun replaceTop(r: Route) { stack[stack.lastIndex] = r }
    fun goHome() { stack.clear(); stack.add(Route.Home) }
    fun tab(r: Route) { stack.clear(); stack.add(if (r == Route.Home) Route.Home else r) }

    fun say(msg: String) { toast = msg }
    fun upsert(c: Countdown) = repo.update { d -> d.copy(countdowns = if (d.countdowns.any { it.id == c.id }) d.countdowns.map { if (it.id == c.id) c else it } else d.countdowns + c) }
    fun remove(id: String) = repo.update { d -> d.copy(countdowns = d.countdowns.filterNot { it.id == id }) }
    fun settings(f: (Settings) -> Settings) = repo.update { it.copy(settings = f(it.settings)) }
    fun loadSamples() = repo.update { it.copy(onboarded = true, countdowns = it.countdowns.filterNot { c -> c.sample } + makeSamples()) }
    /** adds a countdown from a shared link / code (see Share.kt). Returns the new countdown's id, or null if the text isn't one. */
    fun importShared(text: String): String? {
        val c = Share.parse(text) ?: return null
        upsert(c); repo.update { it.copy(onboarded = true) }
        return c.id
    }
    fun finishWelcome() = repo.update { it.copy(onboarded = true) }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PawApp(vm) }
        handle(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handle(intent) }

    /** pawcount://import?d=...  (a shared countdown)  or  pawcount://detail/<id>  (from a widget) */
    private fun handle(i: android.content.Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme != "pawcount") return
        if (uri.host == "import") {
            val id = vm.importShared(uri.toString())
            if (id != null) { vm.say("Added to your countdowns 🐾"); vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Detail(id)) } else vm.say("That link isn’t a Pawcount countdown")
        } else if (uri.host == "new") {
            vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Flow(null))
        } else if (uri.host == "memories") {
            vm.stack.clear(); vm.stack.add(Route.Memories)
        } else if (uri.host == "detail") {
            val id = uri.lastPathSegment
            if (id != null && vm.data.value.countdowns.any { it.id == id }) { vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Detail(id)) }
        }
    }
}

/** ticks once a second so countdowns count down */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }

@Composable
fun PawApp(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    PawTheme(data.settings, vm.fonts) {
        val c = LocalPaw.current
        val reduce = data.settings.reduceMotion == "on" || (data.settings.reduceMotion == "system" && isSystemReduceMotion())
        androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides reduce) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                val route = vm.current
                BackHandler(enabled = vm.stack.size > 1 || route != Route.Home) { vm.pop() }
                AnimatedContent(route, transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 24 }) togetherWith fadeOut(tween(140)) }, label = "route") { r ->
                    when (r) {
                        Route.Welcome -> WelcomeScreen(vm)
                        Route.Home -> HomeScreen(vm)
                        Route.Memories -> MemoriesScreen(vm)
                        Route.Settings -> SettingsScreen(vm)
                        is Route.Detail -> DetailScreen(vm, r.id)
                        is Route.Flow -> FlowScreen(vm, r.editId)
                    }
                }
                ConfettiOverlay(vm.confettiBurst, reduce)
                ToastHost(vm)
            }
        }
    }
}

val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun isSystemReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
