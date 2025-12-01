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

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.stream.Stream;

import jdk.internal.loader.BootLoader;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Fallback implementations for {@link JVMCIReflectionUtil} that is still implemented in terms of
 * core reflection due to missing features in JVMCI.
 */
final class JVMCIReflectionUtilFallback {

    private static Class<?> getJavaClass(ResolvedJavaType type) {
        return OriginalClassProvider.getJavaClass(type);
    }

    static ResolvedJavaPackage getPackage(ResolvedJavaType type) {
        Package pkg = getJavaClass(type).getPackage();
        return pkg == null ? null : new ResolvedJavaPackageImpl(pkg);
    }

    static ResolvedJavaModule getModule(ResolvedJavaType declaringClass) {
        return new ResolvedJavaModuleImpl(getJavaClass(declaringClass).getModule());
    }

    static URL getOrigin(ResolvedJavaType type) {
        ProtectionDomain pd = getJavaClass(type).getProtectionDomain();
        CodeSource cs = pd.getCodeSource();
        if (cs == null) {
            return null;
        }
        return cs.getLocation();
    }

    public static Stream<ResolvedJavaPackage> bootLoaderPackages() {
        return BootLoader.packages().map(ResolvedJavaPackageImpl::new);
    }
}
