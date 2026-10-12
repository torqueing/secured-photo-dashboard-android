package com.photodashboard.app

import com.photodashboard.app.ui.RegisterForm
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterFormTest {
    @Test
    fun usernameRules() {
        for (ok in listOf("john", "John.Smith", "j_s-1", "abc")) assertTrue(ok, RegisterForm.USERNAME.matches(ok))
        for (bad in listOf("ab", "has space", "x".repeat(31), "émile", "")) assertFalse(bad, RegisterForm.USERNAME.matches(bad))
    }
}
