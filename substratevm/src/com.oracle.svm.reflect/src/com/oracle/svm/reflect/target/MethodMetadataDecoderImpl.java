/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.target;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.MethodMetadataDecoder;
import com.oracle.svm.core.util.ByteArrayReader;

/**
 * The metadata for methods in the image is split into two arrays: one for the index and the other
 * for data. The index contains an array of integers pointing to offsets in the data, and indexed by
 * type ID. The data array contains arrays of method metadata, ordered by type ID, such that all
 * methods declared by a class are stored consecutively, in the following format:
 *
 * <pre>
 * {
 *     int queriedMethodsCount;
 *     ReflectMethodEncoding[] queriedMethods[queriedMethodsCount];
 *     int hidingMethodsCount;
 *     SimpleMethodEncoding[] hidingMethods[hidingMethodsCount];
 *     int declaringTypeIndex;             // index in frameInfoSourceClasses
 *     int reachableMethodsCount;
 *     SimpleMethodEncoding[] reachableMethods[reachableMethodsCount];
 * } TypeEncoding;
 * </pre>
 *
 * The declaring class is encoded before the reachable methods to avoid having to be decoded when
 * getting the queried and hiding methods, in which case the declaring class is available as an
 * argument and doesn't need to be retrieved from the encoding.
 *
 * The data for a queried method is stored in the following format:
 *
 * <pre>
 * {
 *     int methodNameIndex;                // index in frameInfoSourceMethodNames ("<init>" for constructors)
 *     int paramCount;
 *     int[] paramTypeIndices[paramCount]; // index in frameInfoSourceClasses
 *     int modifiers;
 *     int returnTypeIndex;                // index in frameInfoSourceClasses (void for constructors)
 *     int exceptionTypeCount;
 *     int[] exceptionTypeIndices[exceptionTypeCount]; // index in frameInfoSourceClasses
 *     int signatureIndex;                 // index in frameInfoSourceMethodNames
 *     int annotationsEncodingLength;
 *     byte[] annotationsEncoding[annotationsEncodingLength];
 *     int parameterAnnotationsEncodingLength;
 *     byte[] parameterAnnotationsEncoding[parameterAnnotationsEncodingLength];
 *     int typeAnnotationsEncodingLength;
 *     byte[] typeAnnotationsEncoding[typeAnnotationsEncodingLength];
 *     boolean hasRealParameterData;
 *     int reflectParameterCount;          // only if hasRealParameterData is true
 *     {
 *         int reflectParameterNameIndex;  // index in frameInfoSourceMethodNames
 *         int reflectParameterModifiers;
 *     } reflectParameters[reflectParameterCount];
 * } ReflectMethodEncoding;
 * </pre>
 *
 * The data for a hiding or reachable method is stored as follows:
 *
 * <pre>
 * {
 *     int methodNameIndex;                // index in frameInfoSourceMethodNames ("<init>" for constructors)
 *     int paramCount;
 *     int[] paramTypeIndices[paramCount]; // index in frameInfoSourceClasses
 * } SimpleMethodEncoding;
 * </pre>
 */
public class MethodMetadataDecoderImpl implements MethodMetadataDecoder {
    public static final int NO_METHOD_METADATA = -1;

    @Fold
    static boolean hasQueriedMethods() {
        return !ImageSingletons.lookup(RuntimeReflectionSupport.class).getQueriedOnlyMethods().isEmpty();
    }

    /**
     * This method returns two arrays. The first one contains the desired method data, the second
     * one contains the names and parameter types of methods hiding methods declared in superclasses
     * which therefore should not be returned by a call to getMethods().
     */
    @Override
    public Pair<Executable[], MethodDescriptor[]> getQueriedAndHidingMethods(DynamicHub declaringType) {
        int dataOffset = getOffset(declaringType.getTypeID());
        if (SubstrateOptions.ConfigureReflectionMetadata.getValue() && getOffset(declaringType.getTypeID()) != NO_METHOD_METADATA) {
            CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
            byte[] data = ImageSingletons.lookup(MethodMetadataEncoding.class).getMethodsEncoding();
            UnsafeArrayTypeReader dataReader = UnsafeArrayTypeReader.create(data, dataOffset, ByteArrayReader.supportsUnalignedMemoryAccess());

            Executable[] queriedMethods = decodeArray(dataReader, Executable.class, () -> decodeReflectionMethod(dataReader, codeInfo, DynamicHub.toClass(declaringType)));
            MethodDescriptor[] hiddenMethods = decodeArray(dataReader, MethodDescriptor.class, () -> decodeSimpleMethod(dataReader, codeInfo, DynamicHub.toClass(declaringType)));
            return Pair.create(queriedMethods, hiddenMethods);
        } else {
            return Pair.create(new Executable[0], new MethodDescriptor[0]);
        }
    }

    @Override
    public MethodDescriptor[] getAllReachableMethods() {
        if (!SubstrateOptions.IncludeMethodData.getValue()) {
            return new MethodDescriptor[0];
        }

        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        byte[] data = ImageSingletons.lookup(MethodMetadataEncoding.class).getMethodsEncoding();
        UnsafeArrayTypeReader dataReader = UnsafeArrayTypeReader.create(data, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        List<MethodDescriptor> allMethods = new ArrayList<>();
        for (int i = 0; i < ImageSingletons.lookup(MethodMetadataEncoding.class).getIndexEncoding().length / Integer.BYTES; ++i) {
            int dataOffset = getOffset(i);
            if (dataOffset != NO_METHOD_METADATA) {
                dataReader.setByteIndex(dataOffset);
                if (SubstrateOptions.ConfigureReflectionMetadata.getValue()) {
                    /* Skip the queried methods data */
                    decodeArray(dataReader, Executable.class, () -> decodeReflectionMethod(dataReader, codeInfo, null));
                    decodeArray(dataReader, MethodDescriptor.class, () -> decodeSimpleMethod(dataReader, codeInfo, null));
                }
                Class<?> declaringClass = decodeType(dataReader, codeInfo);
                if (declaringClass != null) {
                    allMethods.addAll(Arrays.asList(decodeArray(dataReader, MethodDescriptor.class, () -> decodeSimpleMethod(dataReader, codeInfo, declaringClass))));
                }
            }
        }
        return allMethods.toArray(new MethodDescriptor[0]);
    }

    @Override
    public long getMetadataByteLength() {
        MethodMetadataEncoding encoding = ImageSingletons.lookup(MethodMetadataEncoding.class);
        return encoding.getMethodsEncoding().length + encoding.getIndexEncoding().length;
    }

    private static int getOffset(int typeID) {
        MethodMetadataEncoding encoding = ImageSingletons.lookup(MethodMetadataEncoding.class);
        byte[] index = encoding.getIndexEncoding();
        UnsafeArrayTypeReader indexReader = UnsafeArrayTypeReader.create(index, Integer.BYTES * typeID, ByteArrayReader.supportsUnalignedMemoryAccess());
        return indexReader.getS4();
    }

    private static Executable decodeReflectionMethod(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass) {
        String name = decodeName(buf, info);
        Class<?>[] parameterTypes = decodeArray(buf, Class.class, () -> decodeType(buf, info));
        int modifiers = buf.getUVInt();
        Class<?> returnType = decodeType(buf, info);
        Class<?>[] exceptionTypes = decodeArray(buf, Class.class, () -> decodeType(buf, info));
        String signature = decodeName(buf, info);
        byte[] annotations = decodeByteArray(buf);
        byte[] parameterAnnotations = decodeByteArray(buf);
        byte[] typeAnnotations = decodeByteArray(buf);
        boolean hasRealParameterData = buf.getU1() == 1;
        ReflectParameterDescriptor[] reflectParameters = hasRealParameterData ? decodeArray(buf, ReflectParameterDescriptor.class, () -> decodeReflectParameter(buf, info)) : null;

        Target_java_lang_reflect_Executable executable;
        if (name.equals("<init>")) {
            assert returnType == void.class;
            Target_java_lang_reflect_Constructor constructor = new Target_java_lang_reflect_Constructor();
            constructor.constructor(declaringClass, parameterTypes, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations);
            executable = SubstrateUtil.cast(constructor, Target_java_lang_reflect_Executable.class);
        } else {
            Target_java_lang_reflect_Method method = new Target_java_lang_reflect_Method();
            method.constructor(declaringClass, name, parameterTypes, returnType, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations, null);
            executable = SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class);
        }
        if (hasQueriedMethods()) {
            executable.hasRealParameterData = hasRealParameterData;
            if (hasRealParameterData) {
                fillReflectParameters(executable, reflectParameters);
            }
            executable.typeAnnotations = typeAnnotations;
        }
        return SubstrateUtil.cast(executable, Executable.class);
    }

    private static void fillReflectParameters(Target_java_lang_reflect_Executable executable, ReflectParameterDescriptor[] reflectParameters) {
        executable.parameters = new Target_java_lang_reflect_Parameter[reflectParameters.length];
        for (int i = 0; i < reflectParameters.length; ++i) {
            executable.parameters[i] = new Target_java_lang_reflect_Parameter();
            executable.parameters[i].constructor(reflectParameters[i].getName(), reflectParameters[i].getModifiers(), executable, i);
        }
    }

    private static MethodDescriptor decodeSimpleMethod(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass) {
        String name = decodeName(buf, info);
        Class<?>[] paramTypes = decodeArray(buf, Class.class, () -> decodeType(buf, info));
        return new MethodDescriptor(declaringClass, name, paramTypes);
    }

    private static Class<?> decodeType(UnsafeArrayTypeReader buf, CodeInfo info) {
        int classIndex = buf.getSVInt();
        if (classIndex == NO_METHOD_METADATA) {
            return null;
        }
        return NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceClasses(info), classIndex);
    }

    private static String decodeName(UnsafeArrayTypeReader buf, CodeInfo info) {
        int nameIndex = buf.getSVInt();
        String name = NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceMethodNames(info), nameIndex);
        /* Interning the string to ensure JDK8 method search succeeds */
        return name == null ? null : name.intern();
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] decodeArray(UnsafeArrayTypeReader buf, Class<T> elementType, Supplier<T> elementDecoder) {
        int length = buf.getUVInt();
        T[] result = (T[]) Array.newInstance(elementType, length);
        for (int i = 0; i < length; ++i) {
            result[i] = elementDecoder.get();
        }
        return result;
    }

    private static byte[] decodeByteArray(UnsafeArrayTypeReader buf) {
        int length = buf.getUVInt();
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) buf.getS1();
        }
        return result;
    }

    private static ReflectParameterDescriptor decodeReflectParameter(UnsafeArrayTypeReader buf, CodeInfo info) {
        String name = decodeName(buf, info);
        int modifiers = buf.getS4();
        return new ReflectParameterDescriptor(name, modifiers);
    }

    public static class ReflectParameterDescriptor {
        private final String name;
        private final int modifiers;

        public ReflectParameterDescriptor(String name, int modifiers) {
            this.name = name;
            this.modifiers = modifiers;
        }

        public String getName() {
            return name;
        }

        public int getModifiers() {
            return modifiers;
        }
    }
}
