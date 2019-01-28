package org.graalvm.tools.lsp.interop;

import com.oracle.truffle.api.interop.Message;

public class GetDocumentation extends Message {

    public static final GetDocumentation INSTANCE = new GetDocumentation();

    @Override
    public boolean equals(Object message) {
        return message instanceof GetDocumentation;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(INSTANCE);
    }

}
