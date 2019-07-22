/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jdk.internal.module.Modules;
import jdk.vm.ci.services.Services;

public final class ModuleSupport {
    private ModuleSupport() {
    }

    public static List<String> getJVMCIModuleResources() {
        Module jvmciModule = Services.class.getModule();
        Optional<ModuleReference> moduleReference = ModuleFinder.ofSystem().find(jvmciModule.getName());
        assert moduleReference.isPresent() : "Unable access ModuleReference of JVMCI module";
        try (ModuleReader moduleReader = moduleReference.get().open()) {
            return moduleReader.list().collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Unable get list of resources in JVMCI module", e);
        }
    }

    static void openModule(Class<?> declaringClass, Class<?> accessingClass) {
        Module declaringModule = declaringClass.getModule();
        String packageName = declaringClass.getPackageName();
        Module accessingModule = accessingClass.getModule();
        if (!declaringModule.isOpen(packageName, accessingModule)) {
            Modules.addOpens(declaringModule, packageName, accessingModule);
        }
    }
}
