package com.rui.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rui.app.ChatViewModel
import com.rui.app.ui.screens.ChatScreen
import com.rui.app.ui.screens.ModelsScreen
import com.rui.app.ui.screens.Sidebar
import kotlinx.coroutines.launch

enum class Screen { Chat, Models }

@Composable
fun RuiApp(vm: ChatViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(screen != Screen.Chat) { screen = Screen.Chat }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = screen == Screen.Chat,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Sidebar(
                    vm = vm,
                    onNewChat = { vm.newChat(); screen = Screen.Chat; scope.launch { drawer.close() } },
                    onOpenChat = { vm.openChat(it); screen = Screen.Chat; scope.launch { drawer.close() } },
                    onOpenModels = { screen = Screen.Models; scope.launch { drawer.close() } },
                )
            }
        },
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 }) togetherWith fadeOut(tween(120))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Chat -> ChatScreen(vm, onMenu = { scope.launch { drawer.open() } }, onOpenModels = { screen = Screen.Models })
                Screen.Models -> ModelsScreen(vm, onBack = { screen = Screen.Chat })
            }
        }
    }
}
