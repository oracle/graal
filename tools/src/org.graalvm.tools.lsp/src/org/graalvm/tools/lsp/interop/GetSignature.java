package org.graalvm.tools.lsp.interop;

import com.oracle.truffle.api.interop.Message;

public class GetSignature extends Message {

    public static final GetSignature INSTANCE = new GetSignature();

    @Override
    public boolean equals(Object message) {
        return message instanceof GetSignature;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(INSTANCE);
    }

}
