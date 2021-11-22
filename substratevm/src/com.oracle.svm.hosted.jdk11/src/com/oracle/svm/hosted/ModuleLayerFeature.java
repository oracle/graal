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
import com.oracle.svm.core.jdk11.BootModuleLayerSupport;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This feature:
 * <ul>
 * <li>synthesizes the runtime boot module layer</li>
 * <li>ensures that fields/methods from the {@link ClassLoader} class are reachable in order to make
 * native methods of the {@link Module} class work</li>
 * </ul>
 * <p>
 * This feature synthesizes the runtime boot module layer by using type reachability information. If
 * a type is reachable, its module is also reachable and therefore should be included in the runtime
 * boot module layer.
 * </p>
 * <p>
 * The configuration for the runtime boot module layer is resolved using the module reachability
 * data provided to us by the analysis as resolve roots.
 * </p>
 * <p>
 * We are purposefully avoiding public API for module layer creation, such as
 * {@link ModuleLayer#defineModulesWithOneLoader(Configuration, ClassLoader)}, because as a side
 * effect this will create a new class loader. Instead, we use a private constructor to construct
 * the {@link ModuleLayer} instance, which we then patch using the module reachability data provided
 * to us by the analysis.
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
    private Constructor<ModuleLayer> moduleLayerConstructor;
    private Field moduleLayerNameToModuleField;
    private Field moduleLayerParentsField;
    private NameToModuleSynthesizer nameToModuleSynthesizer;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return new JDK11OrLater().getAsBoolean();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BootModuleLayerSupport.class, new BootModuleLayerSupport());
        moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
        moduleLayerNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
        moduleLayerParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
        nameToModuleSynthesizer = new NameToModuleSynthesizer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        Set<String> baseModules = ModuleLayer.boot().modules()
                        .stream()
                        .map(Module::getName)
                        .collect(Collectors.toSet());
        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader, baseModules);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        AnalysisUniverse universe = accessImpl.getUniverse();

        Stream<Module> analysisReachableModules = universe.getTypes()
                        .stream()
                        .filter(t -> t.isReachable() && !t.isArray())
                        .map(t -> t.getJavaClass().getModule())
                        .distinct();

        Set<String> allReachableModules = analysisReachableModules
                        .filter(Module::isNamed)
                        .filter(m -> !m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC))
                        .flatMap(ModuleLayerFeature::extractRequiredModuleNames)
                        .collect(Collectors.toSet());

        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader, allReachableModules);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);
    }

    /*
     * Creates a stream of module names that are reachable from a given module through "requires"
     */
    private static Stream<String> extractRequiredModuleNames(Module m) {
        Stream<String> requiredModules = m.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name);
        return Stream.concat(Stream.of(m.getName()), requiredModules);
    }

    private ModuleLayer synthesizeRuntimeBootLayer(ImageClassLoader cl, Set<String> reachableModules) {
        Configuration cf = synthesizeRuntimeBootLayerConfiguration(cl.modulepath(), reachableModules);
        try {
            ModuleLayer runtimeBootLayer = moduleLayerConstructor.newInstance(cf, List.of(), null);
            Map<String, Module> nameToModule = nameToModuleSynthesizer.synthesizeNameToModule(runtimeBootLayer, cl.getClassLoader());
            patchRuntimeBootLayer(runtimeBootLayer, nameToModule);
            return runtimeBootLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer.", ex);
        }
    }

    private static Configuration synthesizeRuntimeBootLayerConfiguration(List<Path> mp, Set<String> reachableModules) {
        ModuleFinder beforeFinder = new BootModuleLayerModuleFinder();
        ModuleFinder afterFinder = ModuleFinder.of(mp.toArray(Path[]::new));

        try {
            ModuleFinder composed = ModuleFinder.compose(beforeFinder, afterFinder);
            List<String> missingModules = new ArrayList<>();
            for (String module : reachableModules) {
                Optional<ModuleReference> mref = composed.find(module);
                if (mref.isEmpty()) {
                    missingModules.add(module);
                }
            }
            reachableModules.removeAll(missingModules);

            return Configuration.empty().resolve(beforeFinder, afterFinder, reachableModules);
        } catch (FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer configuration.", ex);
        }
    }

    private void patchRuntimeBootLayer(ModuleLayer runtimeBootLayer, Map<String, Module> nameToModule) {
        try {
            moduleLayerNameToModuleField.set(runtimeBootLayer, nameToModule);
            moduleLayerParentsField.set(runtimeBootLayer, List.of(ModuleLayer.empty()));
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }

        // Ensure that the lazy modules field gets set
        runtimeBootLayer.modules();
    }

    static class BootModuleLayerModuleFinder implements ModuleFinder {

        @Override
        public Optional<ModuleReference> find(String name) {
            return ModuleLayer.boot()
                            .configuration()
                            .findModule(name)
                            .map(ResolvedModule::reference);
        }

        @Override
        public Set<ModuleReference> findAll() {
            return ModuleLayer.boot()
                            .configuration()
                            .modules()
                            .stream()
                            .map(ResolvedModule::reference)
                            .collect(Collectors.toSet());
        }
    }

    private static final class NameToModuleSynthesizer {
        private final Module everyoneModule;
        private final Set<Module> everyoneSet;
        private final Constructor<Module> moduleConstructor;
        private final Field moduleLayerField;
        private final Field moduleReadsField;
        private final Field moduleOpenPackagesField;
        private final Field moduleExportedPackagesField;
        private final Method moduleFindModuleMethod;

        NameToModuleSynthesizer() {
            Method classGetDeclaredMethods0Method = ReflectionUtil.lookupMethod(Class.class, "getDeclaredFields0", boolean.class);
            try {
                ModuleSupport.openModuleByClass(Module.class, ModuleLayerFeature.class);
                Field[] moduleClassFields = (Field[]) classGetDeclaredMethods0Method.invoke(Module.class, false);

                Field everyoneModuleField = findFieldByName(moduleClassFields, "EVERYONE_MODULE");
                everyoneModuleField.setAccessible(true);
                everyoneModule = (Module) everyoneModuleField.get(null);

                moduleLayerField = findFieldByName(moduleClassFields, "layer");
                moduleReadsField = findFieldByName(moduleClassFields, "reads");
                moduleOpenPackagesField = findFieldByName(moduleClassFields, "openPackages");
                moduleExportedPackagesField = findFieldByName(moduleClassFields, "exportedPackages");
                moduleLayerField.setAccessible(true);
                moduleReadsField.setAccessible(true);
                moduleOpenPackagesField.setAccessible(true);
                moduleExportedPackagesField.setAccessible(true);
            } catch (ReflectiveOperationException | NoSuchElementException ex) {
                throw VMError.shouldNotReachHere("Failed to find the value of EVERYONE_MODULE field of Module class.", ex);
            }
            everyoneSet = Set.of(everyoneModule);
            moduleConstructor = ReflectionUtil.lookupConstructor(Module.class, ClassLoader.class, ModuleDescriptor.class);
            moduleFindModuleMethod = ReflectionUtil.lookupMethod(Module.class, "findModule", String.class, Map.class, Map.class, List.class);
        }

        private static Field findFieldByName(Field[] fields, String name) {
            return Arrays.stream(fields).filter(f -> f.getName().equals(name)).findAny().get();
        }

        /**
         * This method creates Module instances that will populate the runtime boot module layer of
         * the image. This implementation is copy-pasted from Module#defineModules(Configuration,
         * Function, ModuleLayer) with few simplifications (removing multiple classloader support)
         * and removal of VM state updates (otherwise we would be re-defining modules to the host
         * VM).
         */
        Map<String, Module> synthesizeNameToModule(ModuleLayer runtimeBootLayer, ClassLoader cl)
                        throws IllegalAccessException, InvocationTargetException, InstantiationException {
            Configuration cf = runtimeBootLayer.configuration();

            int cap = (int) (cf.modules().size() / 0.75f + 1.0f);
            Map<String, Module> nameToModule = new HashMap<>(cap);

            /*
             * Remove mapping of modules to classloaders. Create module instances without defining
             * them to the VM
             */
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                ModuleDescriptor descriptor = mref.descriptor();
                String name = descriptor.name();
                Module m = moduleConstructor.newInstance(cl, descriptor);
                moduleLayerField.set(m, runtimeBootLayer);
                nameToModule.put(name, m);
            }

            /*
             * Setup readability and exports/opens. This part is unchanged, save for field setters
             * and VM update removals
             */
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                ModuleDescriptor descriptor = mref.descriptor();

                String mn = descriptor.name();
                Module m = nameToModule.get(mn);
                assert m != null;

                Set<Module> reads = new HashSet<>();
                for (ResolvedModule other : resolvedModule.reads()) {
                    Module m2 = nameToModule.get(other.name());
                    reads.add(m2);
                }
                moduleReadsField.set(m, reads);

                if (!descriptor.isOpen() && !descriptor.isAutomatic()) {
                    if (descriptor.opens().isEmpty()) {
                        Map<String, Set<Module>> exportedPackages = new HashMap<>();
                        for (ModuleDescriptor.Exports exports : m.getDescriptor().exports()) {
                            String source = exports.source();
                            if (exports.isQualified()) {
                                Set<Module> targets = new HashSet<>();
                                for (String target : exports.targets()) {
                                    Module m2 = nameToModule.get(target);
                                    if (m2 != null) {
                                        targets.add(m2);
                                    }
                                }
                                if (!targets.isEmpty()) {
                                    exportedPackages.put(source, targets);
                                }
                            } else {
                                exportedPackages.put(source, everyoneSet);
                            }
                        }
                        moduleExportedPackagesField.set(m, exportedPackages);
                    } else {
                        Map<String, Set<Module>> openPackages = new HashMap<>();
                        Map<String, Set<Module>> exportedPackages = new HashMap<>();
                        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
                            String source = opens.source();
                            if (opens.isQualified()) {
                                Set<Module> targets = new HashSet<>();
                                for (String target : opens.targets()) {
                                    Module m2 = (Module) moduleFindModuleMethod.invoke(null, target, Map.of(), nameToModule, runtimeBootLayer.parents());
                                    if (m2 != null) {
                                        targets.add(m2);
                                    }
                                }
                                if (!targets.isEmpty()) {
                                    openPackages.put(source, targets);
                                }
                            } else {
                                openPackages.put(source, everyoneSet);
                            }
                        }

                        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
                            String source = exports.source();
                            Set<Module> openToTargets = openPackages.get(source);
                            if (openToTargets != null && openToTargets.contains(everyoneModule)) {
                                continue;
                            }

                            if (exports.isQualified()) {
                                Set<Module> targets = new HashSet<>();
                                for (String target : exports.targets()) {
                                    Module m2 = (Module) moduleFindModuleMethod.invoke(null, target, Map.of(), nameToModule, runtimeBootLayer.parents());
                                    if (m2 != null) {
                                        if (openToTargets == null || !openToTargets.contains(m2)) {
                                            targets.add(m2);
                                        }
                                    }
                                }
                                if (!targets.isEmpty()) {
                                    exportedPackages.put(source, targets);
                                }
                            } else {
                                exportedPackages.put(source, everyoneSet);
                            }
                        }

                        moduleOpenPackagesField.set(m, openPackages);
                        moduleExportedPackagesField.set(m, exportedPackages);
                    }
                }
            }

            return nameToModule;
        }
    }
}
