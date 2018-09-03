package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;

public class StaticObjectArray extends StaticObjectWrapper<Object[]> {
    public StaticObjectArray(Klass componentType, Object[] arr) {
        super(componentType.getArrayClass(), arr);
    }
    public StaticObjectArray clone() {
        return new StaticObjectArray(getKlass().getComponentType(), getWrapped().clone());
    }
}
