/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.module.ModuleFinder;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.module.ModuleBootstrap")
@SuppressWarnings("unused")
final class Target_jdk_internal_module_ModuleBootstrap {
    // Checkstyle: stop
    /// Holds the module names consumed by `ModuleBootstrap#addEnableNativeAccess`.
    @Alias @RecomputeFieldValue(kind = Kind.None, isFinal = false) //
    static Set<String> USER_NATIVE_ACCESS_MODULES;
    // Checkstyle: resume

    @Alias
    static native void addExtraReads(ModuleLayer bootLayer);

    @Alias
    static native void addExtraExportsAndOpens(ModuleLayer bootLayer);

    @Alias
    static native void addEnableNativeAccess(ModuleLayer bootLayer);

    @Alias
    static native Set<String> decodeEnableNativeAccess();

    @Alias
    static native ModuleFinder finderFor(String prop);
}

final class ModuleBootstrapSubstitutionsSupport {
    private ModuleBootstrapSubstitutionsSupport() {
    }

    /// Applies only launch-time `--enable-native-access` selections to the runtime boot layer.
    ///
    /// Native-access grants selected during image building are already replicated into runtime
    /// modules by `ModuleLayerFeature`. Replaying the hosted module-name set here would warn for
    /// builder-only modules that are intentionally absent from the image boot layer.
    static void addRuntimeEnableNativeAccessModules(ModuleLayer bootLayer) {
        Set<String> runtimeModules = Target_jdk_internal_module_ModuleBootstrap.decodeEnableNativeAccess();
        /*
         * Replace the build-time USER_NATIVE_ACCESS_MODULES value with only launch-time user
         * grants. ModuleLayerFeature already copies build-time --enable-native-access grants into
         * the Module objects stored in the image heap, so replaying the hosted user list here would
         * only warn for build-time modules that are intentionally absent from the runtime boot
         * layer.
         */
        Target_jdk_internal_module_ModuleBootstrap.USER_NATIVE_ACCESS_MODULES = runtimeModules;
        /*
         * The JDK helper also applies the fixed ModuleLoaderMap.nativeAccessModules() grants. That
         * must still run when there are no launch-time user grants because a JDK module can be
         * added to the boot layer at runtime.
         */
        Target_jdk_internal_module_ModuleBootstrap.addEnableNativeAccess(bootLayer);
    }
}
