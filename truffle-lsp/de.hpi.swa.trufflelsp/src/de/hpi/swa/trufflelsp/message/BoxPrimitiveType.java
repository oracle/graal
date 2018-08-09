package de.hpi.swa.trufflelsp.message;

import com.oracle.truffle.api.interop.Message;

public class BoxPrimitiveType extends Message {

    public static final BoxPrimitiveType INSTANCE = new BoxPrimitiveType();

    @Override
    public boolean equals(Object message) {
        return message instanceof BoxPrimitiveType;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(INSTANCE);
    }

}
