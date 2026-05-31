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
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.reflect.RuntimeMetadataDecoder.NO_DATA;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;

/**
 * Instances of this class are used to represent the reflection metadata for Dynamic hubs prepared
 * at build time and included in the image. For dynamic hubs loaded dynamically at runtime
 * {@link RuntimeReflectionMetadata} is used.
 */
public final class ImageReflectionMetadata implements ReflectionMetadata {
    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int fieldsEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int methodsEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int constructorsEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int recordComponentsEncodingIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int dynamicAccessIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int unsafeAllocatedIndex;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.CompileQueueFinished.class)//
    final int classFlags;

    ImageReflectionMetadata(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int dynamicAccessIndex, int unsafeAllocatedIndex,
                    int classFlags) {
        this.fieldsEncodingIndex = fieldsEncodingIndex;
        this.methodsEncodingIndex = methodsEncodingIndex;
        this.constructorsEncodingIndex = constructorsEncodingIndex;
        this.recordComponentsEncodingIndex = recordComponentsEncodingIndex;
        this.dynamicAccessIndex = dynamicAccessIndex;
        this.unsafeAllocatedIndex = unsafeAllocatedIndex;
        this.classFlags = classFlags;
    }

    static int encode(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int dynamicAccessIndex,
                    int unsafeAllocationIndex, int classFlags, int defaultClassFlags) {
        return ImageReflectionMetadataEncoding.encode(fieldsEncodingIndex, methodsEncodingIndex, constructorsEncodingIndex, recordComponentsEncodingIndex, dynamicAccessIndex, unsafeAllocationIndex,
                        classFlags, defaultClassFlags);
    }

    static boolean hasMetadata(int reflectionMetadataEncodingIndex) {
        return ImageReflectionMetadataEncoding.hasMetadata(reflectionMetadataEncodingIndex);
    }

    static int getClassFlags(int reflectionMetadataEncodingIndex, int defaultClassFlags) {
        return ImageReflectionMetadataEncoding.getClassFlags(reflectionMetadataEncodingIndex, defaultClassFlags);
    }

    static RuntimeDynamicAccessMetadata getDynamicAccessMetadata(int reflectionMetadataEncodingIndex, int layerNum) {
        return ImageReflectionMetadataEncoding.getDynamicAccessMetadata(reflectionMetadataEncodingIndex, layerNum);
    }

    static RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(int reflectionMetadataEncodingIndex, int layerNum) {
        return ImageReflectionMetadataEncoding.getUnsafeAllocationMetadata(reflectionMetadataEncodingIndex, layerNum);
    }

    static Field[] getDeclaredFields(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        return ImageReflectionMetadataEncoding.getDeclaredFields(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
    }

    static Method[] getDeclaredMethods(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        return ImageReflectionMetadataEncoding.getDeclaredMethods(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
    }

    static Constructor<?>[] getDeclaredConstructors(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        return ImageReflectionMetadataEncoding.getDeclaredConstructors(reflectionMetadataEncodingIndex, declaringClass, publicOnly, layerNum);
    }

    static RecordComponent[] getRecordComponents(int reflectionMetadataEncodingIndex, DynamicHub declaringClass, int layerNum) {
        return ImageReflectionMetadataEncoding.getRecordComponents(reflectionMetadataEncodingIndex, declaringClass, layerNum);
    }

    @Override
    public int getClassFlags() {
        return classFlags;
    }

    @Override
    public Field[] getDeclaredFields(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        if (fieldsEncodingIndex == NO_DATA) {
            return new Field[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseFields(declaringClass, fieldsEncodingIndex, publicOnly, layerNum);
    }

    @Override
    public Method[] getDeclaredMethods(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        if (methodsEncodingIndex == NO_DATA) {
            return new Method[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseMethods(declaringClass, methodsEncodingIndex, publicOnly, layerNum);
    }

    @Override
    public Constructor<?>[] getDeclaredConstructors(DynamicHub declaringClass, boolean publicOnly, int layerNum) {
        if (constructorsEncodingIndex == NO_DATA) {
            return new Constructor<?>[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseConstructors(declaringClass, constructorsEncodingIndex, publicOnly, layerNum);
    }

    @Override
    public RecordComponent[] getRecordComponents(DynamicHub declaringClass, int layerNum) {
        if (recordComponentsEncodingIndex == NO_DATA) {
            /* See ReflectionDataBuilder.buildRecordComponents() for details. */
            throw DynamicHub.recordsNotAvailable(declaringClass);
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseRecordComponents(declaringClass, recordComponentsEncodingIndex, layerNum);
    }

    @Override
    public RuntimeDynamicAccessMetadata getDynamicAccessMetadata(DynamicHub dynamicHub, int layerNum) {
        if (dynamicAccessIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseDynamicAccessMetadata(dynamicAccessIndex, layerNum);
    }

    @Override
    public RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(DynamicHub dynamicHub, int layerNum) {
        if (unsafeAllocatedIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseDynamicAccessMetadata(unsafeAllocatedIndex, layerNum);
    }
}
