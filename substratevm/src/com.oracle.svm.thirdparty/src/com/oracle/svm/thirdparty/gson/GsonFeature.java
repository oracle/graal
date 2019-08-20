/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty.gson;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

/**
 * Support for the Gson library on SubstrateVM.
 * <p>
 * Gson uses reflection to instantiate data classes and write properties. All such data classes need
 * to be registered manually by the user using the {@link RuntimeReflection runtime reflection
 * support} of Substrate VM. It is not feasible to automatically detect all necessary classes.
 * <p>
 * This feature registers parts of {@link sun.misc.Unsafe} as reflectively accessible. Gson uses it
 * internally to instantiate classes that do not have a no-argument constructor.
 */
@AutomaticFeature
public final class GsonFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("com.google.gson.Gson") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            /* Reflection usage in com.google.gson.internal.UnsafeAllocator.create(). */
            RuntimeReflection.register(sun.misc.Unsafe.class);
            RuntimeReflection.register(sun.misc.Unsafe.class.getDeclaredField("theUnsafe"));
            RuntimeReflection.register(sun.misc.Unsafe.class.getDeclaredMethod("allocateInstance", Class.class));
        } catch (NoSuchFieldException | NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
