package com.photodashboard.app.ui

data class RegisterForm(
    val email: String,
    val password: String,
    val username: String,
    val firstName: String,
    val lastName: String,
) {
    companion object {
        /** Same rule as the server: 3-30 letters, numbers, dots, dashes or underscores. */
        val USERNAME = Regex("^[A-Za-z0-9._-]{3,30}$")
    }
}
