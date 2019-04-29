package com.oracle.truffle.espresso.interop;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class ForeignToStaticObjectNode extends Node {
    public abstract Object executeConvert(Meta meta, Object value);

    @Child Node isBoxed = Message.IS_BOXED.createNode();
    @Child Node unbox = Message.UNBOX.createNode();

    @Specialization
    protected Object convertStaticObjectArray(@SuppressWarnings("unused") Meta meta, StaticObject[] array) {
        return StaticObject.wrap(array);
    }

    @Specialization(guards = "value.getClass().isArray()")
    protected Object convertArray(@SuppressWarnings("unused") Meta meta, Object value) {
        return StaticObject.wrapPrimitiveArray(value);
    }

    @Specialization
    protected Object convertTruffle(@SuppressWarnings("unused") Meta meta, TruffleObject value) {
        if (ForeignAccess.sendIsBoxed(isBoxed, value)) {
            try {
                // return Meta.box(meta, ForeignAccess.sendUnbox(unbox, value));
                return ForeignAccess.sendUnbox(unbox, value);
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    @Fallback
    protected Object convert(Meta meta, Object value) {
        return Meta.box(meta, value);
    }
}