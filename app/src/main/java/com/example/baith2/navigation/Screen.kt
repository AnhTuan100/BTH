package com.example.baith2.navigation

sealed class Screen(val route: String) {
    object ContactList : Screen("contact_list")

    object ContactDetail : Screen("contact_detail/{contactId}") {
        fun createRoute(contactId: Long) = "contact_detail/$contactId"
    }

    object ContactEdit : Screen("contact_edit/{contactId}") {
        fun createRoute(contactId: Long) = "contact_edit/$contactId"
        const val NEW = "contact_edit/-1"
    }
}
