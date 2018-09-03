package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;

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
        return klass.getTypeDescriptor().toJavaName();
    }
}
