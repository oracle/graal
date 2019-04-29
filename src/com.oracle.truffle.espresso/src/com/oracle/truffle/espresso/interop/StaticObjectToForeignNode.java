package com.oracle.truffle.espresso.interop;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class StaticObjectToForeignNode extends Node {
    public abstract Object executeConvert(Meta meta, Object value);

    @Specialization(guards = "value.isStaticObjectArray()")
    protected Object fromStaticObjectArray(Meta meta, StaticObject value) {
        return convertStaticObjectArray(meta, value.unwrap());
    }

    @Specialization(guards = "value.isArray()")
    protected Object fromArray(@SuppressWarnings("unused") Meta meta, StaticObject value) {
        return value.unwrap();
    }

    @Specialization
    protected Object fromString(Meta meta, StaticObject value) {
        return meta.toHostBoxed(value);
    }

    @Fallback
    protected Object fallback(@SuppressWarnings("unused") Meta meta, Object value) {
        return value;
    }

    private Object convertStaticObjectArray(Meta meta, StaticObject[] array) {
        Object[] res = new Object[array.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = executeConvert(meta, array[i]);
        }
        return res;
    }

}
