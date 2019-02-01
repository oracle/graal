package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ParserKlass;
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


//    abstract EspressoContext getContext();
//    abstract <T> Resolvable<T> replace(int thisIndex, Resolvable<T> resolved);
//
////    default Klass resolveK(int index, ClassLoader cl) {
////        ClassConstant constant = (ClassConstant) at(index);
////        ByteString<Type> type = constant.getType(this);
////        if (TypeDescriptor.isArray(type)) {
////            ByteString<Type> elementalType = TypeDescriptor.getElementalType(type);
////            Klass elementalKlass = resolveK(index, cl);
////            int dims = TypeDescriptor.getArrayDimensions(type);
////            Klass k = elementalKlass;
////            for (int i = 0; i < dims; ++i) {
////                k = k.getArrayClass();
////            }
////            return k;
////        } else {
////
////        }
////
////        return null;
////    }
}
