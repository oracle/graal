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

import java.util.Optional;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.util.ReflectionUtil;

/**
 * Support for the Gson library on SubstrateVM.
 * <p>
 * Gson uses reflection to instantiate data classes and write properties. All such data classes need
 * to be registered manually by the user using the {@link RuntimeReflection runtime reflection
 * support} of Substrate VM. It is not feasible to automatically detect all necessary classes.
 * <p>
 * This feature registers parts of {@code sun.misc.Unsafe} as reflectively accessible. Gson uses it
 * internally to instantiate classes that do not have a no-argument constructor.
 */
public final class GsonFeature implements Feature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("jdk.unsupported");
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        /* `sun.misc.Unsafe` is in `jdk.unsupported` */
        return requiredModule().isPresent();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Class<?> gsonClass = access.findClassByName("com.google.gson.Gson");
        if (gsonClass != null) {
            access.registerReachabilityHandler(GsonFeature::makeUnsafeReflectivelyAccessible, gsonClass);
        }
    }

    private static void makeUnsafeReflectivelyAccessible(DuringAnalysisAccess a) {
        Class<?> unsafeClass = a.findClassByName("sun.misc.Unsafe");
        RuntimeReflection.register(unsafeClass);
        RuntimeReflection.register(ReflectionUtil.lookupField(unsafeClass, "theUnsafe"));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(unsafeClass, "allocateInstance", Class.class));
    }
}
