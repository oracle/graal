package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.runtime.StaticObject;

public final class RuntimeConstantPool extends ConstantPool {
    private final ConstantPool pool;
    private final StaticObject classLoader;

    public RuntimeConstantPool(ConstantPool pool, StaticObject classLoader) {
        this.pool = pool;
        this.classLoader = classLoader;
    }

    @Override
    public int length() {
        return pool.length();
    }

    @Override
    public PoolConstant at(int index, String description) {
        return pool.at(index, description);
    }

    public StaticObject getClassLoader() {
        return classLoader;
    }
}
