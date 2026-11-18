package io.nekohasekai.sagernet.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

object Theme {

    fun apply(context: Context) {
        context.setTheme(R.style.Theme_SagerNet)
    }

    fun applyDialog(context: Context) {
        context.setTheme(R.style.Theme_SagerNet_Dialog)
    }

    fun applyNightTheme() {
        applyNightTheme(DataStore.darkMode)
    }

    fun applyNightTheme(dark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

}
