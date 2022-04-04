/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.BootModuleLayerSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This feature:
 * <ul>
 * <li>synthesizes the runtime boot module layer</li>
 * <li>replicates build-time module relations at runtime</li>
 * <li>replaces references to hosted modules with runtime modules</li>
 * </ul>
 * <p>
 * This feature synthesizes the runtime boot module layer by using type reachability information. If
 * a type is reachable, its module is also reachable and therefore should be included in the runtime
 * boot module layer. If those modules require any additional modules, they will also be marked as
 * reachable.
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
 * to us by the analysis. This should be updated if JDK-8277013 gets resolved.
 * </p>
 * <p>
 * Because the result of this feature is dependant on the analysis results, and because this feature
 * will add reachable object(s) as its result, it is necessary to perform the logic during the
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
    private ModuleLayerFeatureUtils moduleLayerFeatureUtils;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
        moduleLayerNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
        moduleLayerParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
        moduleLayerFeatureUtils = new ModuleLayerFeatureUtils(accessImpl.imageClassLoader);
        Set<String> baseModules = ModuleLayer.boot().modules()
                        .stream()
                        .map(Module::getName)
                        .collect(Collectors.toSet());
        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader, baseModules, Set.of());
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);
        access.registerObjectReplacer(this::replaceHostedModules);
    }

    private Object replaceHostedModules(Object source) {
        if (source instanceof Module) {
            Module module = (Module) source;
            return moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(module, module.getDescriptor());
        }
        return source;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BootModuleLayerSupport.class, new BootModuleLayerSupport());
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

        Set<Module> analysisReachableNamedModules = analysisReachableModules
                        .filter(Module::isNamed)
                        .collect(Collectors.toSet());

        Set<String> extraModules = new HashSet<>();

        extraModules.addAll(ImageSingletons.lookup(ResourcesFeature.class).includedResourcesModules);

        String explicitlyAddedModules = System.getProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES, "");
        if (!explicitlyAddedModules.isEmpty()) {
            extraModules.addAll(Arrays.asList(SubstrateUtil.split(explicitlyAddedModules, ",")));
        }

        extraModules.forEach(moduleName -> {
            Optional<?> module = accessImpl.imageClassLoader.findModule(moduleName);
            if (module.isEmpty()) {
                VMError.shouldNotReachHere("Explicitly required module " + moduleName + " is not available");
            }
            analysisReachableNamedModules.add((Module) module.get());
        });

        Set<Module> analysisReachableSyntheticModules = analysisReachableNamedModules
                        .stream()
                        .filter(ModuleLayerFeature::isModuleSynthetic)
                        .collect(Collectors.toSet());

        Set<String> allReachableModules = analysisReachableNamedModules
                        .stream()
                        .flatMap(ModuleLayerFeature::extractRequiredModuleNames)
                        .collect(Collectors.toSet());

        ModuleLayer runtimeBootLayer = synthesizeRuntimeBootLayer(accessImpl.imageClassLoader, allReachableModules, analysisReachableSyntheticModules);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);

        replicateVisibilityModifications(runtimeBootLayer, accessImpl.imageClassLoader, analysisReachableNamedModules);
    }

    /*
     * Creates a stream of module names that are reachable from a given module through "requires"
     */
    private static Stream<String> extractRequiredModuleNames(Module m) {
        Stream<String> requiredModules = m.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name);
        return Stream.concat(Stream.of(m.getName()), requiredModules);
    }

    private ModuleLayer synthesizeRuntimeBootLayer(ImageClassLoader cl, Set<String> reachableModules, Set<Module> syntheticModules) {
        /**
         * For consistent module lookup we reuse the {@link ModuleFinder}s defined and used in
         * {@link NativeImageClassLoaderSupport}.
         */
        NativeImageClassLoaderSupport classLoaderSupport = cl.classLoaderSupport;
        ModuleFinder beforeFinder = classLoaderSupport.modulepathModuleFinder;
        ModuleFinder afterFinder = classLoaderSupport.upgradeAndSystemModuleFinder;
        Configuration cf = synthesizeRuntimeBootLayerConfiguration(beforeFinder, afterFinder, reachableModules);
        try {
            ModuleLayer runtimeBootLayer = moduleLayerConstructor.newInstance(cf, List.of(), null);
            Map<String, Module> nameToModule = moduleLayerFeatureUtils.synthesizeNameToModule(runtimeBootLayer);
            for (Module syntheticModule : syntheticModules) {
                Module runtimeSyntheticModule = moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(syntheticModule.getName(), syntheticModule.getDescriptor());
                nameToModule.putIfAbsent(runtimeSyntheticModule.getName(), runtimeSyntheticModule);
                moduleLayerFeatureUtils.patchModuleLayerField(runtimeSyntheticModule, runtimeBootLayer);
            }
            patchRuntimeBootLayer(runtimeBootLayer, nameToModule);
            return runtimeBootLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer.", ex);
        }
    }

    private void replicateVisibilityModifications(ModuleLayer runtimeBootLayer, ImageClassLoader cl, Set<Module> analysisReachableNamedModules) {
        List<Module> applicationModules = findApplicationModules(runtimeBootLayer, cl.applicationModulePath());

        Map<String, HostedRuntimeModulePair> moduleLookupMap = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(Module::getName, m -> new HostedRuntimeModulePair(m, runtimeBootLayer)));
        moduleLookupMap.putIfAbsent("ALL-UNNAMED", HostedRuntimeModulePair.withReplicatedHostedModule(moduleLayerFeatureUtils.allUnnamedModule));
        moduleLookupMap.putIfAbsent("EVERYONE", HostedRuntimeModulePair.withReplicatedHostedModule(moduleLayerFeatureUtils.everyoneModule));

        Module builderModule = ModuleLayerFeature.class.getModule();
        assert builderModule != null;

        try {
            for (Map.Entry<String, HostedRuntimeModulePair> e1 : moduleLookupMap.entrySet()) {
                Module hostedFrom = e1.getValue().hostedModule;
                if (!hostedFrom.isNamed()) {
                    continue;
                }
                Module runtimeFrom = e1.getValue().runtimeModule;
                for (Map.Entry<String, HostedRuntimeModulePair> e2 : moduleLookupMap.entrySet()) {
                    Module hostedTo = e2.getValue().hostedModule;
                    if (hostedTo == hostedFrom) {
                        continue;
                    }
                    Module runtimeTo = e2.getValue().runtimeModule;
                    if (isModuleSynthetic(hostedFrom) || hostedFrom.canRead(hostedTo)) {
                        moduleLayerFeatureUtils.addReads(runtimeFrom, runtimeTo);
                        if (hostedFrom == builderModule) {
                            for (Module appModule : applicationModules) {
                                moduleLayerFeatureUtils.addReads(appModule, runtimeTo);
                            }
                        }
                    }
                    for (String pn : runtimeFrom.getPackages()) {
                        if (isModuleSynthetic(hostedFrom) || hostedFrom.isOpen(pn, hostedTo)) {
                            moduleLayerFeatureUtils.addOpens(runtimeFrom, pn, runtimeTo);
                            if (hostedTo == builderModule) {
                                for (Module appModule : applicationModules) {
                                    moduleLayerFeatureUtils.addOpens(runtimeFrom, pn, appModule);
                                }
                            }
                        }
                        if (isModuleSynthetic(hostedFrom) || hostedFrom.isExported(pn, hostedTo)) {
                            moduleLayerFeatureUtils.addExports(runtimeFrom, pn, runtimeTo);
                            if (hostedTo == builderModule) {
                                for (Module appModule : applicationModules) {
                                    moduleLayerFeatureUtils.addExports(runtimeFrom, pn, appModule);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to transfer hosted module relations to the runtime boot module layer.", ex);
        }
    }

    private static boolean isModuleSynthetic(Module m) {
        return m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC);
    }

    private static List<Module> findApplicationModules(ModuleLayer runtimeBootLayer, List<Path> applicationModulePath) {
        List<Module> applicationModules = new ArrayList<>();
        List<String> applicationModuleNames;
        try {
            ModuleFinder applicationModuleFinder = ModuleFinder.of(applicationModulePath.toArray(Path[]::new));
            applicationModuleNames = applicationModuleFinder.findAll()
                            .stream()
                            .map(m -> m.descriptor().name())
                            .collect(Collectors.toList());
        } catch (FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to locate application modules.", ex);
        }

        for (String moduleName : applicationModuleNames) {
            Optional<Module> module = runtimeBootLayer.findModule(moduleName);
            if (module.isEmpty()) {
                // Module is not reachable
                continue;
            }
            applicationModules.add(module.get());
        }

        return applicationModules;
    }

    private static Configuration synthesizeRuntimeBootLayerConfiguration(ModuleFinder beforeFinder, ModuleFinder afterFinder, Set<String> reachableModules) {
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
            for (Module m : runtimeBootLayer.modules()) {
                Optional<Module> hostedModule = ModuleLayer.boot().findModule(m.getName());
                if (hostedModule.isPresent() && hostedModule.get().getClassLoader() == null) {
                    moduleLayerFeatureUtils.patchModuleLoaderField(m, null);
                }
            }
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }

        // Ensure that the lazy modules field gets set
        runtimeBootLayer.modules();
    }

    private static final class HostedRuntimeModulePair {
        static HostedRuntimeModulePair withReplicatedHostedModule(Module module) {
            return new HostedRuntimeModulePair(module, module);
        }

        final Module hostedModule;
        final Module runtimeModule;

        HostedRuntimeModulePair(Module hostedModule, ModuleLayer runtimeBootLayer) {
            this.hostedModule = hostedModule;
            this.runtimeModule = runtimeBootLayer.findModule(hostedModule.getName()).orElseThrow(() -> errorSupplier(hostedModule));
        }

        private HostedRuntimeModulePair(Module hosted, Module runtime) {
            hostedModule = hosted;
            runtimeModule = runtime;
        }

        static RuntimeException errorSupplier(Module m) {
            return VMError.shouldNotReachHere("Failed to find module " + m.getName() + " in the runtime boot module layer");
        }
    }

    private static final class ModuleLayerFeatureUtils {
        private final Map<String, Module> nameToModuleLookup;
        private final ImageClassLoader imageClassLoader;

        private final Module allUnnamedModule;
        private final Set<Module> allUnnamedModuleSet;
        private final Module everyoneModule;
        private final Set<Module> everyoneSet;
        private final Constructor<Module> moduleConstructor;
        private final Field moduleDescriptorField;
        private final Field moduleLayerField;
        private final Field moduleLoaderField;
        private final Field moduleReadsField;
        private final Field moduleOpenPackagesField;
        private final Field moduleExportedPackagesField;
        private final Method moduleFindModuleMethod;

        ModuleLayerFeatureUtils(ImageClassLoader cl) {
            nameToModuleLookup = new HashMap<>();
            imageClassLoader = cl;
            Method classGetDeclaredMethods0Method = ReflectionUtil.lookupMethod(Class.class, "getDeclaredFields0", boolean.class);
            try {
                ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ModuleLayerFeature.class, Module.class);
                Field[] moduleClassFields = (Field[]) classGetDeclaredMethods0Method.invoke(Module.class, false);

                Field everyoneModuleField = findFieldByName(moduleClassFields, "EVERYONE_MODULE");
                everyoneModuleField.setAccessible(true);
                everyoneModule = (Module) everyoneModuleField.get(null);

                Field allUnnamedModuleField = findFieldByName(moduleClassFields, "ALL_UNNAMED_MODULE");
                allUnnamedModuleField.setAccessible(true);
                allUnnamedModule = (Module) allUnnamedModuleField.get(null);

                moduleDescriptorField = findFieldByName(moduleClassFields, "descriptor");
                moduleLayerField = findFieldByName(moduleClassFields, "layer");
                moduleLoaderField = findFieldByName(moduleClassFields, "loader");
                moduleReadsField = findFieldByName(moduleClassFields, "reads");
                moduleOpenPackagesField = findFieldByName(moduleClassFields, "openPackages");
                moduleExportedPackagesField = findFieldByName(moduleClassFields, "exportedPackages");
                moduleDescriptorField.setAccessible(true);
                moduleLayerField.setAccessible(true);
                moduleLoaderField.setAccessible(true);
                moduleReadsField.setAccessible(true);
                moduleOpenPackagesField.setAccessible(true);
                moduleExportedPackagesField.setAccessible(true);

                allUnnamedModuleSet = new HashSet<>();
                allUnnamedModuleSet.add(allUnnamedModule);
                patchModuleLoaderField(allUnnamedModule, imageClassLoader.getClassLoader());
                everyoneSet = new HashSet<>();
                everyoneSet.add(everyoneModule);

                moduleConstructor = ReflectionUtil.lookupConstructor(Module.class, ClassLoader.class, ModuleDescriptor.class);
                moduleFindModuleMethod = ReflectionUtil.lookupMethod(Module.class, "findModule", String.class, Map.class, Map.class, List.class);
            } catch (ReflectiveOperationException | NoSuchElementException ex) {
                throw VMError.shouldNotReachHere("Failed to retrieve fields of the Module class.", ex);
            }
        }

        /**
         * A manual field lookup is necessary due to reflection filters present in newer JDK
         * versions. This method should be removed once {@link ReflectionUtil} becomes immune to
         * reflection filters.
         */
        private static Field findFieldByName(Field[] fields, String name) {
            return Arrays.stream(fields).filter(f -> f.getName().equals(name)).findAny().orElseThrow(VMError::shouldNotReachHere);
        }

        public Module getOrCreateRuntimeModuleForHostedModule(Module hostedModule, ModuleDescriptor runtimeModuleDescriptor) {
            if (hostedModule.isNamed()) {
                return getOrCreateRuntimeModuleForHostedModule(hostedModule.getName(), runtimeModuleDescriptor);
            }

            /*
             * EVERYONE and ALL_UNNAMED modules are unnamed module instances that are used as
             * markers throughout the JDK and therefore we need them in the image heap.
             *
             * We make an optimization that all hosted unnamed modules except EVERYONE module have
             * the same runtime unnamed module. This does not break the module visibility semantics
             * as unnamed modules can access all named modules, and visibility modifications that
             * include unnamed modules do not depend on the actual instance, but only on the fact
             * that the module is unnamed e.g., calling addExports from/to an unnamed module will do
             * nothing.
             */

            if (hostedModule == everyoneModule) {
                return everyoneModule;
            }

            return allUnnamedModule;
        }

        public Module getOrCreateRuntimeModuleForHostedModule(String hostedModuleName, ModuleDescriptor runtimeModuleDescriptor) {
            if (nameToModuleLookup.containsKey(hostedModuleName)) {
                return nameToModuleLookup.get(hostedModuleName);
            } else {
                Module runtimeModule;
                try {
                    runtimeModule = moduleConstructor.newInstance(imageClassLoader.getClassLoader(), runtimeModuleDescriptor);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw VMError.shouldNotReachHere("Failed to reflectively construct a runtime Module object.", ex);
                }
                nameToModuleLookup.put(hostedModuleName, runtimeModule);
                return runtimeModule;
            }
        }

        /**
         * This method creates Module instances that will populate the runtime boot module layer of
         * the image. This implementation is copy-pasted from Module#defineModules(Configuration,
         * Function, ModuleLayer) with few simplifications (removing multiple classloader support)
         * and removal of VM state updates (otherwise we would be re-defining modules to the host
         * VM).
         */
        Map<String, Module> synthesizeNameToModule(ModuleLayer runtimeBootLayer)
                        throws IllegalAccessException, InvocationTargetException {
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
                Module m = getOrCreateRuntimeModuleForHostedModule(name, descriptor);
                if (!descriptor.equals(m.getDescriptor())) {
                    moduleDescriptorField.set(m, descriptor);
                }
                patchModuleLayerField(m, runtimeBootLayer);
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

                if (descriptor.isAutomatic()) {
                    reads.add(allUnnamedModule);
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

        @SuppressWarnings("unchecked")
        void addReads(Module module, Module other) throws IllegalAccessException {
            Set<Module> reads = (Set<Module>) moduleReadsField.get(module);
            if (reads == null) {
                reads = new HashSet<>();
                moduleReadsField.set(module, reads);
            }
            reads.add(other == null ? allUnnamedModule : other);
        }

        @SuppressWarnings("unchecked")
        void addExports(Module module, String pn, Module other) throws IllegalAccessException {
            if (other != null && module.isExported(pn, other)) {
                return;
            }

            Map<String, Set<Module>> exports = (Map<String, Set<Module>>) moduleExportedPackagesField.get(module);
            if (exports == null) {
                exports = new HashMap<>();
                moduleExportedPackagesField.set(module, exports);
            }

            Set<Module> prev;
            if (other == null) {
                prev = exports.putIfAbsent(pn, allUnnamedModuleSet);
            } else {
                HashSet<Module> targets = new HashSet<>();
                targets.add(other);
                prev = exports.putIfAbsent(pn, targets);
            }

            if (prev != null) {
                prev.add(other == null ? allUnnamedModule : other);
            }
        }

        @SuppressWarnings("unchecked")
        void addOpens(Module module, String pn, Module other) throws IllegalAccessException {
            if (other != null && module.isOpen(pn, other)) {
                return;
            }

            Map<String, Set<Module>> opens = (Map<String, Set<Module>>) moduleOpenPackagesField.get(module);
            if (opens == null) {
                opens = new HashMap<>();
                moduleOpenPackagesField.set(module, opens);
            }

            Set<Module> prev;
            if (other == null) {
                prev = opens.putIfAbsent(pn, allUnnamedModuleSet);
            } else {
                HashSet<Module> targets = new HashSet<>();
                targets.add(other);
                prev = opens.putIfAbsent(pn, targets);
            }

            if (prev != null) {
                prev.add(other == null ? allUnnamedModule : other);
            }
        }

        void patchModuleLayerField(Module module, ModuleLayer runtimeBootLayer) throws IllegalAccessException {
            moduleLayerField.set(module, runtimeBootLayer);
        }

        void patchModuleLoaderField(Module module, ClassLoader loader) throws IllegalAccessException {
            moduleLoaderField.set(module, loader);
        }
    }
}
