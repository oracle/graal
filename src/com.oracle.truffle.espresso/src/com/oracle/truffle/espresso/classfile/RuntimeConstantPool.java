package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class RuntimeConstantPool extends ConstantPool {
    private static volatile int fence;

    private final ConstantPool pool;
    private final StaticObject classLoader;

//    @CompilerDirectives.CompilationFinal(dimensions = 1) //
//    private final ResolvedConstant[] constants;

    public RuntimeConstantPool(ConstantPool pool, StaticObject classLoader) {
        this.pool = pool;
        // constants = copyResolvedConstant(pool); // utf8, int, floats..., others->null
        this.classLoader = classLoader;
    }
//
//    private ResolvedConstant[] copyResolvedConstant(ConstantPool pool) {
//        //
//    }

    @Override
    public int length() {
        return pool.length();
    }

    @Override
    public PoolConstant at(int index, String description) {
        return pool.at(index, description);
    }
//
//    public ResolvedConstant resolvedAt(int index, String description) {
//        ResolvedConstant c = constants[index];
//        if (c == null) {
//            CompilerDirectives.transferToInterpreterAndInvalidate();
//            synchronized (this) {
//                fence += 1;
//                c = constants[index];
//                if (c == null) {
//                    constants[index] = c = ((Resolvable) pool.at(index, description)).resolve(this, index);
//                }
//            }
//        }
//        return c;
//    }
//
//    @SuppressWarnings("unchecked")
//    public StaticObject resolvedStringAt(int index) {
//        ResolvedConstant resolved = resolvedAt(index, "");
//        return (StaticObject) resolved.value();
//    }

    public StaticObject getClassLoader() {
        return classLoader;
    }

    public Klass resolveType(ByteString<Type> type, int typeIndex) {
        // TODO(peterssen): Use typeIndex to check if the CP entry is already resolved.
        return EspressoLanguage.getCurrentContext().getMeta().loadKlass(type, classLoader);
    }
}
