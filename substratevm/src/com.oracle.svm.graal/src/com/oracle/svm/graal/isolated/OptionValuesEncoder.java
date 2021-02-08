/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.util.TypedDataInputStream;
import org.graalvm.util.TypedDataOutputStream;
import org.graalvm.word.WordFactory;

final class OptionValuesEncoder {

    public static byte[] encode(UnmodifiableEconomicMap<OptionKey<?>, Object> options) {
        try (ByteArrayOutputStream baout = new ByteArrayOutputStream()) {
            try (TypedDataOutputStream out = new TypedDataOutputStream(baout)) {
                out.writeInt(options.size());
                UnmodifiableMapCursor<OptionKey<?>, Object> cursor = options.getEntries();
                while (cursor.advance()) {
                    ImageHeapRef<OptionKey<?>> keyRef = ImageHeapObjects.ref(cursor.getKey());
                    out.writeLong(keyRef.rawValue());
                    try {
                        out.writeTypedValue(cursor.getValue());
                    } catch (IllegalArgumentException iae) {
                        throw new IllegalArgumentException(String.format("Key: %s, Value: %s, Value type: %s",
                                        cursor.getKey().getName(), cursor.getValue(), cursor.getValue().getClass()), iae);
                    }
                }
            }
            return baout.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    public static EconomicMap<OptionKey<?>, Object> decode(byte[] input) {
        EconomicMap<OptionKey<?>, Object> options = EconomicMap.create();
        try (TypedDataInputStream in = new TypedDataInputStream(new ByteArrayInputStream(input))) {
            final int size = in.readInt();
            for (int i = 0; i < size; i++) {
                ImageHeapRef<OptionKey<?>> keyRef = WordFactory.signed(in.readLong());
                final OptionKey<?> key = ImageHeapObjects.deref(keyRef);
                final Object value = in.readTypedValue();
                options.put(key, value);
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException(in.available() + " undecoded bytes");
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
        return options;
    }

    private OptionValuesEncoder() {
    }
}
