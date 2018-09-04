package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;

import static com.oracle.truffle.espresso.meta.Meta.meta;

public class StaticObject {
    private final Klass klass;

    // Context-less objects.
    public static StaticObject NULL = new StaticObject(null);
    public static StaticObject VOID = new StaticObject(null);

    public Klass getKlass() {
        return klass;
    }

    protected StaticObject(Klass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        if (this == NULL) {
            return "null";
        }
        if (this == VOID) {
            return "void";
        }
        return meta(this).guestToString();
        // return klass.getTypeDescriptor().toJavaName();
    }
}
