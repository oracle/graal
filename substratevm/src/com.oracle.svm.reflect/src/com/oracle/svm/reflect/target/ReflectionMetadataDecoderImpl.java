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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.function.Function;

import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.core.reflect.Target_java_lang_reflect_RecordComponent;
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
public class ReflectionMetadataDecoderImpl implements ReflectionMetadataDecoder {
    public static final int NO_METHOD_METADATA = -1;
    public static final int NULL_OBJECT = -1;
    public static final int COMPLETE_FLAG_INDEX = 31;
    public static final int COMPLETE_FLAG_MASK = 1 << COMPLETE_FLAG_INDEX;
    public static final int IN_HEAP_FLAG_INDEX = 30;
    public static final int IN_HEAP_FLAG_MASK = 1 << IN_HEAP_FLAG_INDEX;
    public static final int HIDING_FLAG_INDEX = 29;
    public static final int HIDING_FLAG_MASK = 1 << HIDING_FLAG_INDEX;

    static byte[] getEncoding() {
        return ImageSingletons.lookup(ReflectionMetadataEncoding.class).getEncoding();
    }

    @Override
    public Field[] parseFields(DynamicHub declaringType, int index, boolean publicOnly, boolean reflectOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Field.class, (i) -> decodeField(reader, codeInfo, DynamicHub.toClass(declaringType), publicOnly, reflectOnly));
    }

    @Override
    public Method[] parseMethods(DynamicHub declaringType, int index, boolean publicOnly, boolean reflectOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Method.class, (i) -> decodeMethod(reader, codeInfo, DynamicHub.toClass(declaringType), publicOnly, reflectOnly));
    }

    @Override
    public Constructor<?>[] parseConstructors(DynamicHub declaringType, int index, boolean publicOnly, boolean reflectOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Constructor.class, (i) -> decodeConstructor(reader, codeInfo, DynamicHub.toClass(declaringType), publicOnly, reflectOnly));
    }

    @Override
    public Class<?>[] parseClasses(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Class.class, (i) -> decodeType(reader, codeInfo));
    }

    @Override
    public Target_java_lang_reflect_RecordComponent[] parseRecordComponents(DynamicHub declaringType, int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Target_java_lang_reflect_RecordComponent.class, (i) -> decodeRecordComponent(reader, codeInfo, DynamicHub.toClass(declaringType)));
    }

    @Override
    public Parameter[] parseReflectParameters(Executable executable, byte[] encoding) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(encoding, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return decodeArray(reader, Parameter.class, (i) -> decodeReflectParameter(reader, codeInfo, executable, i));
    }

    @Override
    public Object[] parseEnclosingMethod(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        Class<?> declaringClass = decodeType(reader, codeInfo);
        String name = decodeName(reader, codeInfo);
        String descriptor = decodeName(reader, codeInfo);
        return new Object[]{declaringClass, name, descriptor};
    }

    @Override
    public byte[] parseByteArray(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeByteArray(reader);
    }

    @Override
    public boolean isHidingMethod(int modifiers) {
        return (modifiers & HIDING_FLAG_MASK) != 0;
    }

    @Override
    public long getMetadataByteLength() {
        return ImageSingletons.lookup(ReflectionMetadataEncoding.class).getEncoding().length;
    }

    private static Field decodeField(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly) {
        int modifiers = buf.getUVInt();
        boolean inHeap = (modifiers & IN_HEAP_FLAG_MASK) != 0;
        if (inHeap) {
            Field field = (Field) decodeObject(buf, info);
            if (publicOnly && !Modifier.isPublic(field.getModifiers())) {
                return null;
            }
            return field;
        }
        boolean complete = (modifiers & COMPLETE_FLAG_MASK) != 0;
        modifiers &= ~COMPLETE_FLAG_MASK;

        String name = decodeName(buf, info);
        if (!complete) {
            if (reflectOnly) {
                return null;
            }
            Target_java_lang_reflect_Field field = new Target_java_lang_reflect_Field();
            if (JavaVersionUtil.JAVA_SPEC >= 17) {
                field.constructorJDK17OrLater(declaringClass, name, null, modifiers, false, -1, null, null);
            } else {
                field.constructorJDK11OrEarlier(declaringClass, name, null, modifiers, -1, null, null);
            }
            return SubstrateUtil.cast(field, Field.class);
        }
        Class<?> type = decodeType(buf, info);
        boolean trustedFinal = (JavaVersionUtil.JAVA_SPEC >= 17) ? buf.getU1() == 1 : false;
        String signature = decodeName(buf, info);
        byte[] annotations = decodeByteArray(buf);
        byte[] typeAnnotations = decodeByteArray(buf);
        int offset = buf.getSVInt();
        String deletedReason = decodeName(buf, info);
        if (publicOnly && !Modifier.isPublic(modifiers)) {
            return null;
        }

        Target_java_lang_reflect_Field field = new Target_java_lang_reflect_Field();
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            field.constructorJDK17OrLater(declaringClass, name, type, modifiers, trustedFinal, -1, signature, annotations);
        } else {
            field.constructorJDK11OrEarlier(declaringClass, name, type, modifiers, -1, signature, annotations);
        }
        field.offset = offset;
        field.deletedReason = deletedReason;
        SubstrateUtil.cast(field, Target_java_lang_reflect_AccessibleObject.class).typeAnnotations = typeAnnotations;
        return SubstrateUtil.cast(field, Field.class);
    }

    private static Method decodeMethod(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly) {
        return (Method) decodeExecutable(buf, info, declaringClass, publicOnly, reflectOnly, true);
    }

    private static Constructor<?> decodeConstructor(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly) {
        return (Constructor<?>) decodeExecutable(buf, info, declaringClass, publicOnly, reflectOnly, false);
    }

    private static Executable decodeExecutable(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly, boolean isMethod) {
        int modifiers = buf.getUVInt();
        boolean inHeap = (modifiers & IN_HEAP_FLAG_MASK) != 0;
        if (inHeap) {
            Executable executable = (Executable) decodeObject(buf, info);
            if (publicOnly && !Modifier.isPublic(executable.getModifiers())) {
                return null;
            }
            return executable;
        }
        boolean complete = (modifiers & COMPLETE_FLAG_MASK) != 0;
        boolean hiding = (modifiers & HIDING_FLAG_MASK) != 0;
        modifiers &= ~COMPLETE_FLAG_MASK;

        String name = isMethod ? decodeName(buf, info) : null;
        Class<?>[] parameterTypes = decodeArray(buf, Class.class, (i) -> decodeType(buf, info));
        Class<?> returnType = isMethod && hiding ? decodeType(buf, info) : null;
        if (!complete) {
            if (reflectOnly != hiding) {
                /*
                 * When querying for reflection methods, we want the hiding methods but not the
                 * reachable methods. When querying for reachable methods, we want the reachable
                 * methods but not the hiding methods.
                 */
                return null;
            }
            if (isMethod) {
                Target_java_lang_reflect_Method method = new Target_java_lang_reflect_Method();
                method.constructor(declaringClass, name, parameterTypes, returnType, null, modifiers, -1, null, null, null, null);
                return SubstrateUtil.cast(method, Executable.class);
            } else {
                Target_java_lang_reflect_Constructor constructor = new Target_java_lang_reflect_Constructor();
                constructor.constructor(declaringClass, parameterTypes, null, modifiers, -1, null, null, null);
                return SubstrateUtil.cast(constructor, Executable.class);
            }
        }
        assert !hiding;
        returnType = isMethod ? decodeType(buf, info) : null;
        Class<?>[] exceptionTypes = decodeArray(buf, Class.class, (i) -> decodeType(buf, info));
        String signature = decodeName(buf, info);
        byte[] annotations = decodeByteArray(buf);
        byte[] parameterAnnotations = decodeByteArray(buf);
        byte[] annotationDefault = isMethod && declaringClass.isAnnotation() ? decodeByteArray(buf) : null;
        byte[] typeAnnotations = decodeByteArray(buf);
        byte[] reflectParameters = decodeByteArray(buf);
        Object accessor = decodeObject(buf, info);
        if (publicOnly && !Modifier.isPublic(modifiers)) {
            return null;
        }

        Target_java_lang_reflect_Executable executable;
        if (isMethod) {
            Target_java_lang_reflect_Method method = new Target_java_lang_reflect_Method();
            method.constructor(declaringClass, name, parameterTypes, returnType, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations, annotationDefault);
            method.methodAccessor = (Target_jdk_internal_reflect_MethodAccessor) accessor;
            executable = SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class);
        } else {
            Target_java_lang_reflect_Constructor constructor = new Target_java_lang_reflect_Constructor();
            constructor.constructor(declaringClass, parameterTypes, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations);
            constructor.constructorAccessor = (Target_jdk_internal_reflect_ConstructorAccessor) accessor;
            executable = SubstrateUtil.cast(constructor, Target_java_lang_reflect_Executable.class);
        }
        executable.rawParameters = reflectParameters;
        SubstrateUtil.cast(executable, Target_java_lang_reflect_AccessibleObject.class).typeAnnotations = typeAnnotations;
        return SubstrateUtil.cast(executable, Executable.class);
    }

    private static Target_java_lang_reflect_RecordComponent decodeRecordComponent(UnsafeArrayTypeReader buf, CodeInfo info, Class<?> declaringClass) {
        String name = decodeName(buf, info);
        Class<?> type = decodeType(buf, info);
        String signature = decodeName(buf, info);
        Method accessor = (Method) decodeObject(buf, info);
        byte[] annotations = decodeByteArray(buf);
        byte[] typeAnnotations = decodeByteArray(buf);

        Target_java_lang_reflect_RecordComponent recordComponent = new Target_java_lang_reflect_RecordComponent();
        recordComponent.clazz = declaringClass;
        recordComponent.name = name;
        recordComponent.type = type;
        recordComponent.signature = signature;
        recordComponent.accessor = accessor;
        recordComponent.annotations = annotations;
        recordComponent.typeAnnotations = typeAnnotations;
        return recordComponent;
    }

    private static Parameter decodeReflectParameter(UnsafeArrayTypeReader buf, CodeInfo info, Executable executable, int i) {
        String name = decodeName(buf, info);
        int modifiers = buf.getUVInt();

        Target_java_lang_reflect_Parameter parameter = new Target_java_lang_reflect_Parameter();
        parameter.constructor(name, modifiers, executable, i);
        return SubstrateUtil.cast(parameter, Parameter.class);
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

    private static Object decodeObject(UnsafeArrayTypeReader buf, CodeInfo info) {
        int objectIndex = buf.getSVInt();
        if (objectIndex == NULL_OBJECT) {
            return null;
        }
        return NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoObjectConstants(info), objectIndex);
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] decodeArray(UnsafeArrayTypeReader buf, Class<T> elementType, Function<Integer, T> elementDecoder) {
        int length = buf.getUVInt();
        T[] result = (T[]) Array.newInstance(elementType, length);
        int valueCount = 0;
        for (int i = 0; i < length; ++i) {
            T element = elementDecoder.apply(i);
            if (element != null) {
                result[valueCount++] = element;
            }
        }
        return Arrays.copyOf(result, valueCount);
    }

    private static byte[] decodeByteArray(UnsafeArrayTypeReader buf) {
        int length = buf.getUVInt();
        if (length == 0) {
            return null;
        }
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) buf.getS1();
        }
        return result;
    }
}
