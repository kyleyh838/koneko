package io.nekohasekai.sagernet.fmt.snell;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class SnellBean extends AbstractBean {

    // sing-box 1.14 Snell outbound: version 4 or 6.
    public Integer version;
    public String psk;
    public String userKey;
    public Boolean reuse;

    // v4 only: obfs_mode none/http/tls, obfs_host for http/tls.
    public String obfsMode;
    public String obfsHost;

    // v6 only: mode default/unshaped/unsafe-raw.
    public String v6Mode;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (version == null) version = 4;
        if (psk == null) psk = "";
        if (userKey == null) userKey = "";
        if (reuse == null) reuse = false;
        if (obfsMode == null) obfsMode = "none";
        if (obfsHost == null) obfsHost = "";
        if (v6Mode == null) v6Mode = "default";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeInt(version);
        output.writeString(psk);
        output.writeString(userKey);
        output.writeBoolean(reuse);
        output.writeString(obfsMode);
        output.writeString(obfsHost);
        output.writeString(v6Mode);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        this.version = input.readInt();
        psk = input.readString();
        userKey = input.readString();
        reuse = input.readBoolean();
        obfsMode = input.readString();
        obfsHost = input.readString();
        v6Mode = input.readString();
    }

    @NotNull
    @Override
    public SnellBean clone() {
        return KryoConverters.deserialize(new SnellBean(), KryoConverters.serialize(this));
    }

    public static final Creator<SnellBean> CREATOR = new CREATOR<SnellBean>() {
        @NonNull
        @Override
        public SnellBean newInstance() {
            return new SnellBean();
        }

        @Override
        public SnellBean[] newArray(int size) {
            return new SnellBean[size];
        }
    };
}
