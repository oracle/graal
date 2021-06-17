/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.BootModuleLayerSupport;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@AutomaticFeature
@Platforms(Platform.HOSTED_ONLY.class)
public final class ModuleLayerFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return new JDK11OrLater().getAsBoolean();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BootModuleLayerSupport.class, new BootModuleLayerSupport());
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        AnalysisUniverse universe = accessImpl.getUniverse();

        Set<Module> reachableModules = universe.getTypes()
                .stream()
                .filter(t -> t.isReachable() && !t.isArray())
                .map(t -> t.getJavaClass().getModule())
                .filter(Module::isNamed)
                .filter(m -> !m.getName().startsWith("jdk.proxy"))
                .collect(Collectors.toSet());

        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader.modulepath(), reachableModules);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);
    }

    private ModuleLayer synthesizeRuntimeBootLayer(List<Path> mp, Set<Module> reachableModules) {
        Configuration cf = synthesizeRuntimeBootLayerConfiguration(mp, reachableModules);
        try {
            Constructor<ModuleLayer> ctor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
            ctor.setAccessible(true);
            ModuleLayer runtimeBootLayer = ctor.newInstance(cf, List.of(), null);
            patchRuntimeBootLayer(runtimeBootLayer, reachableModules);
            return runtimeBootLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to instantiate the runtime boot module layer.", ex);
        }
    }

    private Configuration synthesizeRuntimeBootLayerConfiguration(List<Path> mp, Set<Module> reachableModules) {
        ModuleFinder finder = ModuleFinder.of(mp.toArray(Path[]::new));
        Set<String> roots = reachableModules.stream().map(Module::getName).collect(Collectors.toSet());
        List<Configuration> parents = List.of(ModuleLayer.boot().configuration());
        return Configuration.resolve(finder, parents, finder, roots);
    }

    private void patchRuntimeBootLayer(ModuleLayer runtimeBootLayer, Set<Module> reachableModules) {
        Map<String, Module> nameToModule = reachableModules
                .stream()
                .collect(Collectors.toMap(Module::getName, m -> m));

        Field nameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
        Field modulesField = ReflectionUtil.lookupField(ModuleLayer.class, "modules");
        try {
            nameToModuleField.setAccessible(true);
            modulesField.setAccessible(true);
            nameToModuleField.set(runtimeBootLayer, nameToModule);
            modulesField.set(runtimeBootLayer, reachableModules);
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }
    }
}
