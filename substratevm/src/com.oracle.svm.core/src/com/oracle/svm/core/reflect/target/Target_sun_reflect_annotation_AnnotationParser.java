/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.WithRuntimeClassLoading;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoderSupport;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.VMError;

import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * Substitutions in this class adapt {@link AnnotationParser} to the format produced by
 * {@code RuntimeMetadataEncoderImpl.encodeAnnotations()} and
 * {@code com.oracle.svm.hosted.code.AnnotationMetadataEncoder}.
 */
@TargetClass(AnnotationParser.class)
public final class Target_sun_reflect_annotation_AnnotationParser {

    @Alias
    @TargetElement(name = "parseAnnotation2", onlyWith = WithRuntimeClassLoading.class)
    private static native Annotation originalParseAnnotation2(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container,
                    boolean exceptionOnMissingAnnotationClass,
                    Class<? extends Annotation>[] selectAnnotationClasses);

    @Substitute
    @SuppressWarnings("unchecked")
    private static Annotation parseAnnotation2(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container,
                    boolean exceptionOnMissingAnnotationClass,
                    Class<? extends Annotation>[] selectAnnotationClasses) {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(container).isRuntimeLoaded()) {
            // Use standard format for runtime-loaded types
            return originalParseAnnotation2(buf, constPool, container, exceptionOnMissingAnnotationClass, selectAnnotationClasses);
        }
        int typeIndex = buf.getInt();
        if (typeIndex < 0) {
            if (typeIndex == -1) {
                throw RuntimeMetadataDecoderSupport.decodeAnnotationFormatError(buf, constPool);
            }
            throw new AnnotationFormatError("Annotations could not be parsed at image build time (typeIndex=" + typeIndex + ")");
        }
        Class<? extends Annotation> annotationClass;
        try {
            annotationClass = (Class<? extends Annotation>) RuntimeMetadataDecoderSupport.metadataAccessor().getClass(typeIndex, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
        } catch (Throwable e) {
            if (exceptionOnMissingAnnotationClass) {
                throw new TypeNotPresentException("[unknown]", e);
            }
            AnnotationParserHelper.skipAnnotation(buf, false);
            return null;
        }

        if (selectAnnotationClasses != null && !contains(selectAnnotationClasses, annotationClass)) {
            AnnotationParserHelper.skipAnnotation(buf, false);
            return null;
        }
        AnnotationType type;
        try {
            type = AnnotationType.getInstance(annotationClass);
        } catch (IllegalArgumentException e) {
            AnnotationParserHelper.skipAnnotation(buf, false);
            return null;
        }

        Map<String, Class<?>> memberTypes = type.memberTypes();
        Map<String, Object> memberValues = new LinkedHashMap<>(type.memberDefaults());

        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            int memberNameIndex = buf.getInt();
            String memberName = RuntimeMetadataDecoderSupport.metadataAccessor().getMemberName(memberNameIndex, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
            Class<?> memberType = memberTypes.get(memberName);

            if (memberType == null) {
                // Member is no longer present in annotation type; ignore it
                AnnotationParserHelper.skipMemberValue(buf);
            } else {
                Object value = parseMemberValue(memberType, buf, constPool, container);
                if (value instanceof Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy) {
                    ((Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy) value).setMember(type.members().get(memberName));
                }
                memberValues.put(memberName, value);
            }
        }
        return annotationForMap(annotationClass, memberValues);
    }

    @Alias
    private static native Object parseMemberValue(Class<?> memberType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container);

    @Alias
    @TargetElement(name = "parseClassValue", onlyWith = WithRuntimeClassLoading.class)
    private static native Object originalParseClassValue(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container);

    @Substitute
    static Object parseClassValue(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(container).isRuntimeLoaded()) {
            // Use standard format for runtime-loaded types
            return originalParseClassValue(buf, constPool, container);
        }
        int classIndex = buf.getInt();
        try {
            return RuntimeMetadataDecoderSupport.metadataAccessor().getClass(classIndex, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
        } catch (Throwable t) {
            throw VMError.shouldNotReachHereSubstitution(); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Alias
    @TargetElement(name = "parseEnumValue", onlyWith = WithRuntimeClassLoading.class)
    @SuppressWarnings("rawtypes")
    private static native Object originalParseEnumValue(Class<? extends Enum> enumType, ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container);

    @Substitute
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object parseEnumValue(Class<? extends Enum> enumType, ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(container).isRuntimeLoaded()) {
            // Use standard format for runtime-loaded types
            return originalParseEnumValue(enumType, buf, constPool, container);
        }
        int typeIndex = buf.getInt();
        int constNameIndex = buf.getInt();
        String constName = RuntimeMetadataDecoderSupport.metadataAccessor().getMemberName(constNameIndex, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));

        if (!enumType.isEnum() || enumType != RuntimeMetadataDecoderSupport.metadataAccessor().getClass(typeIndex, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool))) {
            Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy e = new Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy();
            e.constructor(enumType.getTypeName() + "." + constName);
            return e;
        }

        try {
            return Enum.valueOf(enumType, constName);
        } catch (IllegalArgumentException e) {
            return new EnumConstantNotPresentExceptionProxy((Class<? extends Enum<?>>) enumType, constName);
        }
    }

    @Alias
    @TargetElement(name = "parseConst", onlyWith = WithRuntimeClassLoading.class)
    private static native Object originalParseConst(int tag,
                    ByteBuffer buf, Target_jdk_internal_reflect_ConstantPool constPool);

    @Substitute
    private static Object parseConst(int tag,
                    ByteBuffer buf, Target_jdk_internal_reflect_ConstantPool constPool) {
        if (RuntimeClassLoading.isSupported() && constPool != null && constPool.isRuntimeLoaded()) {
            // Use standard format for runtime-loaded types
            return originalParseConst(tag, buf, constPool);
        }
        switch (tag) {
            case 'B':
                return buf.get();
            case 'C':
                return buf.getChar();
            case 'D':
                return buf.getDouble();
            case 'F':
                return buf.getFloat();
            case 'I':
                return buf.getInt();
            case 'J':
                return buf.getLong();
            case 'S':
                return buf.getShort();
            case 'Z':
                byte value = buf.get();
                assert value == 1 || value == 0;
                return value == 1;
            case 's':
                return RuntimeMetadataDecoderSupport.metadataAccessor().getOtherString(buf.getInt(), RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
            case 't': {
                String typeName = RuntimeMetadataDecoderSupport.metadataAccessor().getOtherString(buf.getInt(), RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
                return new TypeNotPresentExceptionProxy(typeName, RuntimeMetadataDecoderSupport.decodeAnnotationFormatError(buf, constPool));
            }
            case 'm': {
                String foundType = RuntimeMetadataDecoderSupport.metadataAccessor().getOtherString(buf.getInt(), RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
                Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy e = new Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy();
                e.constructor(foundType);
                return e;
            }
            case '!': {
                throw RuntimeMetadataDecoderSupport.decodeAnnotationFormatError(buf, constPool);
            }
            default:
                throw new AnnotationFormatError(
                                "Invalid member-value tag in annotation: " + tag);
        }
    }

    @Alias
    @TargetElement(name = "parseArray", onlyWith = WithRuntimeClassLoading.class)
    private static native Object originalParseArray(Class<?> arrayType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container);

    @Substitute
    @SuppressWarnings("unchecked")
    private static Object parseArray(Class<?> arrayType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        if (RuntimeClassLoading.isSupported() && DynamicHub.fromClass(container).isRuntimeLoaded()) {
            // Use standard format for runtime-loaded types
            return originalParseArray(arrayType, buf, constPool, container);
        }
        int length = buf.getShort() & 0xFFFF;  // Number of array components
        if (!arrayType.isArray()) {
            return AnnotationParserHelper.parseUnknownArray(length, buf);
        }
        Class<?> componentType = arrayType.getComponentType();

        if (componentType == byte.class) {
            return AnnotationParserHelper.parseByteArray(length, buf);
        } else if (componentType == char.class) {
            return AnnotationParserHelper.parseCharArray(length, buf);
        } else if (componentType == double.class) {
            return AnnotationParserHelper.parseDoubleArray(length, buf);
        } else if (componentType == float.class) {
            return AnnotationParserHelper.parseFloatArray(length, buf);
        } else if (componentType == int.class) {
            return AnnotationParserHelper.parseIntArray(length, buf);
        } else if (componentType == long.class) {
            return AnnotationParserHelper.parseLongArray(length, buf);
        } else if (componentType == short.class) {
            return AnnotationParserHelper.parseShortArray(length, buf);
        } else if (componentType == boolean.class) {
            return AnnotationParserHelper.parseBooleanArray(length, buf);
        } else if (componentType == String.class) {
            return AnnotationParserHelper.parseStringArray(length, buf, constPool);
        } else if (componentType == Class.class) {
            return AnnotationParserHelper.parseClassArray(length, buf, constPool, container);
        } else if (componentType.isEnum()) {
            return AnnotationParserHelper.parseEnumArray(length, (Class<? extends Enum<?>>) componentType, buf,
                            constPool, container);
        } else if (componentType.isAnnotation()) {
            return AnnotationParserHelper.parseAnnotationArray(length, (Class<? extends Annotation>) componentType, buf,
                            constPool, container);
        } else {
            return AnnotationParserHelper.parseUnknownArray(length, buf);
        }
    }

    @Alias
    static native Annotation parseAnnotation(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container,
                    boolean exceptionOnMissingAnnotationClass);

    @Alias
    public static native Annotation annotationForMap(Class<? extends Annotation> type, Map<String, Object> memberValues);

    @Alias
    static native ExceptionProxy exceptionProxy(int tag);

    @Alias
    private static native boolean contains(Object[] array, Object element);
}

/**
 * Parts of AnnotationParser that can't be handled by substitutions because they lead to code paths
 * that can't distinguish between build-time and run-time loaded classes to select between the SVM
 * and the standard format (e.g., {@code skipMemberValue}).
 */
@BasedOnJDKClass(AnnotationParser.class)
final class AnnotationParserHelper {

    static Object parseClassArray(int length,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        return parseArrayElements(new Class<?>[length],
                        buf, 'c', () -> Target_sun_reflect_annotation_AnnotationParser.parseClassValue(buf, constPool, container));
    }

    static Object parseEnumArray(int length, Class<? extends Enum<?>> enumType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        return parseArrayElements((Object[]) Array.newInstance(enumType, length),
                        buf, 'e', () -> Target_sun_reflect_annotation_AnnotationParser.parseEnumValue(enumType, buf, constPool, container));
    }

    static Object parseAnnotationArray(int length,
                    Class<? extends Annotation> annotationType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container) {
        return parseArrayElements((Object[]) Array.newInstance(annotationType, length),
                        buf, '@', () -> Target_sun_reflect_annotation_AnnotationParser.parseAnnotation(buf, constPool, container, true));
    }

    private static Object parseArrayElements(Object[] result,
                    ByteBuffer buf,
                    int expectedTag,
                    Supplier<Object> parseElement) {
        Object exceptionProxy = null;
        for (int i = 0; i < result.length; i++) {
            int tag = buf.get();
            if (tag == expectedTag) {
                Object value = parseElement.get();
                if (value instanceof ExceptionProxy proxyValue) {
                    if (exceptionProxy == null) {
                        exceptionProxy = proxyValue;
                    }
                } else {
                    result[i] = value;
                }
            } else {
                skipMemberValue(tag, buf);
                if (exceptionProxy == null) {
                    exceptionProxy = Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag);
                }
            }
        }
        return (exceptionProxy != null) ? exceptionProxy : result;
    }

    static Object parseByteArray(int length, ByteBuffer buf) {
        byte[] result = new byte[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'B') {
                result[i] = buf.get();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseCharArray(int length, ByteBuffer buf) {
        char[] result = new char[length];
        boolean typeMismatch = false;
        byte tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'C') {
                result[i] = buf.getChar();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseDoubleArray(int length, ByteBuffer buf) {
        double[] result = new double[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'D') {
                result[i] = buf.getDouble();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseFloatArray(int length, ByteBuffer buf) {
        float[] result = new float[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'F') {
                result[i] = buf.getFloat();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseIntArray(int length, ByteBuffer buf) {
        int[] result = new int[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'I') {
                result[i] = buf.getInt();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseLongArray(int length, ByteBuffer buf) {
        long[] result = new long[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'J') {
                result[i] = buf.getLong();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseShortArray(int length, ByteBuffer buf) {
        short[] result = new short[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'S') {
                result[i] = buf.getShort();
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseBooleanArray(int length, ByteBuffer buf) {
        boolean[] result = new boolean[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 'Z') {
                byte value = buf.get();
                if (value != 0 && value != 1) {
                    skipMemberValue(tag, buf);
                    typeMismatch = true;
                }
                result[i] = value == 1;
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseStringArray(int length,
                    ByteBuffer buf, Target_jdk_internal_reflect_ConstantPool constPool) {
        String[] result = new String[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 's') {
                int index = buf.getInt();
                result[i] = RuntimeMetadataDecoderSupport.metadataAccessor().getOtherString(index, RuntimeMetadataDecoderSupport.getConstantPoolLayerId(constPool));
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag) : result;
    }

    static Object parseUnknownArray(int length,
                    ByteBuffer buf) {
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            skipMemberValue(tag, buf);
        }

        return Target_sun_reflect_annotation_AnnotationParser.exceptionProxy(tag);
    }

    static void skipAnnotation(ByteBuffer buf, boolean complete) {
        if (complete) {
            buf.getInt();   // Skip type index
        }
        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            buf.getInt();   // Skip memberNameIndex
            skipMemberValue(buf);
        }
    }

    static void skipMemberValue(ByteBuffer buf) {
        int tag = buf.get();
        skipMemberValue(tag, buf);
    }

    private static void skipMemberValue(int tag, ByteBuffer buf) {
        switch (tag) {
            case 'e': // Enum value
                buf.getLong();  // (Two ints, actually.)
                break;
            case '@':
                skipAnnotation(buf, true);
                break;
            case '[':
                skipArray(buf);
                break;
            case 'c':
            case 's':
                // Class, or String
                buf.getInt();
                break;
            default:
                // primitive
                switch (tag) {
                    case 'Z':
                    case 'B':
                        buf.get();
                        break;
                    case 'S':
                    case 'C':
                        buf.getShort();
                        break;
                    case 'I':
                    case 'F':
                        buf.getInt();
                        break;
                    case 'J':
                    case 'D':
                        buf.getLong();
                        break;
                }
        }
    }

    private static void skipArray(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        for (int i = 0; i < length; i++) {
            skipMemberValue(buf);
        }
    }
}

@TargetClass(className = "sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy")
final class Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy {
    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void constructor(String foundType);

    @Alias
    native Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy setMember(Method member);
}
