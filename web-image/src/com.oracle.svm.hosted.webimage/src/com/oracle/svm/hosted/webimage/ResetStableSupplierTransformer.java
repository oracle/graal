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

package com.oracle.svm.hosted.webimage;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.util.ReflectionUtil;

/**
 * Field value transformer that creates a new StableSupplier with the same {@link Supplier} as the
 * original that was not initialized yet.
 * <p>
 * This can be used to reset caches that are based on stable value suppliers.
 * <p>
 * Uses reflection because StableSupplier is from JDK25 and proguard does not yet support class
 * files from that version.
 */
public final class ResetStableSupplierTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        Class<?> stableSupplierClass = ReflectionUtil.lookupClass("jdk.internal.lang.stable.StableSupplier");
        Method stableSupplierOf = ReflectionUtil.lookupMethod(stableSupplierClass, "of", Supplier.class);
        Method stableSupplierOriginal = ReflectionUtil.lookupMethod(stableSupplierClass, "original");
        Object originalSupplier = ReflectionUtil.invokeMethod(stableSupplierOriginal, originalValue);
        return ReflectionUtil.invokeMethod(stableSupplierOf, null, originalSupplier);
    }
}
