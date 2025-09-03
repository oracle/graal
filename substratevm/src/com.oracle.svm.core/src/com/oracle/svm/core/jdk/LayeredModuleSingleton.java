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
package com.oracle.svm.core.jdk;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.util.UserError;

/**
 * This singleton keeps track of the {@code Module#openPackages} and {@code Module#exportedPackages}
 * from all image layers.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class LayeredModuleSingleton implements LayeredImageSingleton {
    public static final String ALL_UNNAMED_MODULE_NAME = "native-image-all-unnamed";
    public static final String EVERYONE_MODULE_NAME = "native-image-everyone";

    protected final Map<String, Map<String, Set<String>>> moduleOpenPackages;
    protected final Map<String, Map<String, Set<String>>> moduleExportedPackages;

    private final Map<String, Module> moduleNames = new HashMap<>();

    private Module everyoneModule;
    private Module allUnnamedModule;

    public LayeredModuleSingleton() {
        this(new HashMap<>(), new HashMap<>());
    }

    public LayeredModuleSingleton(Map<String, Map<String, Set<String>>> moduleOpenPackages, Map<String, Map<String, Set<String>>> moduleExportedPackages) {
        this.moduleOpenPackages = moduleOpenPackages;
        this.moduleExportedPackages = moduleExportedPackages;
    }

    public static LayeredModuleSingleton singleton() {
        return ImageSingletons.lookup(LayeredModuleSingleton.class);
    }

    public void setUnnamedModules(Module everyoneModule, Module allUnnamedModule) {
        this.everyoneModule = everyoneModule;
        this.allUnnamedModule = allUnnamedModule;
    }

    public Map<String, Set<String>> getOpenPackages(Module module) {
        return moduleOpenPackages.get(module.getName());
    }

    public Map<String, Set<String>> getExportedPackages(Module module) {
        return moduleExportedPackages.get(module.getName());
    }

    public Collection<Module> getModules() {
        return moduleNames.values();
    }

    public void setOpenPackages(Module module, Map<String, Set<Module>> openPackages) {
        setPackages(module, moduleOpenPackages, openPackages, "opened");
    }

    public void setExportedPackages(Module module, Map<String, Set<Module>> exportedPackages) {
        setPackages(module, moduleExportedPackages, exportedPackages, "exported");
    }

    private void setPackages(Module module, Map<String, Map<String, Set<String>>> modulePackages, Map<String, Set<Module>> packages, String mode) {
        Module oldValue = moduleNames.put(module.toString(), module);
        if (oldValue != null && oldValue != module) {
            throw UserError.abort("Layered images require all modules to have a different name because their identity hash code is not consistent across layers. " +
                            "The modules %s and %s have the same name and were added to the %s packages", module, oldValue, mode);
        }
        Map<String, Set<String>> namesMap = modulePackages.computeIfAbsent(module.getName(), k -> new HashMap<>());
        for (var entry : packages.entrySet()) {
            Set<String> modules = namesMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            modules.addAll(entry.getValue().stream().map(Module::getName).toList());
            modules.remove(null);
            if (entry.getValue().contains(allUnnamedModule)) {
                modules.add(ALL_UNNAMED_MODULE_NAME);
            }
            if (entry.getValue().contains(everyoneModule)) {
                modules.add(EVERYONE_MODULE_NAME);
            }
        }
    }
}
