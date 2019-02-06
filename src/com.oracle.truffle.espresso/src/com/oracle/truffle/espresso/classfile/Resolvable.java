package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.impl.Klass;

public interface Resolvable extends PoolConstant {

    ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass);

    interface ResolvedConstant extends PoolConstant {
        Object value();
    }
}
