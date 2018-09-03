package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;

public final class StaticObjectClass extends StaticObjectImpl {
    private Klass mirror;

    public void setMirror(Klass mirror) {
        assert this.mirror == null;
        this.mirror = mirror;
    }

    public Klass getMirror() {
        assert this.mirror != null;
        return this.mirror;
    }

    public StaticObjectClass(Klass klass) {
        super(klass);
    }

    public StaticObjectClass(Klass klass, boolean isStatic) {
        super(klass, isStatic);
    }
}
