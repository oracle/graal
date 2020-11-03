/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.methodhandles;

import java.lang.invoke.MethodHandle;
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.NativeImageOptions;

@AutomaticFeature
public class MethodHandleFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return NativeImageOptions.areMethodHandlesSupported();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler((duringAccess) -> {
            try {
                RuntimeReflection.register(MethodHandle.class);
                RuntimeReflection.register(MethodHandle.class.getDeclaredMethod("invokeBasic", Object[].class));
                RuntimeReflection.register(MethodHandle.class.getDeclaredMethod("linkToVirtual", Object[].class));
                RuntimeReflection.register(MethodHandle.class.getDeclaredMethod("linkToStatic", Object[].class));
            } catch (NoSuchMethodException e) {
                throw new GraalError(e);
            }
        }, MethodHandle.class);
    }
}

class MethodHandlesSupported implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return NativeImageOptions.areMethodHandlesSupported();
    }
}

class MethodHandlesNotSupported implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !NativeImageOptions.areMethodHandlesSupported();
    }
}
