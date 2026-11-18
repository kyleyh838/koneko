package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.openconnect.OpenConnectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class OpenConnectSettingsActivity : ProfileSettingsActivity<OpenConnectBean>() {

    override fun createEntity() = OpenConnectBean().applyDefaultValues()

    override fun OpenConnectBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverOpenConnectFlavor = flavor
        DataStore.serverOpenConnectAuthGroup = authGroup
        DataStore.serverOpenConnectCookie = cookie
        DataStore.serverOpenConnectTokenMode = tokenMode.ifBlank { "none" }
        DataStore.serverOpenConnectTokenSecret = tokenSecret
        DataStore.serverOpenConnectTokenPin = tokenPin
        DataStore.serverOpenConnectReportedOS = reportedOs
        DataStore.serverOpenConnectUserAgent = userAgent
        DataStore.serverOpenConnectLocalHostname = localHostname
        DataStore.serverOpenConnectNoUDP = noUdp
        DataStore.serverMTU = mtu
        DataStore.serverAllowInsecure = tlsInsecure
        DataStore.serverSNI = tlsServerName
        DataStore.serverCertificates = caCert
        DataStore.serverOpenConnectClientCert = clientCert
        DataStore.serverOpenConnectClientKey = clientKey
    }

    override fun OpenConnectBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        flavor = DataStore.serverOpenConnectFlavor
        authGroup = DataStore.serverOpenConnectAuthGroup
        cookie = DataStore.serverOpenConnectCookie
        tokenMode = DataStore.serverOpenConnectTokenMode.takeUnless { it == "none" } ?: ""
        tokenSecret = DataStore.serverOpenConnectTokenSecret
        tokenPin = DataStore.serverOpenConnectTokenPin
        reportedOs = DataStore.serverOpenConnectReportedOS
        userAgent = DataStore.serverOpenConnectUserAgent
        localHostname = DataStore.serverOpenConnectLocalHostname
        noUdp = DataStore.serverOpenConnectNoUDP
        mtu = DataStore.serverMTU
        tlsInsecure = DataStore.serverAllowInsecure
        tlsServerName = DataStore.serverSNI
        caCert = DataStore.serverCertificates
        clientCert = DataStore.serverOpenConnectClientCert
        clientKey = DataStore.serverOpenConnectClientKey
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.openconnect_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
