package com.oracle.truffle.espresso.impl;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.jni.Utf8;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

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

    public static final ByteString[] EMPTY_ARRAY = new ByteString[0];

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
    }

    public static class Constant extends ModifiedUTF8 {
    }

    public static class Descriptor extends ModifiedUTF8 {
    }

    public static class Type extends Descriptor {
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

    public static <T> ByteString<T> fromJavaString(String string) {
        return Utf8.fromJavaString(string);
    }

}

final class K {
    private K() {
        /* no instances */
    }

    public static ByteString<Type> Class = ByteString.fromJavaString("Ljava/lang/Class;");
    public static ByteString<Type> Object = ByteString.fromJavaString("Ljava/lang/Object;");
    public static ByteString<Type> String = ByteString.fromJavaString("Ljava/lang/String;");
    public static ByteString<Type> Exception = ByteString.fromJavaString("Ljava/lang/Exception;");
    public static ByteString<Type> Throwable = ByteString.fromJavaString("Ljava/lang/Throwable;");
    public static ByteString<Type> ClassLoader = ByteString.fromJavaString("Ljava/lang/ClassLoader;");
    public static ByteString<Type> System = ByteString.fromJavaString("Ljava/lang/System;");

    // Primitives
    public static ByteString<Type> _boolean = JavaKind.Boolean.getType();
    public static ByteString<Type> _byte = JavaKind.Byte.getType();
    public static ByteString<Type> _char = JavaKind.Char.getType();
    public static ByteString<Type> _short = JavaKind.Short.getType();
    public static ByteString<Type> _int = JavaKind.Int.getType();
    public static ByteString<Type> _float = JavaKind.Float.getType();
    public static ByteString<Type> _double = JavaKind.Double.getType();
    public static ByteString<Type> _long = JavaKind.Long.getType();
    public static ByteString<Type> _void = JavaKind.Void.getType();
}
