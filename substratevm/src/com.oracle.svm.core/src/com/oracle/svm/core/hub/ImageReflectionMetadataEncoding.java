/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.reflect.RuntimeMetadataDecoder.NO_DATA;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.code.RuntimeMetadataEncoding;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.util.VMError;

/**
 * Compact encoding for non-layered image reflection metadata.
 * <p>
 * The value stored in {@link DynamicHubCompanion#encodedReflectionMetadata} is a tagged encoded
 * reference:
 * <ul>
 * <li>{@code 0}: no image reflection metadata is present.</li>
 * <li>high bit set: one metadata kind is stored inline. Bits 30..24 contain the metadata-kind mask
 * and bits 23..0 contain the non-negative value.</li>
 * <li>otherwise: the value is {@code offset + 1} into
 * {@link RuntimeMetadataEncoding#getReflectionMetadataEncoding()}.</li>
 * </ul>
 * A side-table entry starts with a one-byte metadata-kind mask. The values for all set bits then
 * follow as little-endian signed Java {@code int}s in ascending bit order: fields, methods,
 * constructors, record components, dynamic access, unsafe allocation, and class flags.
 */
final class ImageReflectionMetadataEncoding {
    private static final int FIELDS = 1;
    private static final int METHODS = 1 << 1;
    private static final int CONSTRUCTORS = 1 << 2;
    private static final int RECORD_COMPONENTS = 1 << 3;
    private static final int DYNAMIC_ACCESS = 1 << 4;
    private static final int UNSAFE_ALLOCATED = 1 << 5;
    private static final int CLASS_FLAGS = 1 << 6;

    /** Zero is the default value of {@link DynamicHubCompanion#encodedReflectionMetadata}. */
    private static final int NO_REFLECTION_METADATA = 0;
    private static final int FIRST_REFLECTION_METADATA_INDEX = 1;
    private static final int INLINE_ENCODING = 1 << 31;
    private static final int INLINE_METADATA_MASK_SHIFT = 24;
    private static final int INLINE_METADATA_MASK = 0x7F << INLINE_METADATA_MASK_SHIFT;
    private static final int INLINE_VALUE_MASK = (1 << INLINE_METADATA_MASK_SHIFT) - 1;
    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final Method[] EMPTY_METHODS = new Method[0];
    private static final Constructor<?>[] EMPTY_CONSTRUCTORS = new Constructor<?>[0];

    @Platforms(Platform.HOSTED_ONLY.class)
    private ImageReflectionMetadataEncoding() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int encode(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int dynamicAccessIndex,
                    int unsafeAllocationIndex, int classFlags, int defaultClassFlags) {
        int mask = 0;
        if (fieldsEncodingIndex != NO_DATA) {
            mask |= FIELDS;
        }
        if (methodsEncodingIndex != NO_DATA) {
            mask |= METHODS;
        }
        if (constructorsEncodingIndex != NO_DATA) {
            mask |= CONSTRUCTORS;
        }
        if (recordComponentsEncodingIndex != NO_DATA) {
            mask |= RECORD_COMPONENTS;
        }
        if (dynamicAccessIndex != NO_DATA) {
            mask |= DYNAMIC_ACCESS;
        }
        if (unsafeAllocationIndex != NO_DATA) {
            mask |= UNSAFE_ALLOCATED;
        }
        if (classFlags != defaultClassFlags) {
            mask |= CLASS_FLAGS;
        }

        if (Integer.bitCount(mask) == 1) {
            int value = switch (mask) {
                case FIELDS -> fieldsEncodingIndex;
                case METHODS -> methodsEncodingIndex;
                case CONSTRUCTORS -> constructorsEncodingIndex;
                case RECORD_COMPONENTS -> recordComponentsEncodingIndex;
                case DYNAMIC_ACCESS -> dynamicAccessIndex;
                case UNSAFE_ALLOCATED -> unsafeAllocationIndex;
                case CLASS_FLAGS -> classFlags;
                default -> throw VMError.shouldNotReachHere("Unexpected reflection metadata mask: " + mask);
            };
            if (value >= 0 && value <= INLINE_VALUE_MASK) {
                return INLINE_ENCODING | (mask << INLINE_METADATA_MASK_SHIFT) | value;
            }
        }

        int index = RuntimeMetadataEncoding.currentLayer().addReflectionMetadata(1 + Integer.BYTES * Integer.bitCount(mask));
        byte[] data = RuntimeMetadataEncoding.currentLayer().getReflectionMetadataEncoding();
        data[index] = (byte) mask;
        int pos = index + 1;
        pos = putIfSet(data, pos, mask, FIELDS, fieldsEncodingIndex);
        pos = putIfSet(data, pos, mask, METHODS, methodsEncodingIndex);
        pos = putIfSet(data, pos, mask, CONSTRUCTORS, constructorsEncodingIndex);
        pos = putIfSet(data, pos, mask, RECORD_COMPONENTS, recordComponentsEncodingIndex);
        pos = putIfSet(data, pos, mask, DYNAMIC_ACCESS, dynamicAccessIndex);
        pos = putIfSet(data, pos, mask, UNSAFE_ALLOCATED, unsafeAllocationIndex);
        putIfSet(data, pos, mask, CLASS_FLAGS, classFlags);
        return index + FIRST_REFLECTION_METADATA_INDEX;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int putIfSet(byte[] data, int pos, int mask, int bit, int value) {
        if ((mask & bit) != 0) {
            assert SubstrateTarget.getArchitecture().getByteOrder() == ByteOrder.LITTLE_ENDIAN;
            data[pos] = (byte) value;
            data[pos + 1] = (byte) (value >>> 8);
            data[pos + 2] = (byte) (value >>> 16);
            data[pos + 3] = (byte) (value >>> 24);
            return pos + Integer.BYTES;
        }
        return pos;
    }

    public static boolean hasMetadata(int encodedReflectionMetadata) {
        return encodedReflectionMetadata != NO_REFLECTION_METADATA;
    }

    public static int getClassFlags(int encodedReflectionMetadata, int defaultClassFlags) {
        return getValue(encodedReflectionMetadata, CLASS_FLAGS, defaultClassFlags);
    }

    public static RuntimeDynamicAccessMetadata getDynamicAccessMetadata(int encodedReflectionMetadata, int layerNum) {
        int index = getValue(encodedReflectionMetadata, DYNAMIC_ACCESS, NO_DATA);
        return index == NO_DATA ? null : RuntimeMetadataDecoder.singleton().parseDynamicAccessMetadata(index, layerNum);
    }

    public static RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(int encodedReflectionMetadata, int layerNum) {
        int index = getValue(encodedReflectionMetadata, UNSAFE_ALLOCATED, NO_DATA);
        return index == NO_DATA ? null : RuntimeMetadataDecoder.singleton().parseDynamicAccessMetadata(index, layerNum);
    }

    public static Field[] getDeclaredFields(int encodedReflectionMetadata, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(encodedReflectionMetadata, FIELDS, NO_DATA);
        return index == NO_DATA ? EMPTY_FIELDS : RuntimeMetadataDecoder.singleton().parseFields(declaringClass, index, publicOnly, layerNum);
    }

    public static Method[] getDeclaredMethods(int encodedReflectionMetadata, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(encodedReflectionMetadata, METHODS, NO_DATA);
        return index == NO_DATA ? EMPTY_METHODS : RuntimeMetadataDecoder.singleton().parseMethods(declaringClass, index, publicOnly, layerNum);
    }

    public static Constructor<?>[] getDeclaredConstructors(int encodedReflectionMetadata, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(encodedReflectionMetadata, CONSTRUCTORS, NO_DATA);
        return index == NO_DATA ? EMPTY_CONSTRUCTORS : RuntimeMetadataDecoder.singleton().parseConstructors(declaringClass, index, publicOnly, layerNum);
    }

    public static RecordComponent[] getRecordComponents(int encodedReflectionMetadata, DynamicHub declaringClass, int layerNum) {
        int index = getValue(encodedReflectionMetadata, RECORD_COMPONENTS, NO_DATA);
        if (index == NO_DATA) {
            throw DynamicHub.recordsNotAvailable(declaringClass);
        }
        return RuntimeMetadataDecoder.singleton().parseRecordComponents(declaringClass, index, layerNum);
    }

    private static int getValue(int encodedReflectionMetadata, int bit, int defaultValue) {
        if (!hasMetadata(encodedReflectionMetadata)) {
            return defaultValue;
        }
        if ((encodedReflectionMetadata & INLINE_ENCODING) != 0) {
            int mask = (encodedReflectionMetadata & INLINE_METADATA_MASK) >>> INLINE_METADATA_MASK_SHIFT;
            return mask == bit ? encodedReflectionMetadata & INLINE_VALUE_MASK : defaultValue;
        }
        byte[] data = MultiLayeredImageSingleton.getForLayer(RuntimeMetadataEncoding.class, 0).getReflectionMetadataEncoding();
        int pos = encodedReflectionMetadata - FIRST_REFLECTION_METADATA_INDEX;
        int mask = ByteArrayReader.getU1(data, pos++);
        if ((mask & bit) == 0) {
            return defaultValue;
        }
        for (int currentBit = FIELDS; currentBit <= CLASS_FLAGS; currentBit <<= 1) {
            if ((mask & currentBit) != 0) {
                int value = ByteArrayReader.getS4(data, pos);
                if (currentBit == bit) {
                    return value;
                }
                pos += Integer.BYTES;
            }
        }
        throw VMError.shouldNotReachHere("Could not find value for reflection metadata bit " + bit + " in mask " + mask);
    }

}
