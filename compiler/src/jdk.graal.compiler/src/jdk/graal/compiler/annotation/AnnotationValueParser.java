/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation;

import java.lang.reflect.GenericSignatureFormatError;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Parser for class file annotations into {@link AnnotationValue} (and related) objects.
 * <p>
 * This is a cut down version of {@code sun.reflect.annotation.AnnotationParser}. In addition to
 * different types instantiated by the parser, it also avoids triggering class initialization for
 * parsed enum values.
 */
public class AnnotationValueParser {
    public static Map<ResolvedJavaType, AnnotationValue> parseAnnotations(
                    byte[] rawAnnotations,
                    ConstantPool constPool,
                    ResolvedJavaType container) {
        Map<ResolvedJavaType, AnnotationValue> result = new EconomicHashMap<>();
        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        int numAnnotations = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numAnnotations; i++) {
            AnnotationValue a = parseAnnotation(buf, constPool, container, false);
            if (a != null) {
                result.put(a.getAnnotationType(), a);
            }
        }
        return result;
    }

    public static List<List<AnnotationValue>> parseParameterAnnotations(
                    byte[] rawAnnotations,
                    ConstantPool constPool,
                    ResolvedJavaType container) {
        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        int numParameters = buf.get() & 0xFF;
        List<List<AnnotationValue>> result = new ArrayList<>(numParameters);

        for (int i = 0; i < numParameters; i++) {
            int numAnnotations = buf.getShort() & 0xFFFF;
            List<AnnotationValue> annotations = new ArrayList<>(numAnnotations);
            for (int j = 0; j < numAnnotations; j++) {
                AnnotationValue a = parseAnnotation(buf, constPool, container, false);
                if (a != null) {
                    annotations.add(a);
                }
            }
            result.add(annotations);
        }
        return result;
    }

    static AnnotationValue parseAnnotation(ByteBuffer buf,
                    ConstantPool constPool,
                    ResolvedJavaType container,
                    boolean exceptionOnMissingAnnotationClass) {
        int typeIndex = buf.getShort() & 0xFFFF;
        ResolvedJavaType annotationClass;
        String sig = "[unknown]";
        try {
            sig = getUtf8At(constPool, typeIndex);
            annotationClass = parseSig(sig, container);
        } catch (NoClassDefFoundError e) {
            if (exceptionOnMissingAnnotationClass) {
                throw new TypeNotPresentException(sig, e);
            }
            skipAnnotation(buf, false);
            return null;
        }
        AnnotationValueType type;
        try {
            type = AnnotationValueType.getInstance(annotationClass);
        } catch (IllegalArgumentException e) {
            skipAnnotation(buf, false);
            return null;
        }

        Map<String, ResolvedJavaType> memberTypes = type.memberTypes();
        Map<String, Object> memberValues = new EconomicHashMap<>(type.memberDefaults());

        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            int memberNameIndex = buf.getShort() & 0xFFFF;
            String memberName = getUtf8At(constPool, memberNameIndex);
            ResolvedJavaType memberType = memberTypes.get(memberName);

            if (memberType == null) {
                // Member is no longer present in annotation type; ignore it
                skipMemberValue(buf);
            } else {
                Object value = parseMemberValue(memberType, buf, constPool, container);
                memberValues.put(memberName, value);
            }
        }
        return new AnnotationValue(annotationClass, memberValues);
    }

    public static Object parseMemberValue(ResolvedJavaType memberType,
                    ByteBuffer buf,
                    ConstantPool constPool,
                    ResolvedJavaType container) {
        char tag = (char) buf.get();
        Object result = switch (tag) {
            case 'e' -> parseEnumValue(memberType, buf, constPool);
            case 'c' -> parseClassValue(buf, constPool, container);
            case 's' -> getUtf8At(constPool, buf.getShort() & 0xFFFF);
            case '@' -> parseAnnotation(buf, constPool, container, true);
            case '[' -> parseArray(memberType, buf, constPool, container);
            default -> parsePrimitiveConst(tag, buf, constPool);
        };
        if (result == null) {
            result = new ElementTypeMismatch(memberType.toJavaName());
        } else if (tag != '[' &&
                        !(result instanceof ErrorElement) &&
                        !matchesMemberType(result, memberType)) {
            if (result instanceof AnnotationValue) {
                result = new ElementTypeMismatch(result.toString());
            } else {
                result = new ElementTypeMismatch(result.getClass().getName() + "[" + result + "]");
            }
        }
        return result;
    }

    private static boolean matchesMemberType(Object result, ResolvedJavaType memberType) {
        if (result instanceof AnnotationValue av) {
            return memberType.equals(av.getAnnotationType());
        }
        if (result instanceof ResolvedJavaType) {
            return memberType.getName().equals("Ljava/lang/Class;");
        }
        if (result instanceof EnumElement ee) {
            return ee.enumType.equals(memberType);
        }
        if (memberType.isPrimitive()) {
            return result.getClass() == memberType.getJavaKind().toBoxedJavaClass();
        }
        return memberType.toJavaName().equals(result.getClass().getName());
    }

    private static Object parsePrimitiveConst(char tag,
                    ByteBuffer buf, ConstantPool constPool) {
        int constIndex = buf.getShort() & 0xFFFF;
        JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(tag);
        return getPrimitiveConstAt(kind, constPool, constIndex);
    }

    private static Object parseClassValue(ByteBuffer buf,
                    ConstantPool constPool,
                    ResolvedJavaType container) {
        int classIndex = buf.getShort() & 0xFFFF;
        String sig = getUtf8At(constPool, classIndex);
        try {
            return parseSig(sig, container);
        } catch (NoClassDefFoundError e) {
            String javaName = MetaUtil.internalNameToJava(sig, true, true);
            return new MissingType(javaName, e);
        }
    }

    private static final Pattern VALID_SIG_PATTERN = Pattern.compile("\\[*([BCDFIJSZ]|L(?<ClassName>[^;]+);)");

    private static boolean isValidClassName(String name) {
        return !name.isEmpty() && name.charAt(0) != '/' && name.charAt(name.length() - 1) != '/' && !name.contains("//");
    }

    private static void checkSig(String sig) {
        Matcher m = VALID_SIG_PATTERN.matcher(sig);
        if (m.matches()) {
            String className = m.group("ClassName");
            if (className == null || isValidClassName(className)) {
                return;
            }
        }
        throw new GenericSignatureFormatError(sig);
    }

    private static ResolvedJavaType parseSig(String sig, ResolvedJavaType container) {
        if (!sig.equals("V")) {
            checkSig(sig);
        }
        try {
            return UnresolvedJavaType.create(sig).resolve(container);
        } catch (Error e) {
            if (LibGraalSupport.inLibGraalRuntime()) {
                String s = e.toString();
                if (s.contains("NoClassDefFoundError") && s.contains(sig)) {
                    // Workaround for JDK that does not properly translate
                    // a NoClassDefFoundError from HotSpot into libgraal
                    throw (NoClassDefFoundError) new NoClassDefFoundError(sig).initCause(e);
                }
            }
            throw e;
        }
    }

    static Object parseEnumValue(ResolvedJavaType enumType, ByteBuffer buf, ConstantPool constPool) {
        String typeName = getUtf8At(constPool, buf.getShort() & 0xFFFF);
        String constName = getUtf8At(constPool, buf.getShort() & 0xFFFF);
        if (!enumType.isEnum() || !enumType.getName().equals(typeName)) {
            String javaName = MetaUtil.internalNameToJava(typeName, true, true);
            return new ElementTypeMismatch(javaName + "." + constName);
        }
        return new EnumElement(enumType, constName);
    }

    private static Object parseArray(ResolvedJavaType arrayType,
                    ByteBuffer buf,
                    ConstantPool constPool,
                    ResolvedJavaType container) {
        int length = buf.getShort() & 0xFFFF;
        if (!arrayType.isArray()) {
            return parseUnknownArray(length, buf);
        }
        ResolvedJavaType componentType = arrayType.getComponentType();
        if (componentType.isPrimitive()) {
            char typeChar = componentType.getName().charAt(0);
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(typeChar);
            return parseConstArray(length, typeChar, kind, buf, constPool, container);
        } else if (componentType.getName().equals("Ljava/lang/String;")) {
            return parseConstArray(length, 's', JavaKind.Object, buf, constPool, container);
        } else if (componentType.getName().equals("Ljava/lang/Class;")) {
            return parseConstArray(length, 'c', JavaKind.Object, buf, constPool, container);
        } else if (componentType.isEnum()) {
            return parseArrayElements(length, buf, 'e', () -> parseEnumValue(componentType, buf, constPool));
        } else if (componentType.isAnnotation()) {
            return parseArrayElements(length, buf, '@', () -> parseAnnotation(buf, constPool, container, true));
        } else {
            return parseUnknownArray(length, buf);
        }
    }

    private static Object parseConstArray(int length, int expectTag, JavaKind kind,
                    ByteBuffer buf, ConstantPool constPool, ResolvedJavaType container) {
        Object[] result = new Object[length];
        int tag = 0;

        Object error = null;
        for (int i = 0; i < length; i++) {
            tag = buf.get();
            if (tag == expectTag) {
                int index = buf.getShort() & 0xFFFF;
                if (kind.isPrimitive()) {
                    result[i] = getPrimitiveConstAt(kind, constPool, index);
                } else {
                    String s = getUtf8At(constPool, index);
                    if (tag == 'c') {
                        try {
                            result[i] = parseSig(s, container);
                        } catch (NoClassDefFoundError e) {
                            if (error == null) {
                                String javaName = MetaUtil.internalNameToJava(s, true, true);
                                error = new MissingType(javaName, e);
                            }
                        }
                    } else {
                        result[i] = s;
                    }
                }
            } else {
                skipMemberValue(tag, buf);
                if (error == null) {
                    error = tagMismatch(tag);
                }
            }
        }
        return error != null ? error : List.of(result);
    }

    private static String getUtf8At(ConstantPool cp, int cpi) {
        try {
            return cp.lookupUtf8(cpi);
        } catch (IndexOutOfBoundsException e) {
            // Translate to IllegalArgumentException to match
            // jdk.internal.reflect.ConstantPool
            throw new IllegalArgumentException(e);
        }
    }

    private static Object getPrimitiveConstAt(JavaKind kind, ConstantPool cp, int cpi) {
        try {
            PrimitiveConstant o = (PrimitiveConstant) cp.lookupConstant(cpi);
            JavaKind stackKind = kind.getStackKind();
            if (o.getJavaKind() != stackKind) {
                throw new IllegalArgumentException("expected " + stackKind + ", got " + o.getJavaKind());
            }
            return JavaConstant.forPrimitive(kind, o.getRawValue()).asBoxedPrimitive();
        } catch (ClassCastException | IndexOutOfBoundsException e) {
            // Translate to IllegalArgumentException to match
            // jdk.internal.reflect.ConstantPool
            throw new IllegalArgumentException(e);
        }
    }

    private static Object parseArrayElements(int length,
                    ByteBuffer buf,
                    int expectedTag,
                    Supplier<Object> parseElement) {
        Object[] result = new Object[length];
        Object invalidTag = null;
        for (int i = 0; i < result.length; i++) {
            int tag = buf.get();
            if (tag == expectedTag) {
                Object value = parseElement.get();
                if (value instanceof ErrorElement ee) {
                    if (invalidTag == null) {
                        invalidTag = ee;
                    }
                } else {
                    result[i] = value;
                }
            } else {
                skipMemberValue(tag, buf);
                if (invalidTag == null) {
                    invalidTag = tagMismatch(tag);
                }
            }
        }
        return (invalidTag != null) ? invalidTag : List.of(result);
    }

    private static Object parseUnknownArray(int length,
                    ByteBuffer buf) {
        int tag = 0;

        for (int i = 0; i < length; i++) {
            tag = buf.get();
            skipMemberValue(tag, buf);
        }

        return tagMismatch(tag);
    }

    private static ElementTypeMismatch tagMismatch(int tag) {
        return new ElementTypeMismatch("Array with component tag: " + (tag == 0 ? "0" : (char) tag));
    }

    private static void skipAnnotation(ByteBuffer buf, boolean complete) {
        if (complete) {
            buf.getShort();
        }
        int numMembers = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMembers; i++) {
            buf.getShort();
            skipMemberValue(buf);
        }
    }

    private static void skipMemberValue(ByteBuffer buf) {
        int tag = buf.get();
        skipMemberValue(tag, buf);
    }

    private static void skipMemberValue(int tag, ByteBuffer buf) {
        switch (tag) {
            case 'e':
                buf.getInt();
                break;
            case '@':
                skipAnnotation(buf, true);
                break;
            case '[':
                skipArray(buf);
                break;
            default:
                buf.getShort();
        }
    }

    private static void skipArray(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        for (int i = 0; i < length; i++) {
            skipMemberValue(buf);
        }
    }
}
