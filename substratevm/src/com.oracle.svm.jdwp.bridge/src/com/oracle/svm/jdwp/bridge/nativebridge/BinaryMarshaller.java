/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.jdwp.bridge.nativebridge;

import java.util.Objects;

/**
 * A marshaller used by the native bridge processor to read or write method parameters and results
 * of a custom type. Marshallers are used to support types that are not directly implemented by the
 * native bridge processor.
 *
 * @see JNIConfig.Builder
 */
public interface BinaryMarshaller<T> {

    /**
     * Decomposes and serializes the given object passed to a foreign method.
     */
    void write(BinaryOutput output, T object);

    /**
     * Deserializes and recreates an object passed to a foreign method.
     */
    T read(BinaryInput input);

    /**
     * Estimates a size in bytes needed to marshall given object. The returned value is used to
     * pre-allocate the {@link BinaryOutput}'s buffer. The accuracy of the estimate affects the
     * speed of marshalling. If the estimate is too small, the pre-allocated buffer must be
     * re-allocated and the already marshalled data must be copied. Too large a value may cause the
     * static buffer to be unused and the dynamic buffer to be unnecessarily allocated.
     */
    default int inferSize(@SuppressWarnings("unused") T object) {
        return Long.BYTES;
    }

    /**
     * Decomposes and serializes the mutable state of a given object to support {@link Out}
     * semantics. Marshallers that do not support {@link Out} parameters do not need to implement
     * this method. The default implementation throws {@link UnsupportedOperationException}. To
     * support {@link Out} parameters the {@link BinaryMarshaller} must implement also
     * {@link #readUpdate(BinaryInput, Object)} and {@link #inferUpdateSize(Object)}.
     * <p>
     * The {@link Out} parameters are passed in the following way:
     * <ol>
     * <li>The start point method writes the parameter using
     * {@link #write(BinaryOutput, Object)}.</li>
     * <li>A foreign method call is made.</li>
     * <li>The end point method reads the parameter using {@link #read(BinaryInput)}.</li>
     * <li>The end point receiver method is called with the unmarshalled parameter.</li>
     * <li>After calling the receiver method, the end point method writes the mutated {@link Out}
     * parameter state using {@link #writeUpdate(BinaryOutput, Object)}.</li>
     * <li>A foreign method returns.</li>
     * <li>The state of the {@link Out} parameter is updated using
     * {@link #readUpdate(BinaryInput, Object)}.</li>
     * </ol>
     * <p>
     *
     * @see BinaryMarshaller#readUpdate(BinaryInput, Object)
     * @see BinaryMarshaller#inferUpdateSize(Object)
     */
    @SuppressWarnings("unused")
    default void writeUpdate(BinaryOutput output, T object) {
        throw new UnsupportedOperationException();
    }

    /**
     * Deserializes and updates the mutable state of a given object to support {@link Out}
     * semantics. Marshallers that do not support {@link Out} parameters do not need to implement
     * this method. The default implementation throws {@link UnsupportedOperationException}. To
     * support {@link Out} parameters the {@link BinaryMarshaller} must implement also
     * {@link #writeUpdate(BinaryOutput, Object)} and {@link #inferUpdateSize(Object)}.
     *
     * @see BinaryMarshaller#writeUpdate(BinaryOutput, Object)
     * @see BinaryMarshaller#inferUpdateSize(Object)
     */
    @SuppressWarnings("unused")
    default void readUpdate(BinaryInput input, T object) {
        throw new UnsupportedOperationException();
    }

    /**
     * Estimates a size in bytes needed to marshall {@link Out} parameter passed back to caller from
     * a foreign method call. The accuracy of the estimate affects the speed of marshalling. If the
     * estimate is too small, the pre-allocated buffer must be re-allocated and the already
     * marshalled data must be copied. Too large a value may cause the static buffer to be unused
     * and the dynamic buffer to be unnecessarily allocated. Marshallers that do not support
     * {@link Out} parameters do not need to implement this method. The default implementation
     * throws {@link UnsupportedOperationException}. To support {@link Out} parameters the
     * {@link BinaryMarshaller} must implement also {@link #writeUpdate(BinaryOutput, Object)} and
     * {@link #readUpdate(BinaryInput, Object)}.
     *
     * @see BinaryMarshaller#writeUpdate(BinaryOutput, Object)
     * @see BinaryMarshaller#readUpdate(BinaryInput, Object)
     */
    default int inferUpdateSize(@SuppressWarnings("unused") T object) {
        throw new UnsupportedOperationException();
    }

    /**
     * Decorates {@code forMarshaller} by a {@link BinaryMarshaller} handling {@code null} values.
     * The returned {@link BinaryMarshaller} calls the {@code forMarshaller} only non-null values.
     */
    static <T> BinaryMarshaller<T> nullable(BinaryMarshaller<T> forMarshaller) {
        Objects.requireNonNull(forMarshaller, "ForMarshaller must be non null.");
        return new NullableBinaryMarshaller<>(forMarshaller);
    }
}
