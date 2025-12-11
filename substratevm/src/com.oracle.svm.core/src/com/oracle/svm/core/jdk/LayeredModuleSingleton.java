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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.vmaccess.ResolvedJavaModule;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.OriginalModuleProvider;

/**
 * This singleton keeps track of the {@code Module#openPackages} and {@code Module#exportedPackages}
 * from all image layers.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class LayeredModuleSingleton {
    public static final String ALL_UNNAMED_MODULE_NAME = "native-image-all-unnamed";
    public static final String EVERYONE_MODULE_NAME = "native-image-everyone";

    /**
     * Map containing all the {@code Module#openPackages} values for each module. The key is the
     * module name and the value is the corresponding {@code Module#openPackages}. The name of the
     * module is used instead of the Module object because the hashcode of the Module object is not
     * consistent across layers. This is ensured by
     * {@link LayeredModuleSingleton#setPackages(ResolvedJavaModule, EconomicMap, Map, String)}}.
     */
    protected final EconomicMap<String, Map<String, Set<String>>> moduleOpenPackages;

    /**
     * See {@link LayeredModuleSingleton#moduleOpenPackages}.
     */
    protected final EconomicMap<String, Map<String, Set<String>>> moduleExportedPackages;

    /**
     * Keeps track of all the modules whose {@code Module#openPackages} and
     * {@code Module#exportedPackages} are set by
     * {@link LayeredModuleSingleton#setExportedPackages(ResolvedJavaModule, Map)} and
     * {@link LayeredModuleSingleton#setOpenPackages(ResolvedJavaModule, Map)}. This also allows to
     * ensure each module has a different name.
     */
    private final EconomicMap<String, ResolvedJavaModule> nameToModule = EconomicMap.create();

    /**
     * The value of {@code Module.EVERYONE_MODULE}.
     */
    private ResolvedJavaModule everyoneModule;

    /**
     * The value of {@code Module.ALL_UNNAMED_MODULE}.
     */
    private ResolvedJavaModule allUnnamedModule;

    public LayeredModuleSingleton() {
        this(EconomicMap.create(), EconomicMap.create());
    }

    public LayeredModuleSingleton(EconomicMap<String, Map<String, Set<String>>> moduleOpenPackages, EconomicMap<String, Map<String, Set<String>>> moduleExportedPackages) {
        this.moduleOpenPackages = moduleOpenPackages;
        this.moduleExportedPackages = moduleExportedPackages;
    }

    public static LayeredModuleSingleton singleton() {
        return ImageSingletons.lookup(LayeredModuleSingleton.class);
    }

    public void setUnnamedModules(ResolvedJavaModule everyoneModule, ResolvedJavaModule allUnnamedModule) {
        this.everyoneModule = everyoneModule;
        this.allUnnamedModule = allUnnamedModule;
    }

    public Map<String, Set<String>> getOpenPackages(ResolvedJavaModule module) {
        return moduleOpenPackages.get(module.getName());
    }

    public Map<String, Set<String>> getExportedPackages(ResolvedJavaModule module) {
        return moduleExportedPackages.get(module.getName());
    }

    /**
     * Returns all the modules whose {@code Module#openPackages} and {@code Module#exportedPackages}
     * are set.
     */
    public Iterable<ResolvedJavaModule> getModules() {
        return nameToModule.getValues();
    }

    public boolean containsModule(ResolvedJavaModule module) {
        for (ResolvedJavaModule m : nameToModule.getValues()) {
            if (m.equals(module)) {
                return true;
            }
        }
        return false;
    }

    public void setOpenPackages(ResolvedJavaModule module, Map<String, Set<Module>> openPackages) {
        setPackages(module, moduleOpenPackages, openPackages, "opened");
    }

    public void setExportedPackages(ResolvedJavaModule module, Map<String, Set<Module>> exportedPackages) {
        setPackages(module, moduleExportedPackages, exportedPackages, "exported");
    }

    private void setPackages(ResolvedJavaModule module, EconomicMap<String, Map<String, Set<String>>> modulePackages, Map<String, Set<Module>> packages, String mode) {
        ResolvedJavaModule oldValue = nameToModule.put(module.toString(), module);
        if (oldValue != null && !oldValue.equals(module)) {
            throw UserError.abort("Layered images require all modules to have a different name because their identity hash code is not consistent across layers. " +
                            "The modules %s and %s have the same name and were added to the %s packages", module, oldValue, mode);
        }
        Map<String, Set<String>> namesMap = modulePackages.computeIfAbsent(module.getName(), _ -> new HashMap<>());
        for (var entry : packages.entrySet()) {
            Set<String> modules = namesMap.computeIfAbsent(entry.getKey(), _ -> new HashSet<>()); // noEconomicSet(streaming)
            modules.addAll(entry.getValue().stream().map(GraalAccess::lookupModule).map(ResolvedJavaModule::getName).toList());
            modules.remove(null);
            /*
             * ALL_UNNAMED_MODULE and EVERYONE_MODULE don't have a name, so they need a special
             * marker to track them.
             */
            if (entry.getValue().contains(OriginalModuleProvider.getJavaModule(allUnnamedModule))) {
                modules.add(ALL_UNNAMED_MODULE_NAME);
            }
            if (entry.getValue().contains(OriginalModuleProvider.getJavaModule(everyoneModule))) {
                modules.add(EVERYONE_MODULE_NAME);
            }
        }
    }
}
