package com.example.baith2.model

data class Contact(
    val id: Long,
    val name: String,
    val phones: List<String>,
    val email: String = ""
) {
    val primaryPhone: String get() = phones.firstOrNull() ?: ""
}