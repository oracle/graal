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
package com.oracle.svm.hosted.reflect;

import java.util.function.Consumer;

import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.core.code.CodeInfoEncoder.Encoders;
import com.oracle.svm.hosted.annotation.AnnotationArrayValue;
import com.oracle.svm.hosted.annotation.AnnotationClassValue;
import com.oracle.svm.hosted.annotation.AnnotationEnumValue;
import com.oracle.svm.hosted.annotation.AnnotationExceptionProxyValue;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationPrimitiveValue;
import com.oracle.svm.hosted.annotation.AnnotationStringValue;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;

public class AnnotationEncoder {
    static void encodeAnnotation(UnsafeArrayTypeWriter buf, AnnotationValue value, Encoders encoders) {
        buf.putS4(encoders.sourceClasses.getIndex(value.getType()));
        buf.putU2(value.getMemberCount());
        value.forEachMember((memberName, memberValue) -> {
            buf.putS4(encoders.sourceMethodNames.getIndex(memberName));
            encodeAnnotationMember(buf, memberValue, encoders);
        });
    }

    static void encodeAnnotationMember(UnsafeArrayTypeWriter buf, AnnotationMemberValue memberValue, Encoders encoders) {
        buf.putU1(memberValue.getTag());
        if (memberValue instanceof AnnotationValue) {
            encodeAnnotation(buf, (AnnotationValue) memberValue, encoders);
        } else if (memberValue instanceof AnnotationClassValue) {
            buf.putS4(encoders.sourceClasses.getIndex(((AnnotationClassValue) memberValue).getValue()));
        } else if (memberValue instanceof AnnotationEnumValue) {
            buf.putS4(encoders.sourceClasses.getIndex(((AnnotationEnumValue) memberValue).getType()));
            buf.putS4(encoders.sourceMethodNames.getIndex(((AnnotationEnumValue) memberValue).getName()));
        } else if (memberValue instanceof AnnotationStringValue) {
            buf.putS4(encoders.sourceMethodNames.getIndex(((AnnotationStringValue) memberValue).getValue()));
        } else if (memberValue instanceof AnnotationArrayValue) {
            buf.putU2(((AnnotationArrayValue) memberValue).getElementCount());
            ((AnnotationArrayValue) memberValue).forEachElement(element -> encodeAnnotationMember(buf, element, encoders));
        } else if (memberValue instanceof AnnotationPrimitiveValue) {
            Object value = ((AnnotationPrimitiveValue) memberValue).getValue();
            switch (memberValue.getTag()) {
                case 'B':
                    buf.putS1((byte) value);
                    return;
                case 'C':
                    buf.putU2((char) value);
                    return;
                case 'D':
                    buf.putS8(Double.doubleToRawLongBits((double) value));
                    return;
                case 'F':
                    buf.putS4(Float.floatToRawIntBits((float) value));
                    return;
                case 'I':
                    buf.putS4((int) value);
                    return;
                case 'J':
                    buf.putS8((long) value);
                    return;
                case 'S':
                    buf.putS2((short) value);
                    return;
                case 'Z':
                    buf.putU1((boolean) value ? 1 : 0);
                    return;
                default:
                    throw GraalError.shouldNotReachHere("Invalid annotation encoding");
            }
        } else if (memberValue instanceof AnnotationExceptionProxyValue) {
            buf.putS4(encoders.objectConstants.getIndex(((AnnotationExceptionProxyValue) memberValue).getObjectConstant()));
        } else {
            throw GraalError.shouldNotReachHere("Invalid annotation member type: " + memberValue.getClass());
        }
    }

    static void encodeTypeAnnotation(UnsafeArrayTypeWriter buf, TypeAnnotationValue value, Encoders encoders) {
        for (byte b : value.getTargetInfo()) {
            buf.putU1(b);
        }
        for (byte b : value.getLocationInfo()) {
            buf.putU1(b);
        }
        encodeAnnotation(buf, value.getAnnotationData(), encoders);
    }

    static <T> void encodeArray(UnsafeArrayTypeWriter buf, T[] elements, Consumer<T> encoder) {
        buf.putU2(elements.length);
        for (T element : elements) {
            encoder.accept(element);
        }
    }
}
