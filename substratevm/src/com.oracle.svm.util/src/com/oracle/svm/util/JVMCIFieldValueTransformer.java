/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import jdk.vm.ci.meta.JavaConstant;

/**
 * A {@linkplain FieldValueTransformer transformer for a field value} based on {@link JavaConstant}
 * that can be registered using
 * {@code BeforeAnalysisAccessImpl#registerFieldValueTransformer(ResolvedJavaField, JVMCIFieldValueTransformer)}.
 *
 * @see FieldValueTransformer
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface JVMCIFieldValueTransformer {

    /**
     * Transforms the field value for the provided receiver. The receiver is null for static fields.
     * The original value of the field, i.e., the hosted value of the field in the image generator,
     * is also provided as an argument.
     * <p>
     * The returned constant should never be {@code null}. Use {@link JavaConstant#NULL_POINTER}
     * instead. The type of the returned object must be assignable to the declared type of the
     * field. If the field has a primitive type, the returned object must not be a
     * {@linkplain JavaConstant#isNull() null constant}.
     * 
     * @see FieldValueTransformer#transform(Object, Object)
     */
    JavaConstant transform(JavaConstant receiver, JavaConstant originalValue);

    /**
     * Returns true when the value for this custom computation is available.
     * 
     * @see FieldValueTransformer#isAvailable()
     */
    default boolean isAvailable() {
        return true;
    }
}
