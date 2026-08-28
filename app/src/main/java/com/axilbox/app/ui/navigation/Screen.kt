package com.axilbox.app.ui.navigation

sealed class Screen(val route: String) {
    object MainMenu : Screen("main_menu")
    object AddInstance : Screen("add_instance")
    object EditInstance : Screen("edit_instance/{instanceId}") {
        fun createRoute(instanceId: Long): String = "edit_instance/$instanceId"
    }
    object BootScreen : Screen("boot_screen/{instanceId}") {
        fun createRoute(instanceId: Long): String = "boot_screen/$instanceId"
    }
}
