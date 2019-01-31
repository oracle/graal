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

//class KlassLoader {
//
//        // Cached in the language.
//    ParserKlass resolveParserKlass(ByteString<Type> type) {
//        return null;
//    }
//
//    // Cached in the language.
//    LinkedKlass resolveLinkedKlass(ByteString<Type> type) {
//        ParserKlass parserKlass = resolveParserKlass(type);
//
//        ConstantPool pool = parserKlass.getConstantPool();
//
//        ByteString<Type> superKlass = ((ClassConstant) pool.at(parserKlass.getSuperKlassIndex(), "super")).getType(pool);
//        // TODO(peterssen): Assert is a class or (null) for j.l.Object .
//        LinkedKlass linkedSuperKlass = resolveLinkedKlass(superKlass);
//
//        LinkedKlass[] linkedInterfaces = new LinkedKlass[parserKlass.getSuperInterfaces().length];
//        for (int i = 0; i < linkedInterfaces.length; ++i) {
//            ByteString<Type> superInterface = ((ClassConstant) pool.at(parserKlass.getSuperInterfacesIndices()[i], "super interface")).getType(pool);
//            // TODO(peterssen): Assert is an interface.
//            linkedInterfaces[i] = resolveLinkedKlass(superInterface);
//        }
//
//        return null; // maybeCached (parserKlass, superKlass, linkedInterfaces) => new
//                     // LinkedKlass(parserKlass, superKlass, linkedInterfaces);
//    }
//
//    // Cached in the class loader.
//    Klass resolveKlass(ByteString<Type> type) {
//        if (Types.isArray(type)) {
//            ByteString<Type> elementalType = Types.getElementalType(type);
//            Klass elementalKlass = resolveKlass(elementalType);
//            int dims = Types.getArrayDimensions(type);
//            Klass k = elementalKlass;
//            for (int i = 0; i < dims; ++i) {
//                k = k.getArrayClass();
//            }
//            return k;
//        } else {
//            LinkedKlass linkedKlass = resolveLinkedKlass(type);
//            return null; // maybeCached(linkedKlass) => Klass(linkedKlass, new
//                         // RuntimeConstantPoolImpl(this));
//        }
//    }
//
//    Klass lookupType(ByteString<Type> unresolvedJavaType, boolean resolve) {
//        return null;
//    }
//}

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
