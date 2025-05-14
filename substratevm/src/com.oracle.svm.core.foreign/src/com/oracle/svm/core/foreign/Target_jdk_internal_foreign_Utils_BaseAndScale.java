/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;

import jdk.internal.foreign.Utils;
import jdk.vm.ci.meta.JavaKind;

@TargetClass(className = "jdk.internal.foreign.Utils", innerClass = "BaseAndScale", onlyWith = ForeignAPIPredicates.Enabled.class)
final class Target_jdk_internal_foreign_Utils_BaseAndScale {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = BaseFieldRecomputer.class) //
    long base;
}

final class BaseFieldRecomputer implements FieldValueTransformer {

    @Override
    public Object transform(Object receiver, Object originalValue) {
        JavaKind kind;
        if (receiver == Utils.BaseAndScale.BYTE) {
            kind = JavaKind.Byte;
        } else if (receiver == Utils.BaseAndScale.CHAR) {
            kind = JavaKind.Char;
        } else if (receiver == Utils.BaseAndScale.SHORT) {
            kind = JavaKind.Short;
        } else if (receiver == Utils.BaseAndScale.INT) {
            kind = JavaKind.Int;
        } else if (receiver == Utils.BaseAndScale.LONG) {
            kind = JavaKind.Long;
        } else if (receiver == Utils.BaseAndScale.FLOAT) {
            kind = JavaKind.Float;
        } else if (receiver == Utils.BaseAndScale.DOUBLE) {
            kind = JavaKind.Double;
        } else {
            throw VMError.shouldNotReachHere("Unexpected BaseAndScale instance: " + receiver);
        }
        int offset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(kind);
        return (long) offset;
    }
}
