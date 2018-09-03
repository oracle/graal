package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;

public class StaticObjectWrapper<T> extends StaticObject {
    private final T wrapped;
    public StaticObjectWrapper(Klass klass, T wrapped) {
        super(klass);
        assert wrapped != null;
        this.wrapped = wrapped;
    }
    public T getWrapped() {
        return wrapped;
    }
}
