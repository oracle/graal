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
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This feature:
 * <ul>
 *      <li> synthesizes the runtime boot module layer </li>
 *      <li> ensures that fields/methods from the {@link ClassLoader} class are reachable in order to
 *      make native methods of the {@link Module} class work </li>
 * </ul>
 * <p>
 * This feature synthesizes the runtime boot module layer by using type reachability information.
 * If a type is reachable, its module is also reachable and therefore should be included in the
 * runtime boot module layer.
 * </p>
 * <p>
 * The configuration for the runtime boot module layer is resolved using the module reachability
 * data provided to us by the analysis as resolve roots.
 * </p>
 * <p>
 * We are purposefully avoiding public API for module layer creation, such as
 * {@link ModuleLayer#defineModulesWithOneLoader(Configuration, ClassLoader)}, because as a side
 * effect this will create a new class loader. Instead, we use a private constructor to construct
 * the {@link ModuleLayer} instance, which we then patch using the module reachability data
 * provided to us by the analysis.
 * </p>
 * <p>
 * Because the result of this feature is dependant on the analysis results, and because this feature
 * will add reachable object(s) as it's result, it is necessary to perform the logic during the
 * analysis, for lack of a better option (even though only the last analysis cycle is sufficient,
 * but that cannot be known in advance).
 * </p>
 */
@AutomaticFeature
@Platforms(Platform.HOSTED_ONLY.class)
public final class ModuleLayerFeature implements Feature {

    private Field moduleNameToModuleField;
    private Field moduleParentsField;
    private Field configurationParentsField;
    private Constructor<ModuleLayer> moduleLayerConstructor;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return new JDK11OrLater().getAsBoolean();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BootModuleLayerSupport.class, new BootModuleLayerSupport());
        moduleNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
        moduleParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
        configurationParentsField = ReflectionUtil.lookupField(Configuration.class, "parents");
        moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(
                a -> a.registerAsUnsafeAccessed(ReflectionUtil.lookupField(ClassLoader.class, "classLoaderValueMap")),
                ReflectionUtil.lookupMethod(ClassLoader.class, "trySetObjectField", String.class, Object.class)
        );
        RuntimeReflection.register(ReflectionUtil.lookupField(ClassLoader.class, "classLoaderValueMap"));
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        AnalysisUniverse universe = accessImpl.getUniverse();

        Map<String, Module> reachableModules = universe.getTypes()
                .stream()
                .filter(t -> t.isReachable() && !t.isArray())
                .map(t -> t.getJavaClass().getModule())
                .filter(m -> m.isNamed() && !m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC))
                .collect(Collectors.toMap(Module::getName, m -> m, (m1, m2) -> m1));

        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader, reachableModules);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);
    }

    private ModuleLayer synthesizeRuntimeBootLayer(ImageClassLoader cl, Map<String, Module> reachableModules) {
        Configuration cf = synthesizeRuntimeBootLayerConfiguration(cl.modulepath(), reachableModules);
        try {
            ModuleLayer runtimeBootLayer = moduleLayerConstructor.newInstance(cf, List.of(), null);
            patchRuntimeBootLayer(runtimeBootLayer, reachableModules);
            return runtimeBootLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer.", ex);
        }
    }

    private Configuration synthesizeRuntimeBootLayerConfiguration(List<Path> mp, Map<String, Module> reachableModules) {
        ModuleFinder finder = ModuleFinder.of(mp.toArray(Path[]::new));
        Set<String> roots = reachableModules.keySet();
        try {
            Configuration cf = ModuleLayer.boot().configuration().resolve(finder, finder, roots);
            configurationParentsField.set(cf, List.of(Configuration.empty()));
            return cf;
        } catch (IllegalAccessException | FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer configuration.", ex);
        }
    }

    private void patchRuntimeBootLayer(ModuleLayer runtimeBootLayer, Map<String, Module> reachableModules) {
        try {
            moduleNameToModuleField.set(runtimeBootLayer, reachableModules);
            moduleParentsField.set(runtimeBootLayer, List.of(ModuleLayer.empty()));
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }
    }
}
