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
package com.oracle.svm.core.code;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.core.reflect.target.ReflectionObjectFactory;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_Executable;
import com.oracle.svm.core.util.ByteArrayReader;

import jdk.graal.compiler.core.common.util.UnsafeArrayTypeReader;

/**
 * This class parses the runtime metadata at image runtime. The encoding formats are specified as
 * comments above each parsing method.
 *
 * See {@code ReflectionMetadataEncoderImpl} for details about the emission of the metadata.
 */
@AutomaticallyRegisteredImageSingleton(RuntimeMetadataDecoder.class)
public class RuntimeMetadataDecoderImpl implements RuntimeMetadataDecoder {
    /**
     * Error indices are less than {@link #NO_DATA}.
     */
    public static final int FIRST_ERROR_INDEX = NO_DATA - 1;
    public static final int NO_METHOD_METADATA = -1;
    public static final int NULL_OBJECT = -1;
    public static final int COMPLETE_FLAG_INDEX = 31;
    public static final int COMPLETE_FLAG_MASK = 1 << COMPLETE_FLAG_INDEX;
    public static final int IN_HEAP_FLAG_INDEX = 30;
    public static final int IN_HEAP_FLAG_MASK = 1 << IN_HEAP_FLAG_INDEX;
    public static final int HIDING_FLAG_INDEX = 29;
    public static final int HIDING_FLAG_MASK = 1 << HIDING_FLAG_INDEX;
    public static final int NEGATIVE_FLAG_INDEX = 28;
    public static final int NEGATIVE_FLAG_MASK = 1 << NEGATIVE_FLAG_INDEX;
    /* single lookup flags are filled before encoding */
    public static final int ALL_FLAGS_MASK = COMPLETE_FLAG_MASK | IN_HEAP_FLAG_MASK | HIDING_FLAG_MASK | NEGATIVE_FLAG_MASK;

    public static final int ALL_FIELDS_FLAG = 1 << 16;
    public static final int ALL_DECLARED_FIELDS_FLAG = 1 << 17;
    public static final int ALL_METHODS_FLAG = 1 << 18;
    public static final int ALL_DECLARED_METHODS_FLAG = 1 << 19;
    public static final int ALL_CONSTRUCTORS_FLAG = 1 << 20;
    public static final int ALL_DECLARED_CONSTRUCTORS_FLAG = 1 << 21;
    public static final int ALL_CLASSES_FLAG = 1 << 22;
    public static final int ALL_DECLARED_CLASSES_FLAG = 1 << 23;
    public static final int ALL_RECORD_COMPONENTS_FLAG = 1 << 24;
    public static final int ALL_PERMITTED_SUBCLASSES_FLAG = 1 << 25;
    public static final int ALL_NEST_MEMBERS_FLAG = 1 << 26;
    public static final int ALL_SIGNERS_FLAG = 1 << 27;

    // Value from Reflection.getClassAccessFlags()
    public static final int CLASS_ACCESS_FLAGS_MASK = 0x1FFF;

    static byte[] getEncoding() {
        return ImageSingletons.lookup(RuntimeMetadataEncoding.class).getEncoding();
    }

    /**
     * Fields encoding.
     *
     * <pre>
     * FieldMetadata[] fields
     * </pre>
     */
    @Override
    public Field[] parseFields(DynamicHub declaringType, int index, boolean publicOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Field.class, (i) -> (Field) decodeField(reader, DynamicHub.toClass(declaringType), publicOnly, true));
    }

    @Override
    public FieldDescriptor[] parseReachableFields(DynamicHub declaringType, int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, FieldDescriptor.class, (i) -> (FieldDescriptor) decodeField(reader, DynamicHub.toClass(declaringType), false, false));
    }

    /**
     * Methods encoding.
     *
     * <pre>
     * MethodMetadata[] methods
     * </pre>
     */
    @Override
    public Method[] parseMethods(DynamicHub declaringType, int index, boolean publicOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Method.class, (i) -> (Method) decodeExecutable(reader, DynamicHub.toClass(declaringType), publicOnly, true, true));
    }

    @Override
    public MethodDescriptor[] parseReachableMethods(DynamicHub declaringType, int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, MethodDescriptor.class, (i) -> (MethodDescriptor) decodeExecutable(reader, DynamicHub.toClass(declaringType), false, false, true));
    }

    /**
     * Constructors encoding.
     *
     * <pre>
     * ConstructorMetadata[] constructors
     * </pre>
     */
    @Override
    public Constructor<?>[] parseConstructors(DynamicHub declaringType, int index, boolean publicOnly) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Constructor.class, (i) -> (Constructor<?>) decodeExecutable(reader, DynamicHub.toClass(declaringType), publicOnly, true, false));
    }

    @Override
    public ConstructorDescriptor[] parseReachableConstructors(DynamicHub declaringType, int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, ConstructorDescriptor.class, (i) -> (ConstructorDescriptor) decodeExecutable(reader, DynamicHub.toClass(declaringType), false, false, false));
    }

    /**
     * Inner classes encoding.
     *
     * <pre>
     * ClassIndex[] innerClasses
     * </pre>
     */
    @Override
    public Class<?>[] parseClasses(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Class.class, (i) -> decodeType(reader));
    }

    /**
     * Record components encoding.
     *
     * <pre>
     * RecordComponentMetadata[] recordComponents
     * </pre>
     */
    @Override
    public RecordComponent[] parseRecordComponents(DynamicHub declaringType, int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, RecordComponent.class, (i) -> decodeRecordComponent(reader, DynamicHub.toClass(declaringType)));
    }

    /**
     * Object array encoding.
     *
     * <pre>
     * ObjectIndex[] objects
     * </pre>
     */
    @Override
    public Object[] parseObjects(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Object.class, (i) -> decodeObject(reader));
    }

    /**
     * Parameters encoding for executables.
     *
     * <pre>
     * ParameterMetadata[] parameters
     * </pre>
     */
    @Override
    public Parameter[] parseReflectParameters(Executable executable, byte[] encoding) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(encoding, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeArray(reader, Parameter.class, (i) -> decodeReflectParameter(reader, executable, i));
    }

    /**
     * Class enclosing method information. {@link Class#getEnclosingMethod()}
     *
     * <pre>
     * EnclosingMethodInfo {
     *     ClassIndex  declaringClass
     *     MembNameIdx name
     *     StringIndex descriptor
     * }
     * </pre>
     */
    @Override
    public Object[] parseEnclosingMethod(int index) {
        if (isErrorIndex(index)) {
            decodeAndThrowError(index);
        }
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        Class<?> declaringClass = decodeType(reader);
        String name = decodeMemberName(reader);
        String descriptor = decodeOtherString(reader);
        return new Object[]{declaringClass, name, descriptor};
    }

    @Override
    public byte[] parseByteArray(int index) {
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(getEncoding(), index, ByteArrayReader.supportsUnalignedMemoryAccess());
        return decodeByteArray(reader);
    }

    @Override
    public boolean isHiding(int modifiers) {
        return (modifiers & HIDING_FLAG_MASK) != 0;
    }

    @Override
    public boolean isNegative(int modifiers) {
        return (modifiers & NEGATIVE_FLAG_MASK) != 0;
    }

    @Override
    public int getMetadataByteLength() {
        return ImageSingletons.lookup(RuntimeMetadataEncoding.class).getEncoding().length;
    }

    public static boolean isErrorIndex(int index) {
        return index < NO_DATA;
    }

    /**
     * Errors are encoded as negated indices of the frame info object constants array.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void decodeAndThrowError(int index) throws T {
        assert isErrorIndex(index);
        int decodedIndex = FIRST_ERROR_INDEX - index;
        throw (T) MetadataAccessor.singleton().getObject(decodedIndex);
    }

    /**
     * Complete field encoding.
     *
     * <pre>
     * CompleteFieldMetadata : FieldMetadata {
     *     int         modifiers               (including COMPLETE flag)
     *     MembNameIdx name
     *     ClassIndex  type
     *     boolean     trustedFinal            (only on JDK 17 and later)
     *     StringIndex signature
     *     byte[]      annotationsEncoding
     *     byte[]      typeAnnotationsEncoding
     *     int         offset
     *     StringIndex deletedReason
     * }
     * </pre>
     *
     * Heap field encoding.
     *
     * <pre>
     * HeapFieldMetadata : FieldMetadata {
     *     int         modifiers   (including IN_HEAP flag)
     *     ObjectIndex fieldObject
     * }
     * </pre>
     *
     * Hiding field encoding.
     *
     * <pre>
     * HidingFieldMetadata : FieldMetadata {
     *     int         modifiers (including HIDING flag)
     *     MembNameIdx name
     *     ClassIndex  type
     * }
     * </pre>
     *
     * Reachable field encoding.
     *
     * <pre>
     * ReachableFieldEncoding : FieldMetadata {
     *     int         modifiers (including EXISTS flag)
     *     MembNameIdx name
     * }
     * </pre>
     *
     * Negative query field encoding.
     *
     * <pre>
     * NegativeQueryFieldEncoding : FieldMetadata {
     *     int         modifiers (always zero)
     *     MembNameIdx name
     * }
     * </pre>
     */
    private static Object decodeField(UnsafeArrayTypeReader buf, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly) {
        int modifiers = buf.getUVInt();
        boolean inHeap = (modifiers & IN_HEAP_FLAG_MASK) != 0;
        boolean complete = (modifiers & COMPLETE_FLAG_MASK) != 0;

        RuntimeConditionSet conditions = decodeConditions(buf);
        if (inHeap) {
            Field field = (Field) decodeObject(buf);
            if (publicOnly && !Modifier.isPublic(field.getModifiers())) {
                /*
                 * Generate negative copy of the field. Finding a non-public field when looking for
                 * a public one should not result in a missing registration exception.
                 */
                return ReflectionObjectFactory.newField(conditions, declaringClass, field.getName(), Object.class, field.getModifiers() | NEGATIVE_FLAG_MASK, false,
                                null, null, ReflectionObjectFactory.FIELD_OFFSET_NONE, null, null);
            }
            if (reflectOnly) {
                return complete ? field : null;
            } else {
                return new FieldDescriptor(field);
            }
        }
        boolean hiding = (modifiers & HIDING_FLAG_MASK) != 0;
        assert !(complete && hiding);
        boolean negative = (modifiers & NEGATIVE_FLAG_MASK) != 0;
        assert !(negative && (complete || hiding));
        modifiers &= ~COMPLETE_FLAG_MASK;

        String name = decodeMemberName(buf);
        Class<?> type = (complete || hiding) ? decodeType(buf) : null;
        if (!complete) {
            if (reflectOnly != (hiding || negative)) {
                /*
                 * When querying for reflection fields, we want the hiding fields and negative
                 * queries but not the reachable fields. When querying for reachable fields, we want
                 * the reachable fields but not the hiding fields and negative queries.
                 */
                return null;
            }
            if (!reflectOnly) {
                return new FieldDescriptor(declaringClass, name);
            }
            return ReflectionObjectFactory.newField(conditions, declaringClass, name, negative ? Object.class : type, modifiers, false, null, null, ReflectionObjectFactory.FIELD_OFFSET_NONE, null,
                            null);
        }
        boolean trustedFinal = buf.getU1() == 1;
        String signature = decodeOtherString(buf);
        byte[] annotations = decodeByteArray(buf);
        byte[] typeAnnotations = decodeByteArray(buf);
        int offset = buf.getSVInt();
        String deletedReason = decodeOtherString(buf);
        if (publicOnly && !Modifier.isPublic(modifiers)) {
            modifiers |= NEGATIVE_FLAG_MASK;
        }

        Field reflectField = ReflectionObjectFactory.newField(conditions, declaringClass, name, type, modifiers, trustedFinal, signature, annotations, offset, deletedReason, typeAnnotations);
        return reflectOnly ? reflectField : new FieldDescriptor(reflectField);
    }

    private static RuntimeConditionSet decodeConditions(UnsafeArrayTypeReader buf) {
        var conditionTypes = decodeArray(buf, Class.class, i -> decodeType(buf));
        return RuntimeConditionSet.createDecoded(conditionTypes);
    }

    /**
     * Complete method encoding.
     *
     * <pre>
     * CompleteMethodMetadata : MethodMetadata {
     *     int          modifiers                    (including COMPLETE flag)
     *     MembNameIdx  name
     *     ClassIndex[] parameterTypes
     *     ClassIndex   returnType
     *     ClassIndex[] exceptionTypes
     *     StringIndex  signature
     *     byte[]       annotationsEncoding
     *     byte[]       parameterAnnotationsEncoding
     *     byte[]       annotationDefaultEncoding    (annotation methods only)
     *     byte[]       typeAnnotationsEncoding
     *     byte[]       reflectParametersEncoding    ({@link #decodeReflectParameter(UnsafeArrayTypeReader, Executable, int)})
     *     ObjectIndex  accessor                     (null if registered as queried only)
     * }
     * </pre>
     *
     * Heap method encoding.
     *
     * <pre>
     * HeapMethodMetadata : MethodMetadata {
     *     int         modifiers    (including IN_HEAP flag)
     *     ObjectIndex methodObject
     * }
     * </pre>
     *
     * Hiding method encoding.
     *
     * <pre>
     * HidingMethodMetadata : MethodMetadata {
     *     int          modifiers      (including HIDING flag)
     *     MembNameIdx  name
     *     ClassIndex[] parameterTypes
     *     ClassIndex   returnType
     * }
     * </pre>
     *
     * Reachable method encoding.
     *
     * <pre>
     * ReachableMethodMetadata : MethodMetadata {
     *     int           modifiers      (including EXISTS flag)
     *     MembNameIdx   name
     *     StringIndex[] parameterTypes
     * }
     * </pre>
     *
     * Negative query method encoding.
     *
     * <pre>
     * NegativeQueryMethodMetadata : MethodMetadata {
     *     int          modifiers      (always zero)
     *     MembNameIdx  name
     *     ClassIndex[] parameterTypes
     * }
     * </pre>
     *
     * Complete constructor encoding.
     *
     * <pre>
     * CompleteConstructorMetadata : ConstructorMetadata {
     *     int          modifiers                    (including COMPLETE flag)
     *     ClassIndex[] parameterTypes
     *     ClassIndex[] exceptionTypes
     *     StringIndex  signature
     *     byte[]       annotationsEncoding
     *     byte[]       parameterAnnotationsEncoding
     *     byte[]       typeAnnotationsEncoding
     *     byte[]       reflectParametersEncoding    ({@link #parseReflectParameters(Executable, byte[])})
     *     ObjectIndex  accessor                     (null if registered as queried only)
     * }
     * </pre>
     *
     * Heap constructor encoding.
     *
     * <pre>
     * HeapConstructorMetadata : ConstructorMetadata {
     *     int         modifiers         (including IN_HEAP flag)
     *     ObjectIndex constructorObject
     * }
     * </pre>
     *
     * Reachable constructor encoding.
     *
     * <pre>
     * ReachableConstructorMetadata : ConstructorMetadata {
     *     int          modifiers      (including EXISTS flag)
     *     ClassIndex[] parameterTypes
     * }
     * </pre>
     *
     * Negative query constructor encoding.
     *
     * <pre>
     * NegativeQueryConstructorMetadata : ConstructorMetadata {
     *     int          modifiers      (always zero)
     *     ClassIndex[] parameterTypes
     * }
     * </pre>
     */
    private static Object decodeExecutable(UnsafeArrayTypeReader buf, Class<?> declaringClass, boolean publicOnly, boolean reflectOnly, boolean isMethod) {
        int modifiers = buf.getUVInt();
        boolean inHeap = (modifiers & IN_HEAP_FLAG_MASK) != 0;
        boolean complete = (modifiers & COMPLETE_FLAG_MASK) != 0;
        RuntimeConditionSet conditions = decodeConditions(buf);
        if (inHeap) {
            Executable executable = (Executable) decodeObject(buf);
            if (publicOnly && !Modifier.isPublic(executable.getModifiers())) {
                /*
                 * Generate negative copy of the executable. Finding a non-public method when
                 * looking for a public one should not result in a missing registration exception.
                 */
                if (isMethod) {
                    executable = ReflectionObjectFactory.newMethod(conditions, declaringClass, executable.getName(), executable.getParameterTypes(), Object.class, null, modifiers | NEGATIVE_FLAG_MASK,
                                    null, null, null, null, null, null, null);
                } else {
                    executable = ReflectionObjectFactory.newConstructor(conditions, declaringClass, executable.getParameterTypes(), null, modifiers | NEGATIVE_FLAG_MASK, null, null, null, null, null,
                                    null);
                }
            }
            if (reflectOnly) {
                return complete ? executable : null;
            } else {
                if (isMethod) {
                    Method method = (Method) executable;
                    return new MethodDescriptor(method);
                } else {
                    Constructor<?> constructor = (Constructor<?>) executable;
                    return new ConstructorDescriptor(constructor);
                }
            }
        }
        boolean hiding = (modifiers & HIDING_FLAG_MASK) != 0;
        assert !(complete && hiding);
        boolean negative = (modifiers & NEGATIVE_FLAG_MASK) != 0;
        assert !(negative && (complete || hiding));
        modifiers &= ~COMPLETE_FLAG_MASK;

        String name = isMethod ? decodeMemberName(buf) : null;
        Object[] parameterTypes;
        if (complete || hiding || negative) {
            parameterTypes = decodeArray(buf, Class.class, (i) -> decodeType(buf));
        } else {
            parameterTypes = decodeArray(buf, String.class, (i) -> decodeOtherString(buf));
        }
        Class<?> returnType = isMethod && (complete || hiding) ? decodeType(buf) : null;
        if (!complete) {
            if (reflectOnly != (hiding || negative)) {
                /*
                 * When querying for reflection methods, we want the hiding methods but not the
                 * reachable methods. When querying for reachable methods, we want the reachable
                 * methods but not the hiding methods.
                 */
                return null;
            }
            if (isMethod) {
                if (!reflectOnly) {
                    return new MethodDescriptor(declaringClass, name, (String[]) parameterTypes);
                }
                return ReflectionObjectFactory.newMethod(conditions, declaringClass, name, (Class<?>[]) parameterTypes, negative ? Object.class : returnType, null, modifiers,
                                null, null, null, null, null, null, null);
            } else {
                if (!reflectOnly) {
                    return new ConstructorDescriptor(declaringClass, (String[]) parameterTypes);
                }
                return ReflectionObjectFactory.newConstructor(conditions, declaringClass, (Class<?>[]) parameterTypes, null, modifiers, null, null, null, null, null, null);
            }
        }
        Class<?>[] exceptionTypes = decodeArray(buf, Class.class, (i) -> decodeType(buf));
        String signature = decodeOtherString(buf);
        byte[] annotations = decodeByteArray(buf);
        byte[] parameterAnnotations = decodeByteArray(buf);
        byte[] annotationDefault = isMethod && declaringClass.isAnnotation() ? decodeByteArray(buf) : null;
        byte[] typeAnnotations = decodeByteArray(buf);
        byte[] reflectParameters = decodeByteArray(buf);
        Object accessor = decodeObject(buf);
        if (publicOnly && !Modifier.isPublic(modifiers)) {
            modifiers |= NEGATIVE_FLAG_MASK;
        }

        Target_java_lang_reflect_Executable executable;
        if (isMethod) {
            Method method = ReflectionObjectFactory.newMethod(conditions, declaringClass, name, (Class<?>[]) parameterTypes, returnType, exceptionTypes, modifiers,
                            signature, annotations, parameterAnnotations, annotationDefault, accessor, reflectParameters, typeAnnotations);
            if (!reflectOnly) {
                return new MethodDescriptor(method);
            }
            executable = SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class);
        } else {
            Constructor<?> constructor = ReflectionObjectFactory.newConstructor(conditions, declaringClass, (Class<?>[]) parameterTypes, exceptionTypes,
                            modifiers, signature, annotations, parameterAnnotations, accessor, reflectParameters, typeAnnotations);
            if (!reflectOnly) {
                return new ConstructorDescriptor(constructor);
            }
            executable = SubstrateUtil.cast(constructor, Target_java_lang_reflect_Executable.class);
        }
        return SubstrateUtil.cast(executable, Executable.class);
    }

    /**
     * Record component encoding.
     *
     * <pre>
     * RecordComponentMetadata {
     *     MembNameIdx name
     *     ClassIndex  type
     *     StringIndex signature
     *     byte[]      annotations
     *     byte[]      typeAnnotations
     * }
     * </pre>
     */
    private static RecordComponent decodeRecordComponent(UnsafeArrayTypeReader buf, Class<?> declaringClass) {
        String name = decodeMemberName(buf);
        Class<?> type = decodeType(buf);
        String signature = decodeOtherString(buf);
        byte[] annotations = decodeByteArray(buf);
        byte[] typeAnnotations = decodeByteArray(buf);

        return ReflectionObjectFactory.newRecordComponent(declaringClass, name, type, signature, annotations, typeAnnotations);
    }

    /**
     * Parameter encoding for executables.
     *
     * <pre>
     * ParameterMetadata {
     *     StringIndex name
     *     int         modifiers
     * }
     * </pre>
     */

    private static Parameter decodeReflectParameter(UnsafeArrayTypeReader buf, Executable executable, int i) {
        String name = decodeOtherString(buf);
        int modifiers = buf.getUVInt();

        return ReflectionObjectFactory.newParameter(executable, i, name, modifiers);
    }

    /** Types are encoded as indices in the code info classes array. */
    private static Class<?> decodeType(UnsafeArrayTypeReader buf) {
        int classIndex = buf.getSVInt();
        if (classIndex == NO_METHOD_METADATA) {
            return null;
        }
        return MetadataAccessor.singleton().getClass(classIndex);
    }

    /** Names are encoded as indices in the code info member names array. */
    private static String decodeMemberName(UnsafeArrayTypeReader buf) {
        int nameIndex = buf.getSVInt();
        String name = MetadataAccessor.singleton().getMemberName(nameIndex);
        /* Interning the string to ensure JDK8 method search succeeds */
        return name == null ? null : name.intern();
    }

    /** Strings other than member names are encoded as indices in the code info strings array. */
    private static String decodeOtherString(UnsafeArrayTypeReader buf) {
        int nameIndex = buf.getSVInt();
        String name = MetadataAccessor.singleton().getOtherString(nameIndex);
        /* Interning the string to ensure JDK8 method search succeeds */
        return name == null ? null : name.intern();
    }

    /**
     * Objects (method accessors and reflection objects in the heap) are encoded as indices in the
     * code info object constants array.
     */
    private static Object decodeObject(UnsafeArrayTypeReader buf) {
        int objectIndex = buf.getSVInt();
        if (objectIndex == NULL_OBJECT) {
            return null;
        }
        return MetadataAccessor.singleton().getObject(objectIndex);
    }

    /**
     * Arrays are encoded by their length followed by the elements encoded one after the other.
     */
    @SuppressWarnings("unchecked")
    private static <T> T[] decodeArray(UnsafeArrayTypeReader buf, Class<T> elementType, Function<Integer, T> elementDecoder) {
        int length = buf.getSVInt();
        if (isErrorIndex(length)) {
            decodeAndThrowError(length);
        }
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
        if (length == NO_DATA) {
            return null;
        }
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) buf.getS1();
        }
        return result;
    }

    /**
     * Accesses metadata through {@link CodeInfo}.
     */
    @AutomaticallyRegisteredImageSingleton(value = MetadataAccessor.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    public static class MetadataAccessorImpl implements MetadataAccessor {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getObject(int index) {
            CodeInfo info = getCodeInfo();
            return (T) NonmovableArrays.getObject(CodeInfoAccess.getObjectConstants(info), index);
        }

        @Override
        public Class<?> getClass(int index) {
            CodeInfo info = getCodeInfo();
            return NonmovableArrays.getObject(CodeInfoAccess.getClasses(info), index);
        }

        @Override
        public String getMemberName(int index) {
            CodeInfo info = getCodeInfo();
            return NonmovableArrays.getObject(CodeInfoAccess.getMemberNames(info), index);
        }

        @Override
        public String getOtherString(int index) {
            CodeInfo info = getCodeInfo();
            return NonmovableArrays.getObject(CodeInfoAccess.getOtherStrings(info), index);
        }

        /** @see com.oracle.svm.core.code.RuntimeMetadataEncoding */
        private static CodeInfo getCodeInfo() {
            return CodeInfoTable.getFirstImageCodeInfo();
        }
    }
}
