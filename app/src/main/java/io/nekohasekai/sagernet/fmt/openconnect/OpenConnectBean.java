package io.nekohasekai.sagernet.fmt.openconnect;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class OpenConnectBean extends AbstractBean {

    // sing-box 1.14 OpenConnect endpoint. Only the common client fields are
    // modeled; CSD/HIP/TNCC wrappers and the like are out of scope.
    // flavor: anyconnect/gp/fortinet/f5/pulse/nc.
    public String flavor;
    public String username;
    public String password;
    public String authGroup;
    public String cookie;
    // Token auth: mode totp/hotp/stoken/oidc (or empty = none).
    public String tokenMode;
    public String tokenSecret;
    public String tokenPin;
    public String reportedOs;
    public String userAgent;
    public String localHostname;
    public Boolean noUdp;
    public Integer mtu;
    public Boolean tlsInsecure;
    public String tlsServerName;
    // PEM texts.
    public String caCert;
    public String clientCert;
    public String clientKey;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == null) serverPort = 443;
        super.initializeDefaultValues();
        if (flavor == null) flavor = "anyconnect";
        if (username == null) username = "";
        if (password == null) password = "";
        if (authGroup == null) authGroup = "";
        if (cookie == null) cookie = "";
        if (tokenMode == null) tokenMode = "";
        if (tokenSecret == null) tokenSecret = "";
        if (tokenPin == null) tokenPin = "";
        if (reportedOs == null) reportedOs = "";
        if (userAgent == null) userAgent = "";
        if (localHostname == null) localHostname = "";
        if (noUdp == null) noUdp = false;
        if (mtu == null) mtu = 0;
        if (tlsInsecure == null) tlsInsecure = false;
        if (tlsServerName == null) tlsServerName = "";
        if (caCert == null) caCert = "";
        if (clientCert == null) clientCert = "";
        if (clientKey == null) clientKey = "";
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(flavor);
        output.writeString(username);
        output.writeString(password);
        output.writeString(authGroup);
        output.writeString(cookie);
        output.writeString(tokenMode);
        output.writeString(tokenSecret);
        output.writeString(tokenPin);
        output.writeString(reportedOs);
        output.writeString(userAgent);
        output.writeString(localHostname);
        output.writeBoolean(noUdp);
        output.writeInt(mtu);
        output.writeBoolean(tlsInsecure);
        output.writeString(tlsServerName);
        output.writeString(caCert);
        output.writeString(clientCert);
        output.writeString(clientKey);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        flavor = input.readString();
        username = input.readString();
        password = input.readString();
        authGroup = input.readString();
        cookie = input.readString();
        tokenMode = input.readString();
        tokenSecret = input.readString();
        tokenPin = input.readString();
        reportedOs = input.readString();
        userAgent = input.readString();
        localHostname = input.readString();
        noUdp = input.readBoolean();
        mtu = input.readInt();
        tlsInsecure = input.readBoolean();
        tlsServerName = input.readString();
        caCert = input.readString();
        clientCert = input.readString();
        clientKey = input.readString();
    }

    @NotNull
    @Override
    public OpenConnectBean clone() {
        return KryoConverters.deserialize(new OpenConnectBean(), KryoConverters.serialize(this));
    }

    public static final Creator<OpenConnectBean> CREATOR = new CREATOR<OpenConnectBean>() {
        @NonNull
        @Override
        public OpenConnectBean newInstance() {
            return new OpenConnectBean();
        }

        @Override
        public OpenConnectBean[] newArray(int size) {
            return new OpenConnectBean[size];
        }
    };
}
