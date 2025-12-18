/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vmaccess;

import java.util.Optional;

import jdk.internal.module.Modules;

/**
 * This class can be used to programmatically ensure that modules such a JVMCI or the graal compiler
 * are exported to {@link VMAccess} implementations.
 * <p>
 * This class requires {@code java.base/jdk.internal.module} to be exported to this module
 * ({@code jdk.graal.compiler.vmaccess}).
 */
public final class ModuleSupport {
    static {
        ModuleSupport.addExports(VMAccess.class, "jdk.internal.vm.ci",
                        "jdk.vm.ci.meta",
                        "jdk.vm.ci.meta.annotation",
                        "jdk.vm.ci.code");
        ModuleSupport.addExports(VMAccess.class, "jdk.graal.compiler",
                        "jdk.graal.compiler.phases.util");
    }

    private ModuleSupport() {
    }

    public static void addExports(Class<?> accessingModuleClass, String targetModuleName, String... packageNames) {
        addExports(accessingModuleClass.getModule(), targetModuleName, packageNames);
    }

    public static void addExports(String accessingModuleName, String targetModuleName, String... packageNames) {
        Optional<Module> maybeModule = ModuleLayer.boot().findModule(accessingModuleName);
        if (maybeModule.isEmpty()) {
            throw new IllegalStateException("Could not find module " + accessingModuleName + " in the boot layer");
        }
        addExports(maybeModule.get(), targetModuleName, packageNames);
    }

    public static void addExports(Module accessingModule, String targetModuleName, String... packageNames) {
        Optional<Module> maybeModule = ModuleLayer.boot().findModule(targetModuleName);
        if (maybeModule.isEmpty()) {
            throw new IllegalStateException("Could not find module " + targetModuleName + " in the boot layer");
        }
        addExports(accessingModule, maybeModule.get(), packageNames);
    }

    public static void addExports(Module accessingModule, Module targetModule, String... packageNames) {
        for (String packageName : packageNames) {
            Modules.addExports(targetModule, packageName, accessingModule);
        }
    }

    public static void addOpens(Module accessingModule, Module targetModule, String... packageNames) {
        for (String packageName : packageNames) {
            Modules.addOpens(targetModule, packageName, accessingModule);
        }
    }
}
