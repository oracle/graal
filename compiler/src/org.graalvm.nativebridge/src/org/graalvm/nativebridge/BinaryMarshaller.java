/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
     * Reads the object value from the {@code input} and returns the recreated object.
     */
    T read(BinaryInput input);

    /**
     * Writes the {@code object}'s value into the {@code output}.
     */
    void write(BinaryOutput output, T object);

    /**
     * Estimates a size in bytes needed to marshall given object. The returned value is used to
     * pre-allocate the {@link BinaryOutput}'s buffer.
     */
    default int inferSize(@SuppressWarnings("unused") T object) {
        return Long.BYTES;
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
