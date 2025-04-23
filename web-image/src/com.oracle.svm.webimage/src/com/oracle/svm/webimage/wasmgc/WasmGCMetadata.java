/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.wasmgc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Holds some metadata required for the {@link org.graalvm.webimage.api.JS} annotation.
 */
public class WasmGCMetadata {
    /**
     * Holds all classes which are encoded in method metadata.
     * <p>
     * The classes are encoded using their index in the list. At runtime, the index can be recovered
     * from the encoding and the class looked up by index, no reverse lookup is necessary.
     */
    private static final List<Class<?>> classes = new ArrayList<>();

    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final Map<Class<?>, Integer> classToIndex = new HashMap<>();

    private static final int RADIX = 36;

    /**
     * @see #encodeClassIndex(int)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String registerClass(Class<?> clazz) {
        assert clazz != null;

        Integer possibleIndex = classToIndex.get(clazz);
        int index;
        if (possibleIndex == null) {
            index = classes.size();
            classes.add(clazz);
            classToIndex.put(clazz, index);
        } else {
            index = possibleIndex;
        }

        return encodeClassIndex(index);
    }

    public static Class<?> lookupClass(String encoding) {
        return classes.get(decodeClassIndex(encoding));
    }

    /**
     * Encodes the given index into some string that can be decoded at runtime using
     * {@link #decodeClassIndex(String)}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static String encodeClassIndex(int index) {
        return Integer.toUnsignedString(index, RADIX);
    }

    /**
     * Opposite of {@link #encodeClassIndex(int)}.
     */
    private static int decodeClassIndex(String encoding) {
        return Integer.parseUnsignedInt(encoding, RADIX);
    }
}
