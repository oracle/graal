/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.jni.Utf8;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Modified-UTF8 byte string (symbol) for internal use in Espresso.
 *
 * An improvement over j.l.String for internal symbols:
 * <ul>
 * <li>Compact representation
 * <li>Cheap equality comparison
 * <li>Uniqueness and data de-duplication (symbols are unique during it's lifetime)
 * <li>Hard/clear separation between guest/host symbols
 * <li>0-cost tagging for added type-safety
 * <li>Lazy decoding
 * <li>Copy-less symbolification...
 * <li>Effectively partial-evaluation constant {@link CompilationFinal}
 * </ul>
 *
 * @param <T> generic tag for extra type-safety at the Java level.
 */
public final class Symbol<T> extends ByteSequence {

    @SuppressWarnings("rawtypes") public static final Symbol[] EMPTY_ARRAY = new Symbol[0];

    Symbol(byte[] bytes, int hashCode) {
        super(bytes, hashCode);
    }

    Symbol(byte[] value) {
        // hashCode must match ByteSequence.hashCodeOfRange.
        this(value, Arrays.hashCode(value));
    }

    @SuppressWarnings("unchecked")
    public static <S> Symbol<S>[] emptyArray() {
        return EMPTY_ARRAY;
    }

    public final ByteSequence substring(int from) {
        return substring(from, length());
    }

    public final ByteSequence substring(int from, int to) {
        assert 0 <= from && from <= to && to <= length();
        if (from == 0 && to == length()) {
            return this;
        }
        return subSequence(from, to - from);
    }

    public static void copyBytes(Symbol<?> src, int srcPos, byte[] dest, int destPos, int length) {
        System.arraycopy(src.value, srcPos, dest, destPos, length);
    }

    @Override
    public final byte byteAt(int index) {
        return value[index];
    }

    @Override
    public final String toString() {
        try {
            return Utf8.toJavaString(value);
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public final int length() {
        return value.length;
    }

    @Override
    public final int offset() {
        return 0;
    }

    private static class ModifiedUTF8 {
    }

    public static final class Constant extends ModifiedUTF8 {
    }

    public static class Descriptor extends ModifiedUTF8 {
    }

    public static final class Name extends ModifiedUTF8 {

        public static void init() {
            /* nop */
        }

        public static final Symbol<Name> INIT = StaticSymbols.putName("<init>");
        public static final Symbol<Name> CLINIT = StaticSymbols.putName("<clinit>");

        public static final Symbol<Name> backtrace = StaticSymbols.putName("backtrace");
        public static final Symbol<Name> clazz = StaticSymbols.putName("clazz");
        public static final Symbol<Name> root = StaticSymbols.putName("root");
        public static final Symbol<Name> value = StaticSymbols.putName("value");
        public static final Symbol<Name> hash = StaticSymbols.putName("hash");
        public static final Symbol<Name> hashCode = StaticSymbols.putName("hashCode");
        public static final Symbol<Name> length = StaticSymbols.putName("length");
        public static final Symbol<Name> findNative = StaticSymbols.putName("findNative");
        public static final Symbol<Name> getSystemClassLoader = StaticSymbols.putName("getSystemClassLoader");
        public static final Symbol<Name> valueOf = StaticSymbols.putName("valueOf");
        public static final Symbol<Name> wrap = StaticSymbols.putName("wrap");
        public static final Symbol<Name> initializeSystemClass = StaticSymbols.putName("initializeSystemClass");
        public static final Symbol<Name> group = StaticSymbols.putName("group");
        public static final Symbol<Name> name = StaticSymbols.putName("name");
        public static final Symbol<Name> priority = StaticSymbols.putName("priority");
        public static final Symbol<Name> blockerLock = StaticSymbols.putName("blockerLock");
        public static final Symbol<Name> constantPoolOop = StaticSymbols.putName("constantPoolOop");
        public static final Symbol<Name> main = StaticSymbols.putName("main");
        public static final Symbol<Name> checkAndLoadMain = StaticSymbols.putName("checkAndLoadMain");
        public static final Symbol<Name> forName = StaticSymbols.putName("forName");
        public static final Symbol<Name> run = StaticSymbols.putName("run");
        public static final Symbol<Name> loadClass = StaticSymbols.putName("loadClass");
        public static final Symbol<Name> addClass = StaticSymbols.putName("addClass");
        public static final Symbol<Name> getMessage = StaticSymbols.putName("getMessage");
        public static final Symbol<Name> getProperty = StaticSymbols.putName("getProperty");
        public static final Symbol<Name> setProperty = StaticSymbols.putName("setProperty");
        public static final Symbol<Name> exit = StaticSymbols.putName("exit");
        public static final Symbol<Name> override = StaticSymbols.putName("override");
        public static final Symbol<Name> parameterTypes = StaticSymbols.putName("parameterTypes");
        public static final Symbol<Name> shutdown = StaticSymbols.putName("shutdown");
        public static final Symbol<Name> clone = StaticSymbols.putName("clone");
        public static final Symbol<Name> printStackTrace = StaticSymbols.putName("printStackTrace");
        public static final Symbol<Name> maxPriority = StaticSymbols.putName("maxPriority");
        public static final Symbol<Name> daemon = StaticSymbols.putName("daemon");

        // Attribute names
        public static final Symbol<Name> Code = StaticSymbols.putName("Code");
        public static final Symbol<Name> EnclosingMethod = StaticSymbols.putName("EnclosingMethod");
        public static final Symbol<Name> Exceptions = StaticSymbols.putName("Exceptions");
        public static final Symbol<Name> InnerClasses = StaticSymbols.putName("InnerClasses");

        public static final Symbol<Name> BootstrapMethods = StaticSymbols.putName("BootstrapMethods");
        public static final Symbol<Name> ConstantValue = StaticSymbols.putName("ConstantValue");
        public static final Symbol<Name> RuntimeVisibleAnnotations = StaticSymbols.putName("RuntimeVisibleAnnotations");
        public static final Symbol<Name> RuntimeVisibleTypeAnnotations = StaticSymbols.putName("RuntimeVisibleTypeAnnotations");
        public static final Symbol<Name> RuntimeVisibleParameterAnnotations = StaticSymbols.putName("RuntimeVisibleParameterAnnotations");
        public static final Symbol<Name> AnnotationDefault = StaticSymbols.putName("AnnotationDefault");
        public static final Symbol<Name> MethodParameters = StaticSymbols.putName("MethodParameters");
        public static final Symbol<Name> Signature = StaticSymbols.putName("Signature");
    }

    public static final class Type extends Descriptor {

        public static void init() {
            /* nop */
        }

        // Core types.
        public static final Symbol<Type> String = StaticSymbols.putType(String.class);
        public static final Symbol<Type> String_array = StaticSymbols.putType(String[].class);

        public static final Symbol<Type> Object = StaticSymbols.putType(Object.class);
        public static final Symbol<Type> Object_array = StaticSymbols.putType(Object[].class);

        public static final Symbol<Type> Class = StaticSymbols.putType(Class.class);
        public static final Symbol<Type> Class_array = StaticSymbols.putType(Class[].class);

        public static final Symbol<Type> Throwable = StaticSymbols.putType(Throwable.class);
        public static final Symbol<Type> Exception = StaticSymbols.putType(Exception.class);
        public static final Symbol<Type> System = StaticSymbols.putType(System.class);
        public static final Symbol<Type> ClassLoader = StaticSymbols.putType(java.lang.ClassLoader.class);

        // Primitive types. Use JavaKind.getType()?
        public static final Symbol<Type> _boolean = StaticSymbols.putType(boolean.class);
        public static final Symbol<Type> _byte = StaticSymbols.putType(byte.class);
        public static final Symbol<Type> _char = StaticSymbols.putType(char.class);
        public static final Symbol<Type> _short = StaticSymbols.putType(short.class);
        public static final Symbol<Type> _int = StaticSymbols.putType(int.class);
        public static final Symbol<Type> _float = StaticSymbols.putType(float.class);
        public static final Symbol<Type> _double = StaticSymbols.putType(double.class);
        public static final Symbol<Type> _long = StaticSymbols.putType(long.class);
        public static final Symbol<Type> _void = StaticSymbols.putType(void.class);

        public static final Symbol<Type> _boolean_array = StaticSymbols.putType(boolean[].class);
        public static final Symbol<Type> _byte_array = StaticSymbols.putType(byte[].class);
        public static final Symbol<Type> _char_array = StaticSymbols.putType(char[].class);
        public static final Symbol<Type> _short_array = StaticSymbols.putType(short[].class);
        public static final Symbol<Type> _int_array = StaticSymbols.putType(int[].class);
        public static final Symbol<Type> _float_array = StaticSymbols.putType(float[].class);
        public static final Symbol<Type> _double_array = StaticSymbols.putType(double[].class);
        public static final Symbol<Type> _long_array = StaticSymbols.putType(long[].class);

        // Boxed types.
        public static final Symbol<Type> Boolean = StaticSymbols.putType(Boolean.class);
        public static final Symbol<Type> Byte = StaticSymbols.putType(Byte.class);
        public static final Symbol<Type> Character = StaticSymbols.putType(Character.class);
        public static final Symbol<Type> Short = StaticSymbols.putType(Short.class);
        public static final Symbol<Type> Integer = StaticSymbols.putType(Integer.class);
        public static final Symbol<Type> Float = StaticSymbols.putType(Float.class);
        public static final Symbol<Type> Double = StaticSymbols.putType(Double.class);
        public static final Symbol<Type> Long = StaticSymbols.putType(Long.class);
        public static final Symbol<Type> Void = StaticSymbols.putType(Void.class);

        public static final Symbol<Type> Cloneable = StaticSymbols.putType(Cloneable.class);

        public static final Symbol<Type> StackOverflowError = StaticSymbols.putType(StackOverflowError.class);
        public static final Symbol<Type> OutOfMemoryError = StaticSymbols.putType(OutOfMemoryError.class);
        public static final Symbol<Type> AssertionError = StaticSymbols.putType(AssertionError.class);

        public static final Symbol<Type> NullPointerException = StaticSymbols.putType(NullPointerException.class);
        public static final Symbol<Type> ClassCastException = StaticSymbols.putType(ClassCastException.class);
        public static final Symbol<Type> ArrayStoreException = StaticSymbols.putType(ArrayStoreException.class);
        public static final Symbol<Type> ArithmeticException = StaticSymbols.putType(ArithmeticException.class);
        public static final Symbol<Type> IllegalMonitorStateException = StaticSymbols.putType(IllegalMonitorStateException.class);
        public static final Symbol<Type> IllegalArgumentException = StaticSymbols.putType(IllegalArgumentException.class);
        public static final Symbol<Type> ClassNotFoundException = StaticSymbols.putType(ClassNotFoundException.class);
        public static final Symbol<Type> NegativeArraySizeException = StaticSymbols.putType(NegativeArraySizeException.class);
        public static final Symbol<Type> InvocationTargetException = StaticSymbols.putType(java.lang.reflect.InvocationTargetException.class);

        public static final Symbol<Type> Thread = StaticSymbols.putType(Thread.class);
        public static final Symbol<Type> ThreadGroup = StaticSymbols.putType(ThreadGroup.class);

        // Guest reflection.
        public static final Symbol<Type> Field = StaticSymbols.putType(java.lang.reflect.Field.class);
        public static final Symbol<Type> Method = StaticSymbols.putType(java.lang.reflect.Method.class);
        public static final Symbol<Type> Constructor = StaticSymbols.putType(java.lang.reflect.Constructor.class);
        public static final Symbol<Type> Parameter = StaticSymbols.putType(java.lang.reflect.Parameter.class);
        public static final Symbol<Type> Executable = StaticSymbols.putType(java.lang.reflect.Executable.class);

        // MagicAccessorImpl is not public.
        public static final Symbol<Type> MagicAccessorImpl = StaticSymbols.putType("Lsun/reflect/MagicAccessorImpl;");

        public static final Symbol<Type> Serializable = StaticSymbols.putType(java.io.Serializable.class);
        public static final Symbol<Type> ByteBuffer = StaticSymbols.putType(java.nio.ByteBuffer.class);
        public static final Symbol<Type> PrivilegedActionException = StaticSymbols.putType(java.security.PrivilegedActionException.class);

        // Shutdown is not public.
        public static final Symbol<Type> Shutdown = StaticSymbols.putType("Ljava/lang/Shutdown;");

        public static final Symbol<Type> sun_launcher_LauncherHelper = StaticSymbols.putType(sun.launcher.LauncherHelper.class);

        // Finalizer is not public.
        public static final Symbol<Type> java_lang_ref_Finalizer = StaticSymbols.putType("Ljava/lang/ref/Finalizer;");
        public static final Symbol<Type> StackTraceElement = StaticSymbols.putType(StackTraceElement.class);
        public static final Symbol<Type> NoSuchFieldError = StaticSymbols.putType(NoSuchFieldError.class);
        public static final Symbol<Type> NoSuchMethodError = StaticSymbols.putType(NoSuchMethodError.class);
        public static final Symbol<Type> IllegalAccessError = StaticSymbols.putType(IllegalAccessError.class);
        public static final Symbol<Type> IncompatibleClassChangeError = StaticSymbols.putType(IncompatibleClassChangeError.class);
        public static final Symbol<Type> AbstractMethodError = StaticSymbols.putType(AbstractMethodError.class);
    }

    public static final class Signature extends Descriptor {

        public static void init() {
            /* nop */
        }

        public static final Symbol<Signature> _int = StaticSymbols.putSignature(Type._int);
        public static final Symbol<Signature> _void = StaticSymbols.putSignature(Type._void);
        public static final Symbol<Signature> Object = StaticSymbols.putSignature(Type.Object);
        public static final Symbol<Signature> String = StaticSymbols.putSignature(Type.String);
        public static final Symbol<Signature> ClassLoader = StaticSymbols.putSignature(Type.ClassLoader);

        public static final Symbol<Signature> Class_String_boolean = StaticSymbols.putSignature(Type.Class, Type.String, Type._boolean);
        public static final Symbol<Signature> _void_Class = StaticSymbols.putSignature(Type._void, Type.Class);

        public static final Symbol<Signature> Object_String_String = StaticSymbols.putSignature(Type.Object, Type.String, Type.String);
        public static final Symbol<Signature> String_String = StaticSymbols.putSignature(Type.String, Type.String);
        public static final Symbol<Signature> _void_String_array = StaticSymbols.putSignature(Type._void, Type.String_array);
        public static final Symbol<Signature> Class_boolean_int_String = StaticSymbols.putSignature(Type.Class, Type._boolean, Type._int, Type.String);

        public static final Symbol<Signature> _void_Throwable = StaticSymbols.putSignature(Type._void, Type.Throwable);
        public static final Symbol<Signature> _void_String = StaticSymbols.putSignature(Type._void, Type.String);
        public static final Symbol<Signature> Class_String = StaticSymbols.putSignature(Type.Class, Type.String);
        public static final Symbol<Signature> ByteBuffer_byte_array = StaticSymbols.putSignature(Type.ByteBuffer, Type._byte_array);
        public static final Symbol<Signature> _long_ClassLoader_String = StaticSymbols.putSignature(Type._long, Type.ClassLoader, Type.String);
        public static final Symbol<Signature> _void_Exception = StaticSymbols.putSignature(Type._void, Type.Exception);
        public static final Symbol<Signature> _void_String_String_String_int = StaticSymbols.putSignature(Type._void, Type.String, Type.String, Type.String, Type._int);
        public static final Symbol<Signature> _void_int = StaticSymbols.putSignature(Type._void, Type._int);

        public static final Symbol<Signature> Boolean_boolean = StaticSymbols.putSignature(Type.Boolean, Type._boolean);
        public static final Symbol<Signature> Byte_byte = StaticSymbols.putSignature(Type.Byte, Type._byte);
        public static final Symbol<Signature> Character_char = StaticSymbols.putSignature(Type.Character, Type._char);
        public static final Symbol<Signature> Short_short = StaticSymbols.putSignature(Type.Short, Type._short);
        public static final Symbol<Signature> Float_float = StaticSymbols.putSignature(Type.Float, Type._float);
        public static final Symbol<Signature> Integer_int = StaticSymbols.putSignature(Type.Integer, Type._int);
        public static final Symbol<Signature> Double_double = StaticSymbols.putSignature(Type.Double, Type._double);
        public static final Symbol<Signature> Long_long = StaticSymbols.putSignature(Type.Long, Type._long);
    }
}
