/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.memory.ByteArraySupport;

/**
 * Accessor class used by generated code for low-level access to frames, bytecode arrays, and
 * other bytecode classes.
 * <p>
 * Do not use directly.
 *
 * @since 24.2
 */
public abstract sealed class BytecodeDSLAccess permits BytecodeDSLCheckedAccess, BytecodeDSLUncheckedAccess {

    private static volatile BytecodeDSLAccess safeSingleton;
    private static volatile BytecodeDSLAccess unsafeSingleton;

    /**
     * Obtains an accessor. Used by generated code; do not use directly.
     *
     * @since 24.2
     */
    public static final BytecodeDSLAccess lookup(Object token, boolean allowUnsafe) {
        BytecodeRootNodes.checkToken(token);
        BytecodeDSLAccess impl;
        if (allowUnsafe && !Boolean.getBoolean("truffle.dsl.DisableUnsafeBytecodeDSLAccess")) {
            impl = unsafeSingleton;
            if (impl == null) {
                impl = unsafeSingleton = createUnsafe();
            }
        } else {
            impl = safeSingleton;
            if (impl == null) {
                impl = safeSingleton = createSafe();
            }
        }
        return impl;
    }

    BytecodeDSLAccess() {
    }

    private static BytecodeDSLAccess createSafe() {
        return new BytecodeDSLCheckedAccess();
    }

    private static BytecodeDSLAccess createUnsafe() {
        return new BytecodeDSLUncheckedAccess();
    }

    /**
     * Returns a {@link ByteArraySupport} to use for byte array accesses.
     *
     * @since 24.2
     */
    public abstract ByteArraySupport getByteArraySupport();

    /**
     * Returns a {@link FrameExtensions} to use for frame accesses.
     *
     * @since 24.2
     */
    public abstract FrameExtensions getFrameExtensions();

    /**
     * Reads from an int array.
     *
     * @since 24.2
     */
    public abstract int readInt(int[] arr, int index);

    /**
     * Writes to an int array.
     *
     * @since 24.2
     */
    public abstract void writeInt(int[] arr, int index, int value);

    /**
     * Reads from an Object array.
     *
     * @since 24.2
     */
    public abstract <T> T readObject(T[] arr, int index);

    /**
     * Writes to an Object array.
     *
     * @since 24.2
     */
    public abstract <T> void writeObject(T[] arr, int index, T value);

    /**
     * Casts a value to the given class. Also assumes non-null. If used in the form
     * uncheckedCast(this.field, ...) and the field is a final field, then the field will get
     * transformed into an immutable field read.
     *
     * @since 24.2
     */
    public abstract <T> T uncheckedCast(Object arr, Class<T> clazz);

    /**
     * Returns a key for deduplicating serialized constants. {@link Float} and {@link Double}
     * {@code NaN} values are replaced with wrappers that preserve NaN bit patterns.
     *
     * @since 25.6
     */
    @SuppressWarnings("static-method")
    public final Object getSerializationKey(Object object) {
        if (object instanceof Float f && f.isNaN()) {
            return new FloatNaNKey(Float.floatToRawIntBits(f));
        } else if (object instanceof Double d && d.isNaN()) {
            return new DoubleNaNKey(Double.doubleToRawLongBits(d));
        } else {
            return object;
        }
    }

    private record FloatNaNKey(int bits) {
    }

    private record DoubleNaNKey(long bits) {
    }

    /**
     * Returns the local offset of a local accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getLocalOffset(LocalAccessor accessor) {
        return accessor.localOffset;
    }

    /**
     * Returns the local index of a local accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getLocalIndex(LocalAccessor accessor) {
        return accessor.localIndex;
    }

    /**
     * Returns the starting local offset of a local range accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getStartOffset(LocalRangeAccessor accessor) {
        return accessor.startOffset;
    }

    /**
     * Returns the starting local index of a local range accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getStartIndex(LocalRangeAccessor accessor) {
        return accessor.startIndex;
    }

    /**
     * Returns the index of the root declaring the local of a materialized local accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getRootIndex(MaterializedLocalAccessor accessor) {
        return accessor.rootIndex;
    }

    /**
     * Returns the local offset of a materialized local accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getLocalOffset(MaterializedLocalAccessor accessor) {
        return accessor.localOffset;
    }

    /**
     * Returns the local index of a materialized local accessor.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final int getLocalIndex(MaterializedLocalAccessor accessor) {
        return accessor.localIndex;
    }

    /**
     * Obtains a {@link LocalAccessor} from its local offset and index.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final LocalAccessor getLocalAccessorConstant(int offset, int index) {
        return LocalAccessor.constantOf(offset, index);
    }

    /**
     * Obtains a {@link LocalRangeAccessor} from its starting local offset, index, and length.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final LocalRangeAccessor getLocalRangeAccessorConstant(int startOffset, int startIndex, int length) {
        return LocalRangeAccessor.constantOf(startOffset, startIndex, length);
    }

    /**
     * Obtains a {@link MaterializedLocalAccessor} from its root index, local offset, and local
     * index.
     *
     * @since 25.7
     */
    @SuppressWarnings("static-method")
    public final MaterializedLocalAccessor getMaterializedLocalAccessorConstant(int rootIndex, int offset, int index) {
        return MaterializedLocalAccessor.constantOf(rootIndex, offset, index);
    }

}
