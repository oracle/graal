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

import java.lang.annotation.AnnotationFormatError;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Parser for class file type annotations that produces {@link TypeAnnotationValue} objects.
 * <p>
 * This is a cut down version of {@code sun.reflect.annotation.TypeAnnotationParser}.
 */
final class TypeAnnotationValueParser {

    public static List<TypeAnnotationValue> parseTypeAnnotations(byte[] rawAnnotations,
                    ConstantPool cp,
                    ResolvedJavaType container) {
        if (rawAnnotations == null) {
            return List.of();
        }

        ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
        int annotationCount = buf.getShort() & 0xFFFF;
        List<TypeAnnotationValue> typeAnnotations = new ArrayList<>(annotationCount);

        for (int i = 0; i < annotationCount; i++) {
            TypeAnnotationValue ta = parseTypeAnnotation(buf, cp, container);
            if (ta != null) {
                typeAnnotations.add(ta);
            }
        }

        return List.copyOf(typeAnnotations);
    }

    private static TypeAnnotationValue parseTypeAnnotation(ByteBuffer buf,
                    ConstantPool cp,
                    ResolvedJavaType container) {
        try {
            byte[] targetInfo = parseTargetInfo(buf);
            byte[] typePath = parseTypePath(buf);
            AnnotationValue a = AnnotationValueParser.parseAnnotation(buf, cp, container, false);
            // Ignore not well-formed type annotations like
            // TypeAnnotationParser.mapTypeAnnotations does
            if (a == null) {
                return null;
            }
            return new TypeAnnotationValue(targetInfo, typePath, a);
        } catch (IllegalArgumentException | BufferUnderflowException e) {
            throw new AnnotationFormatError(e);
        }
    }

    // Values copied from sun.reflect.annotation.TypeAnnotationParser
    // Regular type parameter annotations
    private static final byte CLASS_TYPE_PARAMETER = 0x00;
    private static final byte METHOD_TYPE_PARAMETER = 0x01;
    // Type Annotations outside method bodies
    private static final byte CLASS_EXTENDS = 0x10;
    private static final byte CLASS_TYPE_PARAMETER_BOUND = 0x11;
    private static final byte METHOD_TYPE_PARAMETER_BOUND = 0x12;
    /* private */ static final byte FIELD = 0x13;
    private static final byte METHOD_RETURN = 0x14;
    private static final byte METHOD_RECEIVER = 0x15;
    private static final byte METHOD_FORMAL_PARAMETER = 0x16;
    private static final byte THROWS = 0x17;
    // Type Annotations inside method bodies
    private static final byte LOCAL_VARIABLE = (byte) 0x40;
    private static final byte RESOURCE_VARIABLE = (byte) 0x41;
    private static final byte EXCEPTION_PARAMETER = (byte) 0x42;
    private static final byte INSTANCEOF = (byte) 0x43;
    private static final byte NEW = (byte) 0x44;
    private static final byte CONSTRUCTOR_REFERENCE = (byte) 0x45;
    private static final byte METHOD_REFERENCE = (byte) 0x46;
    private static final byte CAST = (byte) 0x47;
    private static final byte CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT = (byte) 0x48;
    private static final byte METHOD_INVOCATION_TYPE_ARGUMENT = (byte) 0x49;
    private static final byte CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT = (byte) 0x4A;
    private static final byte METHOD_REFERENCE_TYPE_ARGUMENT = (byte) 0x4B;

    private static byte[] parseTargetInfo(ByteBuffer buf) {
        int startPos = buf.position();
        int posCode = buf.get() & 0xFF;
        switch (posCode) {
            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
            case METHOD_FORMAL_PARAMETER:
            case EXCEPTION_PARAMETER: {
                buf.get();
                break;
            }
            case CLASS_EXTENDS:
            case THROWS:
            case INSTANCEOF:
            case NEW:
            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE: {
                buf.getShort();
                break;
            }
            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND: {
                buf.get();
                buf.get();
                break;
            }
            case FIELD:
            case METHOD_RETURN:
            case METHOD_RECEIVER: {
                break;
            }
            case LOCAL_VARIABLE:
            case RESOURCE_VARIABLE: {
                short length = buf.getShort();
                for (int i = 0; i < length; ++i) {
                    buf.getShort();
                    buf.getShort();
                    buf.getShort();
                }
                break;
            }
            case CAST:
            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT: {
                buf.getShort();
                buf.get();
                break;
            }
            default:
                throw new AnnotationFormatError("Could not parse bytes for type annotations");
        }
        int endPos = buf.position();
        byte[] targetInfo = new byte[endPos - startPos];
        buf.position(startPos).get(targetInfo).position(endPos);
        return targetInfo;
    }

    private static byte[] parseTypePath(ByteBuffer buf) {
        int startPos = buf.position();
        int depth = buf.get() & 0xFF;
        for (int i = 0; i < depth; i++) {
            buf.get();
            buf.get();
        }
        int endPos = buf.position();
        byte[] typePath = new byte[endPos - startPos];
        buf.position(startPos).get(typePath).position(endPos);
        return typePath;
    }
}
