package io.nekohasekai.sagernet.fmt.openvpn;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class OpenVPNBean extends AbstractBean {

    // sing-box 1.14 OpenVPN client endpoint. Only the common client fields
    // are modeled; exotic server-side options are out of scope.
    // network: udp/udp4/udp6/tcp/tcp4/tcp6. mode: tls/static_key.
    public String network;
    public String mode;
    public String username;
    public String password;
    // Local tunnel addresses, comma/line separated CIDRs (usually pushed).
    public String address;
    public String cipher;
    // Data ciphers, comma/line separated.
    public String dataCiphers;
    public String auth;
    // PEM texts.
    public String caCert;
    public String clientCert;
    public String clientKey;
    // Peer fingerprints, comma/line separated.
    public String peerFingerprint;
    // Control channel wrap: tls_auth/tls_crypt/tls_crypt_v2 (or empty).
    public String controlWrapType;
    public String controlWrapKey;
    // Key direction: server/client (or empty).
    public String controlWrapDirection;
    // Static-key mode: key text (or empty) + direction.
    public String staticKey;
    public String keyDirection;
    public Boolean remoteRandom;
    public Boolean routeNoPull;
    public Boolean redirectGateway;
    public Integer mtu;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == null) serverPort = 1194;
        super.initializeDefaultValues();
        if (network == null) network = "udp";
        if (mode == null) mode = "tls";
        if (username == null) username = "";
        if (password == null) password = "";
        if (address == null) address = "";
        if (cipher == null) cipher = "";
        if (dataCiphers == null) dataCiphers = "";
        if (auth == null) auth = "";
        if (caCert == null) caCert = "";
        if (clientCert == null) clientCert = "";
        if (clientKey == null) clientKey = "";
        if (peerFingerprint == null) peerFingerprint = "";
        if (controlWrapType == null) controlWrapType = "";
        if (controlWrapKey == null) controlWrapKey = "";
        if (controlWrapDirection == null) controlWrapDirection = "";
        if (staticKey == null) staticKey = "";
        if (keyDirection == null) keyDirection = "";
        if (remoteRandom == null) remoteRandom = false;
        if (routeNoPull == null) routeNoPull = false;
        if (redirectGateway == null) redirectGateway = false;
        if (mtu == null) mtu = 0;
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(network);
        output.writeString(mode);
        output.writeString(username);
        output.writeString(password);
        output.writeString(address);
        output.writeString(cipher);
        output.writeString(dataCiphers);
        output.writeString(auth);
        output.writeString(caCert);
        output.writeString(clientCert);
        output.writeString(clientKey);
        output.writeString(peerFingerprint);
        output.writeString(controlWrapType);
        output.writeString(controlWrapKey);
        output.writeString(controlWrapDirection);
        output.writeString(staticKey);
        output.writeString(keyDirection);
        output.writeBoolean(remoteRandom);
        output.writeBoolean(routeNoPull);
        output.writeBoolean(redirectGateway);
        output.writeInt(mtu);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        network = input.readString();
        mode = input.readString();
        username = input.readString();
        password = input.readString();
        address = input.readString();
        cipher = input.readString();
        dataCiphers = input.readString();
        auth = input.readString();
        caCert = input.readString();
        clientCert = input.readString();
        clientKey = input.readString();
        peerFingerprint = input.readString();
        controlWrapType = input.readString();
        controlWrapKey = input.readString();
        controlWrapDirection = input.readString();
        staticKey = input.readString();
        keyDirection = input.readString();
        remoteRandom = input.readBoolean();
        routeNoPull = input.readBoolean();
        redirectGateway = input.readBoolean();
        mtu = input.readInt();
    }

    @NotNull
    @Override
    public OpenVPNBean clone() {
        return KryoConverters.deserialize(new OpenVPNBean(), KryoConverters.serialize(this));
    }

    public static final Creator<OpenVPNBean> CREATOR = new CREATOR<OpenVPNBean>() {
        @NonNull
        @Override
        public OpenVPNBean newInstance() {
            return new OpenVPNBean();
        }

        @Override
        public OpenVPNBean[] newArray(int size) {
            return new OpenVPNBean[size];
        }
    };
}
