/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.annotation.AnnotationFormatError;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.core.code.CodeInfoEncoder.Encoders;
import com.oracle.svm.core.reflect.target.Target_sun_reflect_annotation_AnnotationParser;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Encodes {@link AnnotationValue}s into a format dictated by
 * {@link sun.reflect.annotation.AnnotationParser} in conjunction
 * {@link Target_sun_reflect_annotation_AnnotationParser}.
 */
public final class AnnotationMetadataEncoder {

    /**
     * Encodes {@code value} into {@code buf} in a format decoded by
     * {@code Target_sun_reflect_annotation_AnnotationParser#parseAnnotation2}.
     */
    public static void encodeAnnotation(UnsafeArrayTypeWriter buf, AnnotationValue value, Encoders encoders) {
        if (value.isError()) {
            buf.putS4(-1);
            buf.putS4(encoders.otherStrings.getIndex(getMessageWithStackTrace(value.getError(), false)));
            return;
        }
        Class<?> annotationType = OriginalClassProvider.getJavaClass(value.getAnnotationType());
        buf.putS4(encoders.classes.getIndex(annotationType));
        buf.putU2(value.getElements().size());
        value.getElements().forEach((memberName, memberValue) -> {
            buf.putS4(encoders.memberNames.getIndex(memberName));
            encodeAnnotationMember(buf, memberValue, encoders);
        });
    }

    public static void registerTypeAnnotation(TypeAnnotationValue value, Encoders encoders) {
        registerAnnotation(value.getAnnotation(), encoders);
    }

    public static void registerAnnotation(AnnotationValue value, Encoders encoders) {
        if (value.isError()) {
            encoders.otherStrings.addObject(getMessageWithStackTrace(value.getError(), false));
            return;
        }
        Class<?> annotationType = OriginalClassProvider.getJavaClass(value.getAnnotationType());
        encoders.classes.addObject(annotationType);
        value.getElements().forEach((memberName, memberValue) -> {
            encoders.memberNames.addObject(memberName);
            registerAnnotationMember(memberValue, encoders);
        });
    }

    public static void registerAnnotationMember(Object memberValue, Encoders encoders) {
        if (memberValue instanceof AnnotationValue) {
            registerAnnotation((AnnotationValue) memberValue, encoders);
        } else if (memberValue instanceof ResolvedJavaType type) {
            encoders.classes.addObject(OriginalClassProvider.getJavaClass(type));
        } else if (memberValue instanceof EnumElement el) {
            encoders.classes.addObject(OriginalClassProvider.getJavaClass(el.enumType));
            encoders.memberNames.addObject(el.name);
        } else if (memberValue instanceof String s) {
            encoders.otherStrings.addObject(s);
        } else if (memberValue instanceof List<?> list) {
            list.forEach(element -> registerAnnotationMember(element, encoders));
        } else if (memberValue instanceof MissingType mt) {
            Throwable cause = mt.getCause();
            encoders.otherStrings.addObject(mt.getTypeName());
            encoders.otherStrings.addObject(cause == null ? null : getMessageWithStackTrace(cause, true));
        } else if (memberValue instanceof ElementTypeMismatch etm) {
            encoders.otherStrings.addObject(etm.getFoundType());
        } else if (memberValue instanceof AnnotationFormatError e) {
            encoders.otherStrings.addObject(getMessageWithStackTrace(e, false));
        } else if (!(memberValue instanceof Number) && !(memberValue instanceof Boolean) && !(memberValue instanceof Character)) {
            throw GraalError.shouldNotReachHere("Invalid annotation member type: " + memberValue.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static void encodeAnnotationMember(UnsafeArrayTypeWriter buf, Object memberValue, Encoders encoders) {
        switch (memberValue) {
            case AnnotationValue av -> {
                // Decoded by AnnotationParser#parseAnnotation
                buf.putU1('@');
                encodeAnnotation(buf, av, encoders);
            }
            case ResolvedJavaType type -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseClassValue
                buf.putU1('c');
                buf.putS4(encoders.classes.getIndex(OriginalClassProvider.getJavaClass(type)));
            }
            case EnumElement ee -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseEnumValue
                buf.putU1('e');
                buf.putS4(encoders.classes.getIndex(OriginalClassProvider.getJavaClass(ee.enumType)));
                buf.putS4(encoders.memberNames.getIndex(ee.name));
            }
            case String s -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('s');
                buf.putS4(encoders.otherStrings.getIndex(s));
            }
            case Byte b -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('B');
                buf.putS1(b);
            }
            case Character c -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('C');
                buf.putU2(c);
            }
            case Double d -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('D');
                buf.putS8(Double.doubleToRawLongBits(d));
            }
            case Float f -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('F');
                buf.putS4(Float.floatToRawIntBits(f));
            }
            case Integer i -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('I');
                buf.putS4(i);
            }
            case Long l -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('J');
                buf.putS8(l);
            }
            case Short s -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('S');
                buf.putS2(s);
            }
            case Boolean b -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('Z');
                buf.putU1(b ? 1 : 0);
            }
            case List<?> list -> {
                // Decoded by AnnotationParser#parseArray
                buf.putU1('[');
                buf.putU2(list.size());
                list.forEach(element -> encodeAnnotationMember(buf, element, encoders));
            }
            case MissingType mt -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                Throwable cause = mt.getCause();
                buf.putU1('t');
                buf.putS4(encoders.otherStrings.getIndex(mt.getTypeName()));
                buf.putS4(encoders.otherStrings.getIndex(cause == null ? null : getMessageWithStackTrace(cause, true)));
            }
            case ElementTypeMismatch etm -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('m');
                buf.putS4(encoders.otherStrings.getIndex(etm.getFoundType()));
            }
            case AnnotationFormatError e -> {
                // Decoded by Target_sun_reflect_annotation_AnnotationParser#parseConst
                buf.putU1('!');
                buf.putS4(encoders.otherStrings.getIndex(getMessageWithStackTrace(e, false)));
            }
            default ->
                throw GraalError.shouldNotReachHere("Invalid annotation member type: " + memberValue.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Gets a string joining {@code error}'s message with its stack trace.
     *
     * @param withClassName specifies whether to prefix the return value with the name of
     *            {@code error}'s class (as {@link Throwable#toString() does})
     */
    private static String getMessageWithStackTrace(Throwable error, boolean withClassName) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        error.printStackTrace(new PrintStream(buf));
        String prefix = error.getClass().getName() + ": ";
        String res = buf.toString();
        if (!withClassName && res.startsWith(prefix)) {
            res = res.substring(prefix.length());
        }
        String nl = System.lineSeparator();
        String[] lines = res.split(nl);
        if (lines.length > 1 && lines[1].startsWith("\tat ")) {
            return lines[0] + nl +
                            "\t<< start build time stack trace >>" + nl +
                            String.join(nl, Arrays.asList(lines).subList(1, lines.length)) + nl +
                            "\t<< end build time stack trace >>";
        }
        return res;
    }

    /**
     * Encodes {@code value} into {@code buf} in a format decoded by
     * {@code TypeAnnotationParser#parseTypeAnnotation}.
     */
    public static void encodeTypeAnnotation(UnsafeArrayTypeWriter buf, TypeAnnotationValue value, Encoders encoders) {
        encodeTargetInfo(buf, value);
        encodeLocationInfo(buf, value);
        encodeAnnotation(buf, value.getAnnotation(), encoders);
    }

    /**
     * Encodes {@code ti} into {@code buf} in a format decoded by
     * {@code Target_sun_reflect_annotation_TypeAnnotationParser.parseTargetInfo}.
     */
    private static void encodeTargetInfo(UnsafeArrayTypeWriter buf, TypeAnnotationValue value) {
        for (byte b : value.getTargetInfo()) {
            buf.putS1(b);
        }
    }

    /**
     * Encodes {@code li} into {@code buf} in a format decoded by
     * {@code TypeAnnotation.LocationInfo#parseLocationInfo(ByteBuffer)}.
     */
    private static void encodeLocationInfo(UnsafeArrayTypeWriter buf, TypeAnnotationValue value) {
        for (byte b : value.getTypePath()) {
            buf.putS1(b);
        }
    }

    public static <T> void encodeArray(UnsafeArrayTypeWriter buf, T[] elements, Consumer<T> encoder) {
        encodeCollection(buf, Arrays.asList(elements), encoder);
    }

    public static <T> void encodeCollection(UnsafeArrayTypeWriter buf, Collection<T> elements, Consumer<T> encoder) {
        buf.putU2(elements.size());
        for (T element : elements) {
            encoder.accept(element);
        }
    }

    private AnnotationMetadataEncoder() {
    }
}
