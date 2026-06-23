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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.module.Modules;

@TargetClass(className = "jdk.internal.loader.BootLoader", innerClass = "PackageHelper")
final class Target_jdk_internal_loader_BootLoader_PackageHelper {

    /// Finds a boot module from the location format returned by `BootLoader.getSystemPackageLocation`.
    ///
    /// This intentionally accepts only `jrt:/` module locations. The original JDK implementation also
    /// recognizes exploded-module `file:/` locations under `JAVA_HOME/modules`, but `JAVA_HOME` might
    /// not be set at image run time and Native Image does not support exploded boot modules. For
    /// classes loaded from `-Xbootclasspath/a:`, `location` will be a file system path without
    /// a `file:/` prefix.
    @Substitute
    private static Module findModule(String location) {
        String moduleName = location.startsWith("jrt:/") ? location.substring(5) : null;
        if (moduleName != null) {
            Module module = Modules.findLoadedModule(moduleName).orElse(null);
            if (module == null) {
                throw new InternalError(moduleName + " not loaded");
            }
            return module;
        }
        return null;
    }
}
