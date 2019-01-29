/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.meta;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.substitutions.Type;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta {

    private final EspressoContext context;

    public Meta(EspressoContext context) {
        this.context = context;
        OBJECT = knownKlass(Object.class);
        STRING = knownKlass(String.class);
        CLASS = knownKlass(java.lang.Class.class);
        BOOLEAN = knownKlass(boolean.class);
        BYTE = knownKlass(byte.class);
        CHAR = knownKlass(char.class);
        SHORT = knownKlass(short.class);
        FLOAT = knownKlass(float.class);
        INT = knownKlass(int.class);
        DOUBLE = knownKlass(double.class);
        LONG = knownKlass(long.class);
        VOID = knownKlass(void.class);

        BOXED_BOOLEAN = knownKlass(Boolean.class);
        BOXED_BYTE = knownKlass(Byte.class);
        BOXED_CHAR = knownKlass(Character.class);
        BOXED_SHORT = knownKlass(Short.class);
        BOXED_FLOAT = knownKlass(Float.class);
        BOXED_INT = knownKlass(Integer.class);
        BOXED_DOUBLE = knownKlass(Double.class);
        BOXED_LONG = knownKlass(Long.class);
        BOXED_VOID = knownKlass(Void.class);

        THROWABLE = knownKlass(Throwable.class);
        STACK_OVERFLOW_ERROR = knownKlass(StackOverflowError.class);
        OUT_OF_MEMORY_ERROR = knownKlass(OutOfMemoryError.class);

        CLONEABLE = knownKlass(Cloneable.class);
        SERIALIZABLE = knownKlass(Serializable.class);
    }

    public final Klass OBJECT;
    public final Klass STRING;
    public final Klass CLASS;

    // Primitives
    public final Klass BOOLEAN;
    public final Klass BYTE;
    public final Klass CHAR;
    public final Klass SHORT;
    public final Klass FLOAT;
    public final Klass INT;
    public final Klass DOUBLE;
    public final Klass LONG;
    public final Klass VOID;

    // Boxed
    public final Klass BOXED_BOOLEAN;
    public final Klass BOXED_BYTE;
    public final Klass BOXED_CHAR;
    public final Klass BOXED_SHORT;
    public final Klass BOXED_FLOAT;
    public final Klass BOXED_INT;
    public final Klass BOXED_DOUBLE;
    public final Klass BOXED_LONG;
    public final Klass BOXED_VOID;

    public final Klass STACK_OVERFLOW_ERROR;
    public final Klass OUT_OF_MEMORY_ERROR;
    public final Klass THROWABLE;

    public final Klass CLONEABLE;
    public final Klass SERIALIZABLE;

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: (host) known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public StaticObject initEx(java.lang.Class<?> clazz) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class).invokeDirect();
        return ex;
    }

    public static StaticObject initEx(Klass clazz, String message) {
        StaticObject ex = clazz.allocateInstance();
        meta(ex).method("<init>", void.class, String.class).invoke(message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, String message) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class, String.class).invoke(message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, @Type(Throwable.class) StaticObject cause) {
        StaticObject ex = throwableKlass(clazz).allocateInstance();
        meta(ex).method("<init>", void.class, Throwable.class).invoke(cause);
        return ex;
    }

    public EspressoException throwEx(java.lang.Class<?> clazz) {
        throw new EspressoException(initEx(clazz));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, String message) {
        throw new EspressoException(initEx(clazz, message));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, @Type(Throwable.class) StaticObject cause) {
        throw new EspressoException(initEx(clazz, cause));
    }

    @TruffleBoundary
    public Klass throwableKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Throwable.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    @TruffleBoundary
    public Klass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        // Resolve classes using BCL.
        return context.getRegistries().resolveWithBootClassLoader(context.getTypeDescriptors().make(MetaUtil.toInternalName(hostClass.getName())));
    }

    @TruffleBoundary
    public Klass loadKlass(String className, StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        return context.getRegistries().resolve(context.getTypeDescriptors().make(MetaUtil.toInternalName(className)), classLoader));
    }

    @TruffleBoundary
    public static String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        char[] value = ((StaticObjectArray) meta(str).declaredField("value").get()).unwrap();
        return createString(value);
    }

    @TruffleBoundary
    public StaticObject toGuest(String str) {
        return toGuest(this, str);
    }

    @TruffleBoundary
    public static StaticObject toGuest(Meta meta, String str) {
        if (str == null) {
            return StaticObject.NULL;
        }

        final char[] value = getStringValue(str);
        final int hash = getStringHash(str);

        StaticObject result = meta.STRING.metaNew().fields(
                        Field.set("value", StaticObjectArray.wrap(value)),
                        Field.set("hash", hash)).getInstance();

        // String.hashCode must be equivalent for host and guest.
        assert str.hashCode() == (int) meta(result).method("hashCode", int.class).invokeDirect();

        return result;
    }

    private static String createString(char[] value) {
        try {
            return STRING_CONSTRUCTOR.newInstance(value, true);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public Object toGuestBoxed(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuest((String) hostObject);
        }
        if (hostObject instanceof StaticObject || (hostObject.getClass().isArray() && hostObject.getClass().getComponentType().isPrimitive())) {
            return hostObject;
        }

        if (Arrays.stream(JavaKind.values()).anyMatch(new Predicate<JavaKind>() {
            @Override
            public boolean test(JavaKind c) {
                return c.toBoxedJavaClass() == hostObject.getClass();
            }
        })) {
            // boxed value
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toGuest(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuest((String) hostObject);
        }
        if (hostObject instanceof StaticObject || (hostObject.getClass().isArray() && hostObject.getClass().getComponentType().isPrimitive())) {
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toHostBoxed(Object object) {
        assert object != null;
        if (object instanceof StaticObject) {
            StaticObject guestObject = (StaticObject) object;
            if (StaticObject.isNull(guestObject)) {
                return null;
            }
            if (guestObject == StaticObject.VOID) {
                return null;
            }
            if (guestObject instanceof StaticObjectArray) {
                return ((StaticObjectArray) guestObject).unwrap();
            }
            if (guestObject.getKlass() == STRING) {
                return toHostString(guestObject);
            }
        }
        return object;
    }
    // region Low level host String access

    private static java.lang.reflect.Field STRING_VALUE;
    private static java.lang.reflect.Field STRING_HASH;
    private static Constructor<String> STRING_CONSTRUCTOR;

    static {
        try {
            STRING_VALUE = String.class.getDeclaredField("value");
            STRING_VALUE.setAccessible(true);
            STRING_HASH = String.class.getDeclaredField("hash");
            STRING_HASH.setAccessible(true);
            STRING_CONSTRUCTOR = String.class.getDeclaredConstructor(char[].class, boolean.class);
            STRING_CONSTRUCTOR.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static char[] getStringValue(String s) {
        try {
            return (char[]) STRING_VALUE.get(s);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static int getStringHash(String s) {
        try {
            return (int) STRING_HASH.get(s);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // endregion
}
