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
import java.nio.ByteBuffer;
import java.security.PrivilegedActionException;
import java.util.Arrays;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.substitutions.Host;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta implements ContextAccess {

    private final EspressoContext context;

    public Meta(EspressoContext context) {
        this.context = context;

        // Core types.
        Object = knownKlass(Object.class);
        String = knownKlass(String.class);
        Class = knownKlass(Class.class);

        // Primitives.
        _boolean = knownPrimitive(boolean.class);
        _byte = knownPrimitive(byte.class);
        _char = knownPrimitive(char.class);
        _short = knownPrimitive(short.class);
        _float = knownPrimitive(float.class);
        _int = knownPrimitive(int.class);
        _double = knownPrimitive(double.class);
        _long = knownPrimitive(long.class);
        _void = knownPrimitive(void.class);

        // Boxed types.
        Boolean = knownKlass(Boolean.class);
        Byte = knownKlass(Byte.class);
        Char = knownKlass(Character.class);
        Short = knownKlass(Short.class);
        Float = knownKlass(Float.class);
        Int = knownKlass(Integer.class);
        Double = knownKlass(Double.class);
        Long = knownKlass(Long.class);
        Void = knownKlass(Void.class);

        Boolean_valueOf = Boolean.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Boolean.class, boolean.class));
        Byte_valueOf = Byte.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Byte.class, byte.class));
        Char_valueOf = Char.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Character.class, char.class));
        Short_valueOf = Short.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Short.class, short.class));
        Float_valueOf = Float.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Float.class, float.class));
        Int_valueOf = Int.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Integer.class, int.class));
        Double_valueOf = Double.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Double.class, double.class));
        Long_valueOf = Long.lookupDeclaredMethod(Name.valueOf, context.getSignatures().makeRaw(Long.class, long.class));

        String_value = String.lookupDeclaredField(Name.value, Types.fromClass(byte[].class));
        String_hash = String.lookupDeclaredField(Name.hash, Types.fromClass(int.class));
        String_hashCode = String.lookupDeclaredMethod(Name.hashCode, context.getSignatures().makeRaw(int.class));
        String_length = String.lookupDeclaredMethod(Name.length, context.getSignatures().makeRaw(int.class));

        Throwable = knownKlass(Throwable.class);
        Throwable_backtrace = Throwable.lookupField(Name.backtrace, Object.getType());

        StackOverflowError = knownKlass(StackOverflowError.class);
        OutOfMemoryError = knownKlass(OutOfMemoryError.class);

        PrivilegedActionException = knownKlass(PrivilegedActionException.class);
        PrivilegedActionException_init_Exception = PrivilegedActionException.lookupDeclaredMethod(Name.INIT, context.getSignatures().makeRaw(void.class, Exception.class));

        Cloneable = knownKlass(Cloneable.class);
        Serializable = knownKlass(Serializable.class);

        ClassLoader = knownKlass(Throwable.class);
        ClassLoader_findNative = ClassLoader.lookupDeclaredMethod(Name.findNative, context.getSignatures().makeRaw(long.class, ClassLoader.class, String.class));
        ClassLoader_getSystemClassLoader = ClassLoader.lookupDeclaredMethod(Name.getSystemClassLoader, context.getSignatures().makeRaw(ClassLoader.class));

        // Guest reflection.
        Constructor = knownKlass(Constructor.class);
        Constructor_clazz = Constructor.lookupDeclaredField(Name.clazz, Class.getType());
        Constructor_root = Constructor.lookupDeclaredField(Name.root, Constructor.getType());

        Method = knownKlass(java.lang.reflect.Method.class);
        Method_root = Method.lookupDeclaredField(Name.root, Method.getType());

        ByteBuffer = knownKlass(ByteBuffer.class);
        ByteBuffer_wrap = ByteBuffer.lookupDeclaredMethod(Name.wrap, context.getSignatures().makeRaw(ByteBuffer.class, byte[].class));

        Thread = knownKlass(Thread.class);
        ThreadGroup = knownKlass(ThreadGroup.class);

        Thread_group = Thread.lookupDeclaredField(Name.group, ThreadGroup.getType());
        Thread_name = Thread.lookupDeclaredField(Name.name, String.getType());
        Thread_priority = Thread.lookupDeclaredField(Name.priority, _int.getType());
        Thread_blockerLock = Thread.lookupDeclaredField(Name.blockerLock, Object.getType());

        System = knownKlass(System.class);
        System_initializeSystemClass = System.lookupDeclaredMethod(Name.initializeSystemClass, context.getSignatures().makeRaw(void.class));

        ARRAY_SUPERINTERFACES = new ObjectKlass[]{Cloneable, Serializable};
    }

    public final ObjectKlass Object;
    public final ObjectKlass String;
    public final ObjectKlass Class;

    // Primitives.
    public final PrimitiveKlass _boolean;
    public final PrimitiveKlass _byte;
    public final PrimitiveKlass _char;
    public final PrimitiveKlass _short;
    public final PrimitiveKlass _float;
    public final PrimitiveKlass _int;
    public final PrimitiveKlass _double;
    public final PrimitiveKlass _long;
    public final PrimitiveKlass _void;

    // Boxed primitives.
    public final ObjectKlass Boolean;
    public final ObjectKlass Byte;
    public final ObjectKlass Char;
    public final ObjectKlass Short;
    public final ObjectKlass Float;
    public final ObjectKlass Int;
    public final ObjectKlass Double;
    public final ObjectKlass Long;
    public final ObjectKlass Void;

    // Boxing conversions.
    public final Method Boolean_valueOf;
    public final Method Byte_valueOf;
    public final Method Char_valueOf;
    public final Method Short_valueOf;
    public final Method Float_valueOf;
    public final Method Int_valueOf;
    public final Method Double_valueOf;
    public final Method Long_valueOf;

    // Guest String.
    public final Field String_value;
    public final Field String_hash;
    public final Method String_hashCode;
    public final Method String_length;

    public final ObjectKlass ClassLoader;
    public final Method ClassLoader_findNative;
    public final Method ClassLoader_getSystemClassLoader;

    public final ObjectKlass Constructor;
    public final Field Constructor_clazz;
    public final Field Constructor_root;

    public final ObjectKlass Method;
    public final Field Method_root;

    public final ObjectKlass StackOverflowError;
    public final ObjectKlass OutOfMemoryError;
    public final ObjectKlass Throwable;
    public final Field Throwable_backtrace;

    public final ObjectKlass PrivilegedActionException;
    public final Method PrivilegedActionException_init_Exception;

    // Array support.
    public final ObjectKlass Cloneable;
    public final ObjectKlass Serializable;

    public final ObjectKlass ByteBuffer;
    public final Method ByteBuffer_wrap;

    public final ObjectKlass ThreadGroup;
    public final ObjectKlass Thread;
    public final Field Thread_group;
    public final Field Thread_name;
    public final Field Thread_priority;
    public final Field Thread_blockerLock;

    public final ObjectKlass System;
    public final Method System_initializeSystemClass;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] ARRAY_SUPERINTERFACES;

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: (host) known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public StaticObject initEx(java.lang.Class<?> clazz) {
        assert clazz.isAssignableFrom(Throwable.class);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = getInterpreterToVM().newObject(exKlass);
        exKlass.lookupDeclaredMethod(Name.INIT, getSignatures().makeRaw(void.class)).invokeDirect(ex);
        return ex;
    }

    public static StaticObject initEx(Klass klass, String message) {
        StaticObject ex = klass.allocateInstance();
        // Call constructor.
        klass.lookupDeclaredMethod(Name.INIT, klass.getSignatures().makeRaw(void.class, String.class)).invokeDirect(ex, message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, String message) {
        assert clazz.isAssignableFrom(Throwable.class);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = getInterpreterToVM().newObject(exKlass);
        exKlass.lookupDeclaredMethod(Name.INIT, exKlass.getSignatures().makeRaw(void.class, String.class)).invokeDirect(ex, message);
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        assert clazz.isAssignableFrom(Throwable.class);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = getInterpreterToVM().newObject(exKlass);
        exKlass.lookupDeclaredMethod(Name.INIT, exKlass.getSignatures().makeRaw(void.class, Throwable.class)).invokeDirect(ex, cause);
        return ex;
    }

    public EspressoException throwEx(java.lang.Class<?> clazz) {
        throw new EspressoException(initEx(clazz));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, String message) {
        throw new EspressoException(initEx(clazz, message));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        throw new EspressoException(initEx(clazz, cause));
    }

    @TruffleBoundary
    public Klass throwableKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Throwable.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    public ObjectKlass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        // Resolve non-primitive classes using BCL.
        return (ObjectKlass) getRegistries().loadKlassWithBootClassLoader(Types.fromClass(hostClass));
    }

    public PrimitiveKlass knownPrimitive(java.lang.Class<?> primitiveClass) {
        // assert isKnownClass(hostClass);
        assert primitiveClass.isPrimitive();
        // Resolve primitive classes using BCL.
        return (PrimitiveKlass) getRegistries().loadKlassWithBootClassLoader(Types.fromClass(primitiveClass));
    }

    @TruffleBoundary
    public Klass loadKlass(ByteString<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        return context.getRegistries().loadKlass(type, classLoader);
    }

    @TruffleBoundary
    public static String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        Meta meta = str.getKlass().getMeta();
        char[] value = ((StaticObjectArray) meta.String_value.get(str)).unwrap();
        return HostJava.createString(value);
    }

    @TruffleBoundary
    public StaticObject toGuest(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        final char[] value = HostJava.getStringValue(hostString);
        final int hash = HostJava.getStringHash(hostString);
        StaticObject guestString = String.allocateInstance();
        String_value.set(guestString, StaticObjectArray.wrap(value));
        String_hash.set(guestString, hash);
        // String.hashCode must be equivalent for host and guest.
        assert hostString.hashCode() == (int) String_hashCode.invokeDirect(guestString);
        return guestString;
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
            if (guestObject.getKlass() == String) {
                return toHostString(guestObject);
            }
        }
        return object;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // region Low level host String access

    private static class HostJava {

        private final static java.lang.reflect.Field String_value;
        private final static java.lang.reflect.Field String_hash;
        private final static Constructor<String> String_init;

        static {
            try {
                String_value = String.class.getDeclaredField("value");
                String_value.setAccessible(true);
                String_hash = String.class.getDeclaredField("hash");
                String_hash.setAccessible(true);
                String_init = String.class.getDeclaredConstructor(char[].class, boolean.class);
                String_init.setAccessible(true);
            } catch (NoSuchMethodException | NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static char[] getStringValue(String s) {
            try {
                return (char[]) String_value.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static int getStringHash(String s) {
            try {
                return (int) String_hash.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static String createString(final char[] value) {
            try {
                return HostJava.String_init.newInstance(value, true);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    // endregion
}
