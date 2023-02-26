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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.BootModuleLayerSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This feature:
 * <ul>
 * <li>synthesizes the runtime boot module layer and all reachable module layers initialized at
 * image-build time</li>
 * <li>replicates build-time module relations at runtime (i.e., reads, opens and exports)</li>
 * <li>an object replacer that replaces references to hosted modules with appropriate runtime module
 * instances</li>
 * </ul>
 * <p>
 * This feature synthesizes the runtime module layers by using type reachability information. If a
 * type is reachable, its module is also reachable and therefore that module should be included in
 * the runtime module layer corresponding to the hosted module layer. Additionally, any required
 * modules (e.g., via {@code requires} keyword) will also be included.
 * </p>
 * <p>
 * Modules with the same module name can be loaded by different classloaders, therefore this feature
 * keeps track of the runtime modules, separately for every loader. Mappings from module name to
 * module loader are calculated based on the hosted module layer information. This ensures that the
 * mapping is correct, i.e., there cannot exist two different mappings for the same module name,
 * otherwise the mapping used in the hosted module layer would have the same problem.
 * </p>
 * <p>
 * The configuration for the runtime boot module layer is synthesized using the module reachability
 * data provided to us by the analysis as resolve roots. Runtime boot module configuration is
 * special because it needs to include modules from the module layer used for image build. For all
 * other module layers, it is sufficient to reuse hosted configurations.
 * </p>
 * <p>
 * This feature purposefully avoids public API for module layer creation, such as
 * {@link ModuleLayer#defineModulesWithOneLoader(Configuration, ClassLoader)}, because as a side
 * effect these methods create a new class loader. Instead, this feature uses a private
 * {@link ModuleLayer} constructor to construct the {@link ModuleLayer} instance. This instance
 * needs to be patched to use the runtime module layer configuration and name-to-module mappings.
 * This behavior should be updated if JDK-8277013 gets resolved, which will greatly simplify
 * name-to-module mapping synthesizing.
 * </p>
 */
@AutomaticallyRegisteredFeature
public final class ModuleLayerFeature implements InternalFeature {
    private ModuleLayerFeatureUtils moduleLayerFeatureUtils;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        moduleLayerFeatureUtils = new ModuleLayerFeatureUtils(accessImpl.imageClassLoader);

        /*
         * Generate a temporary module layer to serve as a runtime boot module layer until the
         * analysis is finished.
         */
        Set<String> baseModules = ModuleLayer.boot().modules()
                        .stream()
                        .map(Module::getName)
                        .collect(Collectors.toSet());
        Function<String, ClassLoader> clf = moduleLayerFeatureUtils::getClassLoaderForBootLayerModule;
        ModuleLayer runtimeBootLayer = synthesizeRuntimeModuleLayer(new ArrayList<>(List.of(ModuleLayer.empty())), accessImpl.imageClassLoader, baseModules, Set.of(), clf, null);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);

        /*
         * Register an object replacer that will ensure all references to hosted module instances
         * are replaced with the appropriate runtime module instance.
         */
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

        List<String> nonExplicit = List.of("ALL-DEFAULT", "ALL-SYSTEM", "ALL-MODULE-PATH");
        extraModules.stream().filter(Predicate.not(nonExplicit::contains)).forEach(moduleName -> {
            Optional<?> module = accessImpl.imageClassLoader.findModule(moduleName);
            if (module.isEmpty()) {
                VMError.shouldNotReachHere("Explicitly required module " + moduleName + " is not available");
            }
            analysisReachableNamedModules.add((Module) module.get());
        });

        Set<Module> analysisReachableSyntheticModules = analysisReachableNamedModules
                        .stream()
                        .filter(ModuleLayerFeatureUtils::isModuleSynthetic)
                        .collect(Collectors.toSet());

        /*
         * Find reachable module layers and process them in order of distance from the boot module
         * layer. This order is important because in order to synthesize a module layer, all of its
         * parent module layers also need to be synthesized as well.
         */
        List<ModuleLayer> reachableModuleLayers = analysisReachableNamedModules
                        .stream()
                        .map(Module::getLayer)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.comparingInt(ModuleLayerFeatureUtils::distanceFromBootModuleLayer))
                        .collect(Collectors.toList());

        List<ModuleLayer> runtimeModuleLayers = synthesizeRuntimeModuleLayers(accessImpl, reachableModuleLayers, analysisReachableNamedModules, analysisReachableSyntheticModules);
        ModuleLayer runtimeBootLayer = runtimeModuleLayers.get(0);
        BootModuleLayerSupport.instance().setBootLayer(runtimeBootLayer);

        /*
         * Ensure that runtime modules have the same relations (i.e., reads, opens and exports) as
         * the originals.
         */
        replicateVisibilityModifications(runtimeBootLayer, accessImpl.imageClassLoader, analysisReachableNamedModules);
    }

    /*
     * Creates a stream of module names that are reachable from a given module through "requires"
     */
    private static Stream<String> extractRequiredModuleNames(Module m) {
        Stream<String> requiredModules = m.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name);
        return Stream.concat(Stream.of(m.getName()), requiredModules);
    }

    private List<ModuleLayer> synthesizeRuntimeModuleLayers(FeatureImpl.AfterAnalysisAccessImpl accessImpl, List<ModuleLayer> hostedModuleLayers, Collection<Module> reachableNamedModules,
                    Collection<Module> reachableSyntheticModules) {
        /*
         * Module layer for image build contains modules from the module path that need to be
         * included in the runtime boot module layer. Furthermore, this module layer is not needed
         * at runtime. Because of that, we find its modules ahead of time to include it in the
         * runtime boot module layer.
         */
        ModuleLayer moduleLayerForImageBuild = accessImpl.imageClassLoader.classLoaderSupport.moduleLayerForImageBuild;
        Set<String> moduleLayerForImageBuildModules = moduleLayerForImageBuild
                        .modules()
                        .stream()
                        .map(Module::getName)
                        .collect(Collectors.toSet());

        /*
         * A mapping from hosted to runtime module layers. Used when looking up runtime module layer
         * instances for hosted parent module layers.
         */
        Map<ModuleLayer, ModuleLayer> moduleLayerPairs = new HashMap<>(hostedModuleLayers.size());
        moduleLayerPairs.put(ModuleLayer.empty(), ModuleLayer.empty());

        /*
         * Include explicitly required modules that are not necessarily reachable
         */
        Set<String> allReachableAndRequiredModuleNames = reachableNamedModules
                        .stream()
                        .flatMap(ModuleLayerFeature::extractRequiredModuleNames)
                        .collect(Collectors.toSet());

        for (ModuleLayer hostedModuleLayer : hostedModuleLayers) {
            if (hostedModuleLayer == moduleLayerForImageBuild) {
                continue;
            }

            boolean hostedLayerIsBootModuleLayer = hostedModuleLayer == ModuleLayer.boot();

            Set<String> reachableModuleNamesForHostedModuleLayer = hostedModuleLayer
                            .modules()
                            .stream()
                            .map(Module::getName)
                            .collect(Collectors.toSet());
            if (hostedLayerIsBootModuleLayer) {
                reachableModuleNamesForHostedModuleLayer.addAll(moduleLayerForImageBuildModules);
                Module builderModule = ModuleLayerFeature.class.getModule();
                assert builderModule != null;
                reachableModuleNamesForHostedModuleLayer.remove(builderModule.getName());
            }
            reachableModuleNamesForHostedModuleLayer.retainAll(allReachableAndRequiredModuleNames);

            Function<String, ClassLoader> clf = name -> moduleLayerFeatureUtils.getClassLoaderForModuleInModuleLayer(hostedModuleLayer, name);

            Set<Module> syntheticModules = new HashSet<>();
            if (hostedLayerIsBootModuleLayer) {
                syntheticModules.addAll(reachableSyntheticModules);
            }

            List<ModuleLayer> parents = hostedModuleLayer.parents().stream().map(moduleLayerPairs::get).collect(Collectors.toList());

            Configuration cf = null;
            if (!hostedLayerIsBootModuleLayer) {
                cf = hostedModuleLayer.configuration();
            }
            ModuleLayer runtimeModuleLayer = synthesizeRuntimeModuleLayer(parents, accessImpl.imageClassLoader, reachableModuleNamesForHostedModuleLayer, syntheticModules, clf, cf);
            moduleLayerPairs.put(hostedModuleLayer, runtimeModuleLayer);
        }

        moduleLayerPairs.remove(ModuleLayer.empty());
        return new ArrayList<>(moduleLayerPairs.values());
    }

    private ModuleLayer synthesizeRuntimeModuleLayer(List<ModuleLayer> parentLayers, ImageClassLoader cl, Set<String> reachableModules, Set<Module> syntheticModules,
                    Function<String, ClassLoader> clf, Configuration cfOverride) {
        /**
         * For consistent module lookup we reuse the {@link ModuleFinder}s defined and used in
         * {@link NativeImageClassLoaderSupport}.
         */
        NativeImageClassLoaderSupport classLoaderSupport = cl.classLoaderSupport;
        ModuleFinder beforeFinder = classLoaderSupport.modulepathModuleFinder;
        ModuleFinder afterFinder = classLoaderSupport.upgradeAndSystemModuleFinder;
        Configuration runtimeModuleLayerConfiguration;
        if (cfOverride == null) {
            List<Configuration> parentConfigs = parentLayers.stream().map(ModuleLayer::configuration).collect(Collectors.toList());
            runtimeModuleLayerConfiguration = synthesizeRuntimeModuleLayerConfiguration(beforeFinder, parentConfigs, afterFinder, reachableModules);
        } else {
            runtimeModuleLayerConfiguration = cfOverride;
        }
        ModuleLayer runtimeModuleLayer = null;
        try {
            runtimeModuleLayer = moduleLayerFeatureUtils.createNewModuleLayerInstance(runtimeModuleLayerConfiguration);
            Map<String, Module> nameToModule = moduleLayerFeatureUtils.synthesizeNameToModule(runtimeModuleLayer, clf);
            for (Module syntheticModule : syntheticModules) {
                Module runtimeSyntheticModule = moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(syntheticModule, syntheticModule.getDescriptor());
                nameToModule.putIfAbsent(runtimeSyntheticModule.getName(), runtimeSyntheticModule);
                moduleLayerFeatureUtils.patchModuleLayerField(runtimeSyntheticModule, runtimeModuleLayer);
            }
            patchRuntimeModuleLayer(runtimeModuleLayer, nameToModule, parentLayers);
            return runtimeModuleLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime module layer: " + runtimeModuleLayer, ex);
        }
    }

    private void replicateVisibilityModifications(ModuleLayer runtimeBootLayer, ImageClassLoader cl, Set<Module> analysisReachableNamedModules) {
        List<Module> applicationModules = findApplicationModules(runtimeBootLayer, cl.applicationModulePath());

        Map<Module, Module> modulePairs = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(m -> m, m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));
        modulePairs.put(moduleLayerFeatureUtils.allUnnamedModule, moduleLayerFeatureUtils.allUnnamedModule);
        modulePairs.put(moduleLayerFeatureUtils.everyoneModule, moduleLayerFeatureUtils.everyoneModule);

        Module builderModule = ModuleLayerFeature.class.getModule();
        assert builderModule != null;

        try {
            for (Map.Entry<Module, Module> e1 : modulePairs.entrySet()) {
                Module hostedFrom = e1.getKey();
                if (!hostedFrom.isNamed()) {
                    continue;
                }
                Module runtimeFrom = e1.getValue();
                for (Map.Entry<Module, Module> e2 : modulePairs.entrySet()) {
                    Module hostedTo = e2.getKey();
                    if (hostedTo == hostedFrom) {
                        continue;
                    }
                    Module runtimeTo = e2.getValue();
                    if (ModuleLayerFeatureUtils.isModuleSynthetic(hostedFrom) || hostedFrom.canRead(hostedTo)) {
                        moduleLayerFeatureUtils.addReads(runtimeFrom, runtimeTo);
                        if (hostedFrom == builderModule) {
                            for (Module appModule : applicationModules) {
                                moduleLayerFeatureUtils.addReads(appModule, runtimeTo);
                            }
                        }
                    }
                    for (String pn : runtimeFrom.getPackages()) {
                        if (ModuleLayerFeatureUtils.isModuleSynthetic(hostedFrom) || hostedFrom.isOpen(pn, hostedTo)) {
                            moduleLayerFeatureUtils.addOpens(runtimeFrom, pn, runtimeTo);
                            if (hostedTo == builderModule) {
                                for (Module appModule : applicationModules) {
                                    moduleLayerFeatureUtils.addOpens(runtimeFrom, pn, appModule);
                                }
                            }
                        }
                        if (ModuleLayerFeatureUtils.isModuleSynthetic(hostedFrom) || hostedFrom.isExported(pn, hostedTo)) {
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

    private static Configuration synthesizeRuntimeModuleLayerConfiguration(ModuleFinder beforeFinder, List<Configuration> parentConfigs, ModuleFinder afterFinder, Set<String> reachableModules) {
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

            return Configuration.resolve(beforeFinder, parentConfigs, afterFinder, reachableModules);
        } catch (FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime boot module layer configuration.", ex);
        }
    }

    private void patchRuntimeModuleLayer(ModuleLayer runtimeModuleLayer, Map<String, Module> nameToModule, List<ModuleLayer> parents) {
        try {
            moduleLayerFeatureUtils.patchModuleLayerNameToModuleField(runtimeModuleLayer, nameToModule);
            moduleLayerFeatureUtils.patchModuleLayerParentsField(runtimeModuleLayer, parents);
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }

        // Ensure that the lazy modules field gets set
        runtimeModuleLayer.modules();
    }

    private static final class ModuleLayerFeatureUtils {
        private final Map<ClassLoader, Map<String, Module>> runtimeModules;
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
        private final Constructor<ModuleLayer> moduleLayerConstructor;
        private final Field moduleLayerNameToModuleField;
        private final Field moduleLayerParentsField;

        ModuleLayerFeatureUtils(ImageClassLoader cl) {
            runtimeModules = new HashMap<>();
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

                moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
                moduleLayerNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
                moduleLayerParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
            } catch (ReflectiveOperationException | NoSuchElementException ex) {
                throw VMError.shouldNotReachHere("Failed to retrieve fields of the Module/ModuleLayer class.", ex);
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

        private static boolean isModuleSynthetic(Module m) {
            return m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC);
        }

        static int distanceFromBootModuleLayer(ModuleLayer layer) {
            if (layer == ModuleLayer.boot()) {
                return 0;
            }
            return layer.parents()
                            .stream()
                            .map(p -> 1 + ModuleLayerFeatureUtils.distanceFromBootModuleLayer(p))
                            .max(Integer::compareTo)
                            .orElse(0);
        }

        public Module getRuntimeModuleForHostedModule(Module hostedModule, boolean optional) {
            if (hostedModule.isNamed()) {
                return getRuntimeModuleForHostedModule(hostedModule.getClassLoader(), hostedModule.getName(), optional);
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
            } else {
                return allUnnamedModule;
            }
        }

        public Module getRuntimeModuleForHostedModule(ClassLoader loader, String hostedModuleName, boolean optional) {
            Map<String, Module> loaderRuntimeModules = runtimeModules.get(loader);
            if (loaderRuntimeModules == null) {
                if (optional) {
                    return null;
                } else {
                    throw VMError.shouldNotReachHere("No runtime modules registered for class loader: " + loader);
                }
            }
            Module runtimeModule = loaderRuntimeModules.get(hostedModuleName);
            if (runtimeModule == null) {
                if (optional) {
                    return null;
                } else {
                    throw VMError.shouldNotReachHere("Runtime module " + hostedModuleName + "is not registered for class loader: " + loader);
                }
            } else {
                return runtimeModule;
            }
        }

        public Module getOrCreateRuntimeModuleForHostedModule(Module hostedModule, ModuleDescriptor runtimeModuleDescriptor) {
            if (hostedModule.isNamed()) {
                return getOrCreateRuntimeModuleForHostedModule(hostedModule.getClassLoader(), hostedModule.getName(), runtimeModuleDescriptor);
            } else {
                return hostedModule == everyoneModule ? everyoneModule : allUnnamedModule;
            }
        }

        public Module getOrCreateRuntimeModuleForHostedModule(ClassLoader loader, String hostedModuleName, ModuleDescriptor runtimeModuleDescriptor) {
            synchronized (runtimeModules) {
                Module runtimeModule = getRuntimeModuleForHostedModule(loader, hostedModuleName, true);
                if (runtimeModule != null) {
                    return runtimeModule;
                }

                try {
                    runtimeModule = moduleConstructor.newInstance(loader, runtimeModuleDescriptor);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw VMError.shouldNotReachHere("Failed to reflectively construct a runtime Module object.", ex);
                }
                runtimeModules.putIfAbsent(loader, new HashMap<>());
                runtimeModules.get(loader).put(hostedModuleName, runtimeModule);
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
        Map<String, Module> synthesizeNameToModule(ModuleLayer runtimeModuleLayer, Function<String, ClassLoader> clf)
                        throws IllegalAccessException, InvocationTargetException {
            Configuration cf = runtimeModuleLayer.configuration();

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
                ClassLoader loader = clf.apply(name);
                Module m = getOrCreateRuntimeModuleForHostedModule(loader, name, descriptor);
                if (!descriptor.equals(m.getDescriptor())) {
                    moduleDescriptorField.set(m, descriptor);
                }
                patchModuleLayerField(m, runtimeModuleLayer);
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
                                    Module m2 = (Module) moduleFindModuleMethod.invoke(null, target, Map.of(), nameToModule, runtimeModuleLayer.parents());
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
                                    Module m2 = (Module) moduleFindModuleMethod.invoke(null, target, Map.of(), nameToModule, runtimeModuleLayer.parents());
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

        ModuleLayer createNewModuleLayerInstance(Configuration cf) throws InvocationTargetException, InstantiationException, IllegalAccessException {
            return moduleLayerConstructor.newInstance(cf, List.of(), null);
        }

        void patchModuleLayerNameToModuleField(ModuleLayer moduleLayer, Map<String, Module> nameToModule) throws IllegalAccessException {
            moduleLayerNameToModuleField.set(moduleLayer, nameToModule);
        }

        void patchModuleLayerParentsField(ModuleLayer moduleLayer, List<ModuleLayer> parents) throws IllegalAccessException {
            moduleLayerParentsField.set(moduleLayer, parents);
        }

        ClassLoader getClassLoaderForBootLayerModule(String name) {
            Optional<Module> module = ModuleLayer.boot().findModule(name);
            assert module.isPresent();
            return module.get().getClassLoader();
        }

        ClassLoader getClassLoaderForModuleInModuleLayer(ModuleLayer hostedModuleLayer, String name) {
            Optional<Module> module = hostedModuleLayer.findModule(name);
            return module.isPresent() ? module.get().getClassLoader() : imageClassLoader.getClassLoader();
        }
    }
}
