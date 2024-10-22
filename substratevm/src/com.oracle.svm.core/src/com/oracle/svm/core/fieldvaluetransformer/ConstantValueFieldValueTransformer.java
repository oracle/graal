/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.fieldvaluetransformer;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Sets the field to the provided constant value.
 *
 * When that value is the {@link #defaultValueForField default value for the field}, this
 * transformer implements the field value transformation semantics of {@link Kind#Reset}.
 */
public record ConstantValueFieldValueTransformer(Object value) implements FieldValueTransformer {

    public static FieldValueTransformer defaultValueForField(ResolvedJavaField field) {
        return new ConstantValueFieldValueTransformer(switch (field.getType().getJavaKind()) {
            case Byte -> Byte.valueOf((byte) 0);
            case Boolean -> Boolean.valueOf(false);
            case Short -> Short.valueOf((short) 0);
            case Char -> Character.valueOf((char) 0);
            case Int -> Integer.valueOf(0);
            case Long -> Long.valueOf(0);
            case Float -> Float.valueOf(0);
            case Double -> Double.valueOf(0);
            case Object -> null;
            default -> throw VMError.shouldNotReachHere(String.valueOf(field));
        });
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        return value;
    }
}
