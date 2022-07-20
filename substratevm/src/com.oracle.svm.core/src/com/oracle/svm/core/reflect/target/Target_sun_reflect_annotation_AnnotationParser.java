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
package com.oracle.svm.core.reflect.target;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.reflect.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.util.VMError;

import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;

/**
 * Substitutions in this class are required to adapt the JDK encoding for annotations to our
 * modified version of it. See {@code ReflectionMetadataEncoderImpl.encodeAnnotations()} for a
 * description of the changes and the rationale behind them.
 */
@TargetClass(AnnotationParser.class)
public final class Target_sun_reflect_annotation_AnnotationParser {

    @Substitute
    @SuppressWarnings("unchecked")
    private static Annotation parseAnnotation2(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container,
                    boolean exceptionOnMissingAnnotationClass,
                    Class<? extends Annotation>[] selectAnnotationClasses) {
        int typeIndex = buf.getInt();
        Class<? extends Annotation> annotationClass;
        try {
            annotationClass = (Class<? extends Annotation>) constPool.getClassAt(typeIndex);
        } catch (Throwable e) {
            if (exceptionOnMissingAnnotationClass) {
                throw new TypeNotPresentException("[unknown]", e);
            }
            skipAnnotation(buf, false);
            return null;
        }

        if (selectAnnotationClasses != null && !contains(selectAnnotationClasses, annotationClass)) {
            skipAnnotation(buf, false);
            return null;
        }
        AnnotationType type;
        try {
            type = AnnotationType.getInstance(annotationClass);
        } catch (IllegalArgumentException e) {
            skipAnnotation(buf, false);
            return null;
        }

        Map<String, Class<?>> memberTypes = type.memberTypes();
        Map<String, Object> memberValues = new LinkedHashMap<>(type.memberDefaults());

        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            int memberNameIndex = buf.getInt();
            String memberName = constPool.getUTF8At(memberNameIndex);
            Class<?> memberType = memberTypes.get(memberName);

            if (memberType == null) {
                // Member is no longer present in annotation type; ignore it
                skipMemberValue(buf);
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
    public static native Object parseMemberValue(Class<?> memberType,
                    ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    Class<?> container);

    @Substitute
    private static Object parseClassValue(ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    @SuppressWarnings("unused") Class<?> container) {
        int classIndex = buf.getInt();
        try {
            return constPool.getClassAt(classIndex);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere();
        }
    }

    @Substitute
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnumValue(Class<? extends Enum> enumType, ByteBuffer buf,
                    Target_jdk_internal_reflect_ConstantPool constPool,
                    @SuppressWarnings("unused") Class<?> container) {
        int typeIndex = buf.getInt();
        int constNameIndex = buf.getInt();
        String constName = constPool.getUTF8At(constNameIndex);

        if (!enumType.isEnum() || enumType != constPool.getClassAt(typeIndex)) {
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

    @Substitute
    private static Object parseConst(int tag,
                    ByteBuffer buf, Target_jdk_internal_reflect_ConstantPool constPool) {
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
                return constPool.getUTF8At(buf.getInt());
            case 'E':
                return NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoObjectConstants(CodeInfoTable.getImageCodeInfo()), buf.getInt());
            default:
                throw new AnnotationFormatError(
                                "Invalid member-value tag in annotation: " + tag);
        }
    }

    @Substitute
    private static Object parseByteArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseCharArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseDoubleArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseFloatArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseIntArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseLongArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseShortArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseBooleanArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
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
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static Object parseStringArray(int length,
                    ByteBuffer buf, @SuppressWarnings("unused") Target_jdk_internal_reflect_ConstantPool constPool) {
        String[] result = new String[length];
        boolean typeMismatch = false;
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == 's') {
                int index = buf.getInt();
                result[i] = constPool.getUTF8At(index);
            } else {
                skipMemberValue(tag, buf);
                typeMismatch = true;
            }
        }
        return typeMismatch ? exceptionProxy(tag) : result;
    }

    @Substitute
    private static void skipAnnotation(ByteBuffer buf, boolean complete) {
        if (complete) {
            buf.getInt();   // Skip type index
        }
        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            buf.getInt();   // Skip memberNameIndex
            skipMemberValue(buf);
        }
    }

    @Alias
    private static native void skipMemberValue(ByteBuffer buf);

    @Substitute
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

    @Alias
    private static native void skipArray(ByteBuffer buf);

    @Alias
    public static native Annotation annotationForMap(Class<? extends Annotation> type, Map<String, Object> memberValues);

    @Alias
    private static native ExceptionProxy exceptionProxy(int tag);

    @Alias
    private static native boolean contains(Object[] array, Object element);
}

@TargetClass(className = "sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy")
final class Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy {
    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void constructor(String foundType);

    @Alias
    native Target_sun_reflect_annotation_AnnotationTypeMismatchExceptionProxy setMember(Method member);
}
