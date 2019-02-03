package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
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

    public Klass resolveType(ByteString<Type> type, int typeIndex) {
        // TODO(peterssen): Use typeIndex to check if the CP entry is already resolved.
        return EspressoLanguage.getCurrentContext().getMeta().loadKlass(type, classLoader);
    }
}
