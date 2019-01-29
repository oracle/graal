package com.oracle.truffle.espresso.impl;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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
