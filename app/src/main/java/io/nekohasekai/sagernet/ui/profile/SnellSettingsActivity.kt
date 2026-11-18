package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.snell.SnellBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class SnellSettingsActivity : ProfileSettingsActivity<SnellBean>() {

    override fun createEntity() = SnellBean().applyDefaultValues()

    override fun SnellBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = psk
        DataStore.serverUsername = userKey
        DataStore.serverSnellVersion = version
        DataStore.serverObfs = obfsMode
        DataStore.serverHost = obfsHost
        DataStore.serverSnellMode = v6Mode
        DataStore.serverSnellReuse = reuse
    }

    override fun SnellBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        psk = DataStore.serverPassword
        userKey = DataStore.serverUsername
        version = DataStore.serverSnellVersion
        obfsMode = DataStore.serverObfs
        obfsHost = DataStore.serverHost
        v6Mode = DataStore.serverSnellMode
        reuse = DataStore.serverSnellReuse
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.snell_preferences)

        val version = findPreference<Preference>(Key.SERVER_SNELL_VERSION)!!
        val obfsMode = findPreference<Preference>(Key.SERVER_OBFS)!!
        val obfsHost = findPreference<Preference>(Key.SERVER_HOST)!!
        val v6Mode = findPreference<Preference>(Key.SERVER_SNELL_MODE)!!
        fun syncVisibility(v: Int) {
            val isV4 = v == 4
            obfsMode.isVisible = isV4
            obfsHost.isVisible = isV4
            v6Mode.isVisible = !isV4
        }
        syncVisibility(DataStore.serverSnellVersion)
        version.setOnPreferenceChangeListener { _, newValue ->
            syncVisibility((newValue as? String)?.toIntOrNull() ?: 4)
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
