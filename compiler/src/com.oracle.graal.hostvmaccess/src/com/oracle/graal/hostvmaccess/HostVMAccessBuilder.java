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
package com.oracle.graal.hostvmaccess;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.vmaccess.ModuleSupport;
import com.oracle.graal.vmaccess.VMAccess;

public final class HostVMAccessBuilder implements VMAccess.Builder {
    private List<String> classpath = List.of();
    private List<String> modulepath = List.of();
    private List<String> addModules = List.of();
    private boolean enableAssertions;
    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    @Override
    public String getVMAccessName() {
        return "host";
    }

    @Override
    public VMAccess.Builder classPath(List<String> paths) {
        this.classpath = paths;
        return this;
    }

    @Override
    public VMAccess.Builder modulePath(List<String> paths) {
        this.modulepath = paths;
        return this;
    }

    @Override
    public VMAccess.Builder addModules(List<String> modules) {
        this.addModules = modules;
        return this;
    }

    @Override
    public VMAccess.Builder enableAssertions(boolean assertionStatus) {
        this.enableAssertions = assertionStatus;
        return this;
    }

    @Override
    public VMAccess.Builder enableSystemAssertions(boolean assertionStatus) {
        // ignored
        return this;
    }

    @Override
    public VMAccess.Builder systemProperty(String name, String value) {
        systemProperties.put(name, value);
        return this;
    }

    @Override
    public VMAccess.Builder vmOption(String option) {
        // ignored
        return this;
    }

    @Override
    public VMAccess build() {
        ModuleAccess.ensureModuleAccess();
        List<Path> classPath = classpath.stream().map(Path::of).toList();
        ModuleFinder upgradeAndSystemModuleFinder = createUpgradeAndSystemModuleFinder();
        Path[] modulePath = modulepath.stream().map(Path::of).toArray(Path[]::new);
        ModuleFinder modulePathsFinder = ModuleFinder.of(modulePath);
        Set<String> moduleNames = modulePathsFinder.findAll().stream() //
                        .map(moduleReference -> moduleReference.descriptor().name()) //
                        .collect(Collectors.toCollection(HashSet::new));
        moduleNames.addAll(addModules);
        Configuration configuration = ModuleLayer.boot().configuration().resolve(modulePathsFinder, upgradeAndSystemModuleFinder, moduleNames);

        HostVMAccessClassLoader classLoader = new HostVMAccessClassLoader(classPath, configuration, ClassLoader.getSystemClassLoader());
        classLoader.setDefaultAssertionStatus(enableAssertions);
        return new HostVMAccess(classLoader);
    }

    private ModuleFinder createUpgradeAndSystemModuleFinder() {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        if (upgradeModulePath != null) {
            finder = ModuleFinder.compose(upgradeModulePath, finder);
        }
        return finder;
    }

    private ModuleFinder finderFor(String prop) {
        String s = systemProperties.get(prop);
        if (s == null || s.isEmpty()) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir : dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModuleFinder.of(paths);
        }
    }

    private static final class ModuleAccess {
        static {
            Module module = ModuleAccess.class.getModule();
            ModuleSupport.addExports(module, "java.base",
                            "jdk.internal.access",
                            "jdk.internal.loader",
                            "jdk.internal.module");
            ModuleSupport.addExports(module, "jdk.internal.vm.ci",
                            "jdk.vm.ci.meta",
                            "jdk.vm.ci.runtime");
            ModuleSupport.addExports(module, "jdk.graal.compiler",
                            "jdk.graal.compiler.api.replacements",
                            "jdk.graal.compiler.api.runtime",
                            "jdk.graal.compiler.core.target",
                            "jdk.graal.compiler.phases.util",
                            "jdk.graal.compiler.runtime");
        }

        static void ensureModuleAccess() {
        }
    }
}
