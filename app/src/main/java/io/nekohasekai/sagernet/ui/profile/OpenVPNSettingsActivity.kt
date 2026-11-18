package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.openvpn.OpenVPNBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class OpenVPNSettingsActivity : ProfileSettingsActivity<OpenVPNBean>() {

    override fun createEntity() = OpenVPNBean().applyDefaultValues()

    override fun OpenVPNBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverNetwork = network
        DataStore.serverOpenVPNMode = mode
        DataStore.serverOpenVPNAddress = address
        DataStore.serverEncryption = cipher
        DataStore.serverOpenVPNDataCiphers = dataCiphers
        DataStore.serverOpenVPNAuth = auth
        DataStore.serverCertificates = caCert
        DataStore.serverOpenVPNClientCert = clientCert
        DataStore.serverOpenVPNClientKey = clientKey
        DataStore.serverOpenVPNPeerFingerprint = peerFingerprint
        DataStore.serverOpenVPNControlWrapType = controlWrapType.ifBlank { "none" }
        DataStore.serverOpenVPNControlWrapKey = controlWrapKey
        DataStore.serverOpenVPNControlWrapDirection = controlWrapDirection.ifBlank { "none" }
        DataStore.serverOpenVPNStaticKey = staticKey
        DataStore.serverOpenVPNKeyDirection = keyDirection.ifBlank { "none" }
        DataStore.serverOpenVPNRemoteRandom = remoteRandom
        DataStore.serverOpenVPNRouteNoPull = routeNoPull
        DataStore.serverOpenVPNRedirectGateway = redirectGateway
        DataStore.serverMTU = mtu
    }

    override fun OpenVPNBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        network = DataStore.serverNetwork
        mode = DataStore.serverOpenVPNMode
        address = DataStore.serverOpenVPNAddress
        cipher = DataStore.serverEncryption
        dataCiphers = DataStore.serverOpenVPNDataCiphers
        auth = DataStore.serverOpenVPNAuth
        caCert = DataStore.serverCertificates
        clientCert = DataStore.serverOpenVPNClientCert
        clientKey = DataStore.serverOpenVPNClientKey
        peerFingerprint = DataStore.serverOpenVPNPeerFingerprint
        controlWrapType = DataStore.serverOpenVPNControlWrapType.takeUnless { it == "none" } ?: ""
        controlWrapKey = DataStore.serverOpenVPNControlWrapKey
        controlWrapDirection = DataStore.serverOpenVPNControlWrapDirection.takeUnless { it == "none" } ?: ""
        staticKey = DataStore.serverOpenVPNStaticKey
        keyDirection = DataStore.serverOpenVPNKeyDirection.takeUnless { it == "none" } ?: ""
        remoteRandom = DataStore.serverOpenVPNRemoteRandom
        routeNoPull = DataStore.serverOpenVPNRouteNoPull
        redirectGateway = DataStore.serverOpenVPNRedirectGateway
        mtu = DataStore.serverMTU
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.openvpn_preferences)

        val mode = findPreference<Preference>(Key.SERVER_OPENVPN_MODE)!!
        val tlsPrefs = listOf(
            Key.SERVER_CERTIFICATES,
            Key.SERVER_OPENVPN_CLIENT_CERT,
            Key.SERVER_OPENVPN_CLIENT_KEY,
            Key.SERVER_OPENVPN_PEER_FINGERPRINT,
            Key.SERVER_OPENVPN_CONTROL_WRAP_TYPE,
            Key.SERVER_OPENVPN_CONTROL_WRAP_KEY,
            Key.SERVER_OPENVPN_CONTROL_WRAP_DIRECTION,
        ).map { findPreference<Preference>(it)!! }
        val staticPrefs = listOf(
            Key.SERVER_OPENVPN_STATIC_KEY,
            Key.SERVER_OPENVPN_KEY_DIRECTION,
        ).map { findPreference<Preference>(it)!! }
        fun syncVisibility(m: String) {
            val isTls = m != "static_key"
            tlsPrefs.forEach { it.isVisible = isTls }
            staticPrefs.forEach { it.isVisible = !isTls }
        }
        syncVisibility(DataStore.serverOpenVPNMode)
        mode.setOnPreferenceChangeListener { _, newValue ->
            syncVisibility(newValue as? String ?: "tls")
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
