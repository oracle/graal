/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.util.Map;
import java.util.Set;

public class OptionClassFilter {
    private final Set<OptionOrigin> reasonCommandLine = Set.of(OptionOrigin.commandLineAPIOptionOriginSingleton, OptionOrigin.commandLineNonAPIOptionOriginSingleton);

    private final Map<String, Set<OptionOrigin>> requireCompletePackageOrClass;
    private final Set<Module> requireCompleteModules;
    private boolean requireCompleteAll;

    public OptionClassFilter(Map<String, Set<OptionOrigin>> requireCompletePackageOrClass, Set<Module> requireCompleteModules, boolean requireCompleteAll) {
        this.requireCompletePackageOrClass = requireCompletePackageOrClass;
        this.requireCompleteModules = requireCompleteModules;
        this.requireCompleteAll = requireCompleteAll;
    }

    public Object isIncluded(Class<?> clazz) {
        Module module = clazz.getModule();
        return isIncluded(module.isNamed() ? module.getName() : null, clazz.getPackageName(), clazz.getName());
    }

    public Object isIncluded(String moduleName, String packageName, String className) {
        if (requireCompleteAll) {
            return reasonCommandLine;
        }

        if (moduleName != null) {
            String module = isModuleIncluded(moduleName);
            if (module != null) {
                return module;
            }
        }

        Set<OptionOrigin> origins = isPackageOrClassIncluded(className);
        if (origins != null) {
            return origins;
        }
        return isPackageOrClassIncluded(packageName);
    }

    public Set<OptionOrigin> isPackageOrClassIncluded(String packageName) {
        if (requireCompleteAll) {
            return reasonCommandLine;
        }

        return requireCompletePackageOrClass.get(packageName);
    }

    public String isModuleIncluded(String moduleName) {
        for (Module module : requireCompleteModules) {
            if (module.getName().equals(moduleName)) {
                return module.toString();
            }
        }
        return null;
    }

    public void addPackageOrClass(String packageOrClass, Set<OptionOrigin> reason) {
        requireCompletePackageOrClass.put(packageOrClass, reason);
    }
}
