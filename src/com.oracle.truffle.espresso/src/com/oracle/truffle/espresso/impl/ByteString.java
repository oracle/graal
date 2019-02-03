package com.oracle.truffle.espresso.impl;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.jni.Utf8;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Modified-UTF8 byte string for internal use by Espresso.
 *
 * An improvement over j.l.String for internal symbols:
 * <ul>
 * <li>Compact representation
 * <li>Hard/clear separation between guest/host symbols
 * <li>0-cost tagging for added type-safety
 * <li>Lazy decoding
 * <li>Effectively partial-evaluation constant {@link CompilationFinal}
 * </ul>
 *
 * @param <T> generic tag for extra type-safety at the Java level.
 */
public final class ByteString<T> {

    @SuppressWarnings("rawtypes") public static final ByteString[] EMPTY_ARRAY = new ByteString[0];

    @SuppressWarnings("unchecked")
    public static <S> ByteString<S>[] emptyArray() {
        return (ByteString<S>[]) EMPTY_ARRAY;
    }

    @Stable @CompilationFinal(dimensions = 1) //
    private final byte[] value;

    private int hash;

    public ByteString<T> substring(int from) {
        return substring(from, length());
    }

    public ByteString<T> substring(int from, int to) {
        return new ByteString<>(Arrays.copyOfRange(value, from, to));
    }

    public static void copyBytes(ByteString<?> src, int srcPos, byte[] dest, int destPos, int length) {
        System.arraycopy(src.value, srcPos, dest, destPos, length);
    }

    public static class ModifiedUTF8 {
    }

    public static interface Interned {
    }

    public static class Name extends ModifiedUTF8 {
        public static final ByteString<Name> INIT = ByteString.fromJavaString("<init>");
        public static final ByteString<Name> CLINIT = ByteString.fromJavaString("<clinit>");
        public static final ByteString<Name> backtrace = ByteString.fromJavaString("backtrace");
        public static final ByteString<Name> clazz = ByteString.fromJavaString("clazz");
        public static final ByteString<Name> root = ByteString.fromJavaString("root");
        public static final ByteString<Name> value = ByteString.fromJavaString("value");
        public static final ByteString<Name> hash = ByteString.fromJavaString("hash");
        public static final ByteString<Name> hashCode = ByteString.fromJavaString("hashCode");
        public static final ByteString<Name> length = ByteString.fromJavaString("length");
        public static final ByteString<Name> findNative = ByteString.fromJavaString("findNative");
        public static final ByteString<Name> getSystemClassLoader = ByteString.fromJavaString("getSystemClassLoader");
        public static final ByteString<Name> valueOf = ByteString.fromJavaString("valueOf");
        public static final ByteString<Name> wrap = ByteString.fromJavaString("wrap");
        public static final ByteString<Name> initializeSystemClass = ByteString.fromJavaString("initializeSystemClass");

        public static final ByteString<Name> group = ByteString.fromJavaString("group");
        public static final ByteString<Name> name = ByteString.fromJavaString("name");
        public static final ByteString<Name> priority = ByteString.fromJavaString("priority");
        public static final ByteString<Name> blockerLock = ByteString.fromJavaString("blockerLock");
        public static final ByteString<Name> constantPoolOop = ByteString.fromJavaString("constantPoolOop");
        public static final ByteString<Name> main = ByteString.fromJavaString("main");
        public static final ByteString<Name> checkAndLoadMain = ByteString.fromJavaString("checkAndLoadMain");
        public static final ByteString<Name> forName = ByteString.fromJavaString("forName");

        // Attribute names
        public static final ByteString<Name> Code = ByteString.fromJavaString("Code");
        public static final ByteString<Name> EnclosingMethod = ByteString.fromJavaString("EnclosingMethod");
        public static final ByteString<Name> Exceptions = ByteString.fromJavaString("Exceptions");
        public static final ByteString<Name> InnerClasses = ByteString.fromJavaString("InnerClasses");
        public static final ByteString<Name> loadClass = ByteString.fromJavaString("loadClass");
        public static final ByteString<Name> addClass = ByteString.fromJavaString("addClass");
        public static final ByteString<Name> RuntimeVisibleAnnotations = ByteString.fromJavaString("RuntimeVisibleAnnotations");
        public static final ByteString<Name> run = ByteString.fromJavaString("run");
        public static final ByteString<Name> getMessage = ByteString.fromJavaString("getMessage");
        public static final ByteString<Name> getProperty = ByteString.fromJavaString("getProperty");
    }

    public static class Symbol extends ModifiedUTF8 {
    }

    public static class Descriptor extends ModifiedUTF8 {
    }

    public static class Type extends Descriptor {
        // Core types.
        public static final ByteString<Type> String = Types.fromClass(String.class);
        public static final ByteString<Type> String_array = Types.fromClass(String[].class);

        public static final ByteString<Type> Object = Types.fromClass(Object.class);
        public static final ByteString<Type> Object_array = Types.fromClass(Object[].class);

        public static final ByteString<Type> Class = Types.fromClass(Class.class);
        public static final ByteString<Type> Class_array = Types.fromClass(Class[].class);

        public static final ByteString<Type> Throwable = Types.fromClass(Throwable.class);
        public static final ByteString<Type> Exception = Types.fromClass(Exception.class);
        public static final ByteString<Type> System = Types.fromClass(System.class);
        public static final ByteString<Type> ClassLoader = Types.fromClass(ClassLoader.class);

        // Primitive types. Use JavaKind.getType()?
        public static final ByteString<Type> _boolean = Types.fromClass(boolean.class);
        public static final ByteString<Type> _byte = Types.fromClass(byte.class);
        public static final ByteString<Type> _char = Types.fromClass(char.class);
        public static final ByteString<Type> _short = Types.fromClass(short.class);
        public static final ByteString<Type> _int = Types.fromClass(int.class);
        public static final ByteString<Type> _float = Types.fromClass(float.class);
        public static final ByteString<Type> _double = Types.fromClass(double.class);
        public static final ByteString<Type> _long = Types.fromClass(long.class);
        public static final ByteString<Type> _void = Types.fromClass(void.class);

        public static final ByteString<Type> _boolean_array = Types.fromClass(boolean[].class);
        public static final ByteString<Type> _byte_array = Types.fromClass(byte[].class);
        public static final ByteString<Type> _char_array = Types.fromClass(char[].class);
        public static final ByteString<Type> _short_array = Types.fromClass(short[].class);
        public static final ByteString<Type> _int_array = Types.fromClass(int[].class);
        public static final ByteString<Type> _float_array = Types.fromClass(float[].class);
        public static final ByteString<Type> _double_array = Types.fromClass(double[].class);
        public static final ByteString<Type> _long_array = Types.fromClass(long[].class);

        // Boxed types.
        public static final ByteString<Type> Boolean = Types.fromClass(Boolean.class);
        public static final ByteString<Type> Byte = Types.fromClass(Byte.class);
        public static final ByteString<Type> Character = Types.fromClass(Character.class);
        public static final ByteString<Type> Short = Types.fromClass(Short.class);
        public static final ByteString<Type> Integer = Types.fromClass(Integer.class);
        public static final ByteString<Type> Float = Types.fromClass(Float.class);
        public static final ByteString<Type> Double = Types.fromClass(Double.class);
        public static final ByteString<Type> Long = Types.fromClass(Long.class);
        public static final ByteString<Type> Void = Types.fromClass(Void.class);

        public static final ByteString<Type> Cloneable = Types.fromClass(Cloneable.class);

        public static final ByteString<Type> StackOverflowError = Types.fromClass(StackOverflowError.class);
        public static final ByteString<Type> OutOfMemoryError = Types.fromClass(OutOfMemoryError.class);

        public static final ByteString<Type> NullPointerException = Types.fromClass(NullPointerException.class);
        public static final ByteString<Type> ClassCastException = Types.fromClass(ClassCastException.class);
        public static final ByteString<Type> ArrayStoreException = Types.fromClass(ArrayStoreException.class);
        public static final ByteString<Type> ArithmeticException = Types.fromClass(ArithmeticException.class);
        public static final ByteString<Type> IllegalMonitorStateException = Types.fromClass(IllegalMonitorStateException.class);
        public static final ByteString<Type> IllegalArgumentException = Types.fromClass(IllegalArgumentException.class);
        public static final ByteString<Type> ClassNotFoundException = Types.fromClass(ClassNotFoundException.class);

        public static final ByteString<Type> Thread = Types.fromClass(Thread.class);
        public static final ByteString<Type> ThreadGroup = Types.fromClass(ThreadGroup.class);

        public static final ByteString<Type> Field = Types.fromClass(java.lang.reflect.Field.class);
        public static final ByteString<Type> Method = Types.fromClass(java.lang.reflect.Method.class);
        public static final ByteString<Type> Constructor = Types.fromClass(java.lang.reflect.Constructor.class);

        public static final ByteString<Type> Serializable = Types.fromClass(java.io.Serializable.class);
        public static final ByteString<Type> ByteBuffer = Types.fromClass(java.nio.ByteBuffer.class);
        public static final ByteString<Type> PrivilegedActionException = Types.fromClass(java.security.PrivilegedActionException.class);

        public static final ByteString<Type> sun_launcher_LauncherHelper = Types.fromClass(sun.launcher.LauncherHelper.class);

        // Finalizer is not public.
        public static final ByteString<Type> java_lang_ref_Finalizer = Types.fromJavaString("Ljava/lang/ref/Finalizer;");
        public static final ByteString<Type> StackTraceElement = Types.fromClass(StackTraceElement.class);
    }

    public static class Signature extends Descriptor {
    }

    public byte byteAt(int index) {
        return value[index];
    }

    public ByteString(byte[] value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            h = Arrays.hashCode(value);
            hash = h;
        }
        return h;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ByteString) {
            ByteString other = (ByteString) obj;
            return Arrays.equals(value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        try {
            return Utf8.toJavaString(value);
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public int length() {
        return value.length;
    }

    public static <T> ByteString<T> singleASCII(char ch) {
        if (ch > 127) {
            throw new IllegalArgumentException("non-ASCII char");
        }
        return new ByteString<>(new byte[]{(byte) ch});
    }

    @Deprecated
    public static <T> ByteString<T> fromJavaString(String string) {
        return Utf8.fromJavaString(string);
    }

}
