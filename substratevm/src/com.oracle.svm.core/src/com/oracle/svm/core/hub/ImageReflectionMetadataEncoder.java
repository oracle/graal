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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.code.RuntimeMetadataEncoding;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;

public final class ImageReflectionMetadataEncoder {
    private static final int FIELDS = 1;
    private static final int METHODS = 1 << 1;
    private static final int CONSTRUCTORS = 1 << 2;
    private static final int RECORD_COMPONENTS = 1 << 3;
    private static final int DYNAMIC_ACCESS = 1 << 4;
    private static final int UNSAFE_ALLOCATED = 1 << 5;
    private static final int CLASS_FLAGS = 1 << 6;

    /** Zero is the default value of {@link DynamicHubCompanion#reflectionMetadataEncodingIndex}. */
    private static final int NO_REFLECTION_METADATA = 0;
    private static final int FIRST_REFLECTION_METADATA_INDEX = 1;
    private static final int INLINE_ENCODING = 1 << 31;
    private static final int INLINE_METADATA_MASK_SHIFT = 24;
    private static final int INLINE_METADATA_MASK = 0x7F << INLINE_METADATA_MASK_SHIFT;
    private static final int INLINE_VALUE_MASK = (1 << INLINE_METADATA_MASK_SHIFT) - 1;

    private ImageReflectionMetadataEncoder() {
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
                default -> throw new IllegalStateException();
            };
            if (value >= 0 && value <= INLINE_VALUE_MASK) {
                return INLINE_ENCODING | (mask << INLINE_METADATA_MASK_SHIFT) | value;
            }
        }

        int index = RuntimeMetadataEncoding.currentLayer().addReflectionMetadata(1 + 4 * Integer.bitCount(mask));
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

    private static int putIfSet(byte[] data, int pos, int mask, int bit, int value) {
        if ((mask & bit) != 0) {
            data[pos] = (byte) value;
            data[pos + 1] = (byte) (value >>> 8);
            data[pos + 2] = (byte) (value >>> 16);
            data[pos + 3] = (byte) (value >>> 24);
            return pos + 4;
        }
        return pos;
    }

    public static boolean hasMetadata(int reflectionMetadataEncodingIndex) {
        return reflectionMetadataEncodingIndex != NO_REFLECTION_METADATA;
    }

    public static int getClassFlags(int reflectionMetadataEncodingIndex, int defaultClassFlags) {
        return getValue(reflectionMetadataEncodingIndex, CLASS_FLAGS, defaultClassFlags);
    }

    public static RuntimeDynamicAccessMetadata getDynamicAccessMetadata(int reflectionMetadataEncodingIndex, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, DYNAMIC_ACCESS, NO_DATA);
        return index == NO_DATA ? null : ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseDynamicAccessMetadata(index, layerNum);
    }

    public static RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(int reflectionMetadataEncodingIndex, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, UNSAFE_ALLOCATED, NO_DATA);
        return index == NO_DATA ? null : ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseDynamicAccessMetadata(index, layerNum);
    }

    public static Field[] getDeclaredFields(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, FIELDS, NO_DATA);
        return index == NO_DATA ? new Field[0] : ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseFields(declaringClass, index, publicOnly, layerNum);
    }

    public static Method[] getDeclaredMethods(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, METHODS, NO_DATA);
        return index == NO_DATA ? new Method[0] : ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseMethods(declaringClass, index, publicOnly, layerNum);
    }

    public static Constructor<?>[] getDeclaredConstructors(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, CONSTRUCTORS, NO_DATA);
        return index == NO_DATA ? new Constructor<?>[0] : ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseConstructors(declaringClass, index, publicOnly, layerNum);
    }

    public static RecordComponent[] getRecordComponents(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, int layerNum) {
        int index = getValue(reflectionMetadataEncodingIndex, RECORD_COMPONENTS, NO_DATA);
        if (index == NO_DATA) {
            throw DynamicHub.recordsNotAvailable(declaringClass);
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseRecordComponents(declaringClass, index, layerNum);
    }

    private static int getValue(int reflectionMetadataEncodingIndex, int bit, int defaultValue) {
        if (!hasMetadata(reflectionMetadataEncodingIndex)) {
            return defaultValue;
        }
        if ((reflectionMetadataEncodingIndex & INLINE_ENCODING) != 0) {
            int mask = (reflectionMetadataEncodingIndex & INLINE_METADATA_MASK) >>> INLINE_METADATA_MASK_SHIFT;
            return mask == bit ? reflectionMetadataEncodingIndex & INLINE_VALUE_MASK : defaultValue;
        }
        byte[] data = MultiLayeredImageSingleton.getForLayer(RuntimeMetadataEncoding.class, 0).getReflectionMetadataEncoding();
        int pos = reflectionMetadataEncodingIndex - FIRST_REFLECTION_METADATA_INDEX;
        int mask = ByteArrayReader.getU1(data, pos++);
        if ((mask & bit) == 0) {
            return defaultValue;
        }
        int[] bits = {FIELDS, METHODS, CONSTRUCTORS, RECORD_COMPONENTS, DYNAMIC_ACCESS, UNSAFE_ALLOCATED, CLASS_FLAGS};
        for (int currentBit : bits) {
            if ((mask & currentBit) != 0) {
                int value = ByteArrayReader.getS4(data, pos);
                if (currentBit == bit) {
                    return value;
                }
                pos += 4;
            }
        }
        throw new IllegalStateException();
    }

    static final class ReflectionMetadataView implements ReflectionMetadata {
        @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class) //
        private final int reflectionMetadataEncodingIndex;

        ReflectionMetadataView(int reflectionMetadataEncodingIndex) {
            this.reflectionMetadataEncodingIndex = reflectionMetadataEncodingIndex;
        }

        @Override
        public int getClassFlags() {
            return ImageReflectionMetadataEncoder.getClassFlags(reflectionMetadataEncodingIndex, 0);
        }

        @Override
        public Field[] getDeclaredFields(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
            return ImageReflectionMetadataEncoder.getDeclaredFields(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
        }

        @Override
        public Method[] getDeclaredMethods(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
            return ImageReflectionMetadataEncoder.getDeclaredMethods(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
        }

        @Override
        public Constructor<?>[] getDeclaredConstructors(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
            return ImageReflectionMetadataEncoder.getDeclaredConstructors(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
        }

        @Override
        public RecordComponent[] getRecordComponents(DynamicHub dynamicHub, int layerNum) {
            return ImageReflectionMetadataEncoder.getRecordComponents(reflectionMetadataEncodingIndex, dynamicHub, layerNum);
        }

        @Override
        public RuntimeDynamicAccessMetadata getDynamicAccessMetadata(DynamicHub dynamicHub, int layerNum) {
            return ImageReflectionMetadataEncoder.getDynamicAccessMetadata(reflectionMetadataEncodingIndex, layerNum);
        }

        @Override
        public RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(DynamicHub dynamicHub, int layerNum) {
            return ImageReflectionMetadataEncoder.getUnsafeAllocationMetadata(reflectionMetadataEncodingIndex, layerNum);
        }
    }
}
