/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.c.type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.CTypeConversionSupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/**
 * Utility methods to convert between Java types and C types.
 *
 * @since 19.0
 */
public final class CTypeConversion {

    private CTypeConversion() {
    }

    /**
     * An auto-closable that holds a Java {@link CharSequence} as a null-terminated C char[] array.
     * The C pointer is only valid as long as the auto-closeable has not been closed.
     *
     * @since 19.0
     */
    public interface CCharPointerHolder extends AutoCloseable {
        /**
         * Returns the C pointer to the null-terminated C char[] array.
         *
         * @since 19.0
         */
        CCharPointer get();

        /**
         * Discards the C pointer.
         *
         * @since 19.0
         */
        @Override
        void close();
    }

    /**
     * Provides access to a C pointer for the provided Java String, encoded with the default
     * charset.
     *
     * @since 19.0
     */
    public static CCharPointerHolder toCString(CharSequence javaString) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCString(javaString);
    }

    /**
     * Copies the {@code javaString} into the buffer as a 0 terminated C string encoded with the
     * default character set.
     * <p>
     * When a nullptr is passed as the buffer and the buffer size is zero, then no copying is
     * performed but the byte-sized length of the {@code javaString} is still returned.
     * <p>
     * In case the string is larger than the {@code buffer}, then an
     * {@code IllegalArgumentException} is thrown.
     *
     * @param javaString managed Java string
     * @param buffer to store the bytes of 0 terminated javaString encoded with the default charset
     *            or a nullptr
     * @param bufferSize size of the buffer or 0 when the buffer is a nullptr
     * @return the byte-sized length of the {@code javaString} when represented as a C string
     *         encoded with the default character set. This length does not include the 0
     *         terminator.
     *
     * @since 19.0
     */
    public static UnsignedWord toCString(CharSequence javaString, CCharPointer buffer, UnsignedWord bufferSize) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCString(javaString, buffer, bufferSize);
    }

    /**
     * Copies the {@code javaString} into the buffer as a 0 terminated C string encoded with the
     * {@code charset} character set.
     * <p>
     * When a nullptr is passed as the buffer and the buffer size is zero, then no copying is
     * performed but the byte-sized length of the {@code javaString} is still returned.
     * <p>
     * In case the string is larger than the {@code buffer}, then an
     * {@code IllegalArgumentException} is thrown.
     *
     * @param javaString managed Java string
     * @param charset desired character set for the returned string
     * @param buffer to store the bytes of 0 terminated javaString encoded with charset or a nullptr
     * @param bufferSize size of the buffer or 0 when the buffer is a nullptr
     * @return the byte-sized length of the {@code javaString} when represented as C string encoded
     *         with the {@code charset} character set. This length does not include the 0
     *         terminator.
     *
     * @since 19.0
     */
    public static UnsignedWord toCString(CharSequence javaString, Charset charset, CCharPointer buffer, UnsignedWord bufferSize) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCString(javaString, charset, buffer, bufferSize);
    }

    /**
     * Decodes a 0 terminated C {@code char*} to a Java string using the platform's default charset.
     *
     * @param cString the pointer to a 0 terminated C string
     * @return a Java string
     *
     * @since 19.0
     */
    public static String toJavaString(CCharPointer cString) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toJavaString(cString);
    }

    /**
     * Decodes a C {@code char*} of length {@code length} to a Java string using the platform's
     * default charset.
     *
     * @param cString the pointer to a 0 terminated C string
     * @return a Java string
     *
     * @since 19.0
     */
    public static String toJavaString(CCharPointer cString, UnsignedWord length) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toJavaString(cString, length);
    }

    /**
     * Decodes a C {@code char*} of length {@code length} to a Java string using {@code charset}.
     *
     * @param cString the pointer to a 0 terminated C string
     * @return a Java string
     *
     * @since 19.0
     */
    public static String toJavaString(CCharPointer cString, UnsignedWord length, Charset charset) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toJavaString(cString, length, charset);
    }

    /**
     * Decodes a UTF-8 encoded, 0 terminated C {@code char*} to a Java string.
     *
     * @param utf8String the pointer to a UTF-8 encoded, 0 terminated C string
     * @return a Java string
     *
     * @since 22.3
     */
    public static String utf8ToJavaString(CCharPointer utf8String) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).utf8ToJavaString(utf8String);
    }

    /**
     * Converts a Java boolean into a C int containing boolean values.
     *
     * @param value the Java boolean value
     * @return the C boolean value
     *
     * @since 19.0
     */
    public static byte toCBoolean(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    /**
     * Converts a C int containing boolean values into a Java boolean.
     *
     * @since 19.0
     */
    public static boolean toBoolean(int value) {
        return value != 0;
    }

    /**
     * Converts a C pointer into a Java boolean.
     *
     * @since 19.0
     */
    public static boolean toBoolean(PointerBase pointer) {
        return pointer.isNonNull();
    }

    /**
     * An auto-closable that holds a Java {@link CharSequence}[] array as a null-terminated array of
     * null-terminated C char[]s. The C pointers are only valid as long as the auto-closeable has
     * not been closed.
     *
     * @since 19.0
     */
    public abstract static class CCharPointerPointerHolder implements AutoCloseable {

        /**
         * @since 19.0
         */
        public CCharPointerPointerHolder() {
        }

        /**
         * Returns the C pointer to pointers of null-terminated C char[] arrays.
         *
         * @since 19.0
         */
        public abstract CCharPointerPointer get();

        /**
         * Discards the C pointer.
         *
         * @since 19.0
         */
        @Override
        public abstract void close();
    }

    /**
     * Provides access to C pointers for the provided Java Strings, encoded with the default
     * charset.
     *
     * @since 19.0
     */
    public static CCharPointerPointerHolder toCStrings(CharSequence[] javaStrings) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCStrings(javaStrings);
    }

    /**
     * Creates a {@link ByteBuffer} that refers to the native memory at the specified address. The
     * passed size becomes the {@linkplain ByteBuffer#capacity capacity} of the byte buffer, and the
     * buffer's {@linkplain ByteBuffer#order() byte order} is set to
     * {@linkplain ByteOrder#nativeOrder() native byte order}. The caller is responsible for
     * ensuring that the memory can be safely accessed while the ByteBuffer is used, and for freeing
     * the memory afterwards.
     *
     * @since 19.0
     */
    public static ByteBuffer asByteBuffer(PointerBase address, int size) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).asByteBuffer(address, size);
    }

    /**
     * Provides access to a C pointer for the provided Java byte array.
     *
     * @since 22.2
     */
    public static CCharPointerHolder toCBytes(byte[] bytes) {
        return ImageSingletons.lookup(CTypeConversionSupport.class).toCBytes(bytes);
    }
}
