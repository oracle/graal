/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

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
