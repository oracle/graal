/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHereAtRuntime;

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
import java.net.URI;
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

import jdk.internal.module.DefaultRoots;
import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.ModuleReferenceImpl;
import jdk.internal.module.SystemModuleFinders;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.RuntimeModuleSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AnalysisAccessBase;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.LogUtils;
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
@SuppressWarnings("unused")
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
        ModuleLayer runtimeBootLayer = synthesizeRuntimeModuleLayer(new ArrayList<>(List.of(ModuleLayer.empty())), accessImpl, accessImpl.imageClassLoader, baseModules, Set.of(), clf, null);
        RuntimeModuleSupport.instance().setBootLayer(runtimeBootLayer);
        RuntimeModuleSupport.instance().setHostedToRuntimeModuleMapper(moduleLayerFeatureUtils::getOrCreateRuntimeModuleForHostedModule);

        /*
         * Register an object replacer that will ensure all references to hosted module instances
         * are replaced with the appropriate runtime module instance.
         */
        access.registerObjectReplacer(this::replaceHostedModules);
    }

    private Object replaceHostedModules(Object source) {
        if (source instanceof Module module) {
            return moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(module);
        }
        return source;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeModuleSupport.class, new RuntimeModuleSupport());

        List<Module> bootLayerAutomaticModules = ModuleLayer.boot().modules()
                        .stream()
                        .filter(m -> m.isNamed() && m.getDescriptor().isAutomatic())
                        .toList();
        if (!bootLayerAutomaticModules.isEmpty()) {
            LogUtils.warning(
                            "Detected automatic module(s) on the module-path of the image builder:\n%s\nExtending the image builder with automatic modules is not supported and might result in failed build. " +
                                            "This is probably caused by specifying a jar-file that is not a proper module on the module-path. " +
                                            "Please ensure that only proper modules are found on the module-path.",
                            bootLayerAutomaticModules.stream().map(ModuleLayerFeatureUtils::formatModule).collect(Collectors.joining(System.lineSeparator())));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        scanRuntimeBootLayerPrototype((BeforeAnalysisAccessImpl) access);

        access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleReferenceLocationField, ModuleLayerFeatureUtils.ResetModuleReferenceLocation.INSTANCE);
        access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleReferenceImplLocationField, ModuleLayerFeatureUtils.ResetModuleReferenceLocation.INSTANCE);
    }

    /**
     * Generate a temporary module layer to serve as a prototype object for
     * {@link RuntimeModuleSupport}.bootLayer. The value doesn't need to actually be set in the
     * field, just scanned such that the analysis sees the deep type hierarchy. The field value
     * wouldn't be processed during analysis anyway since the field is annotated with
     * {@link UnknownObjectField}, only the field declared type is injected in the type flow graphs.
     * The concrete value is set in {@link ModuleLayerFeature#afterAnalysis}. Later when the field
     * is read the lazy value supplier scans the concrete value and patches the shadow heap.
     */
    private void scanRuntimeBootLayerPrototype(BeforeAnalysisAccessImpl accessImpl) {
        Set<String> baseModules = ModuleLayer.boot().modules().stream().map(Module::getName).collect(Collectors.toSet());
        Function<String, ClassLoader> clf = moduleLayerFeatureUtils::getClassLoaderForBootLayerModule;
        ModuleLayer runtimeBootLayer = synthesizeRuntimeModuleLayer(new ArrayList<>(List.of(ModuleLayer.empty())), accessImpl, accessImpl.imageClassLoader, baseModules, Set.of(), clf, null);
        /* Only scan the value if module support is enabled and bootLayer field is reachable. */
        accessImpl.registerReachabilityHandler((a) -> accessImpl.rescanObject(runtimeBootLayer), ReflectionUtil.lookupField(RuntimeModuleSupport.class, "bootLayer"));
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        AfterAnalysisAccessImpl accessImpl = (AfterAnalysisAccessImpl) access;

        Set<Module> runtimeImageNamedModules = accessImpl.getUniverse().getTypes()
                        .stream()
                        .filter(ModuleLayerFeature::typeIsReachable)
                        .map(t -> t.getJavaClass().getModule())
                        .filter(Module::isNamed)
                        .collect(Collectors.toSet());

        /*
         * Parse explicitly added modules via --add-modules. This is done early as this information
         * is required when filtering the analysis reachable module set.
         */
        Set<String> extraModules = ModuleLayerFeatureUtils.parseModuleSetModifierProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES);
        extraModules.addAll(Resources.getIncludedResourcesModules());
        extraModules.stream().filter(Predicate.not(ModuleSupport.nonExplicitModules::contains)).forEach(moduleName -> {
            Optional<?> module = accessImpl.imageClassLoader.findModule(moduleName);
            if (module.isEmpty()) {
                throw VMError.shouldNotReachHere("Explicitly required module " + moduleName + " is not available");
            }
            runtimeImageNamedModules.add((Module) module.get());
        });

        Set<Module> analysisReachableSyntheticModules = runtimeImageNamedModules
                        .stream()
                        .filter(ModuleLayerFeatureUtils::isModuleSynthetic)
                        .collect(Collectors.toSet());

        /*
         * Find reachable module layers and process them in order of distance from the boot module
         * layer. This order is important because in order to synthesize a module layer, all of its
         * parent module layers also need to be synthesized as well.
         */
        List<ModuleLayer> reachableModuleLayers = runtimeImageNamedModules
                        .stream()
                        .map(Module::getLayer)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.comparingInt(ModuleLayerFeatureUtils::distanceFromBootModuleLayer))
                        .collect(Collectors.toList());

        /*
         * Remove once GR-44584 is merged. See
         * com.oracle.svm.driver.NativeImage.BuildConfiguration.getImageProvidedJars().
         */
        if (!accessImpl.imageClassLoader.applicationClassPath().isEmpty()) {
            extraModules.add("ALL-MODULE-PATH");
        }

        Set<String> rootModules = calculateRootModules(extraModules);
        List<ModuleLayer> runtimeModuleLayers = synthesizeRuntimeModuleLayers(accessImpl, reachableModuleLayers, runtimeImageNamedModules, analysisReachableSyntheticModules, rootModules);
        ModuleLayer runtimeBootLayer = runtimeModuleLayers.get(0);
        RuntimeModuleSupport.instance().setBootLayer(runtimeBootLayer);

        /*
         * Ensure that runtime modules have the same relations (i.e., reads, opens and exports) as
         * the originals.
         */
        replicateVisibilityModifications(runtimeBootLayer, accessImpl, accessImpl.imageClassLoader, runtimeImageNamedModules);
        replicateNativeAccess(accessImpl, runtimeImageNamedModules);
    }

    /**
     * This method is a custom version of jdk.internal.module.ModuleBootstrap#boot2() used to
     * compute the root module set that should be seen at image runtime. It reuses the same methods
     * as the original (via reflective invokes).
     */
    private Set<String> calculateRootModules(Collection<String> addModules) {
        ModuleFinder upgradeModulePath = NativeImageClassLoaderSupport.finderFor("jdk.module.upgrade.path");
        ModuleFinder appModulePath = moduleLayerFeatureUtils.getAppModuleFinder();
        String mainModule = ModuleLayerFeatureUtils.getMainModuleName();
        Set<String> limitModules = ModuleLayerFeatureUtils.parseModuleSetModifierProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_LIMITED_MODULES);

        Object systemModules = null;
        ModuleFinder systemModuleFinder;

        boolean haveModulePath = appModulePath != null || upgradeModulePath != null;

        if (!haveModulePath && addModules.isEmpty() && limitModules.isEmpty()) {
            systemModules = moduleLayerFeatureUtils.invokeSystemModuleFinderSystemModules(mainModule);
        }
        if (systemModules == null) {
            systemModules = moduleLayerFeatureUtils.invokeSystemModuleFinderAllSystemModules();
        }
        if (systemModules != null) {
            systemModuleFinder = moduleLayerFeatureUtils.invokeSystemModuleFinderOf(systemModules);
        } else {
            systemModuleFinder = SystemModuleFinders.ofSystem();
        }

        /*
         * We need to include module roots required for Native Image to work at runtime.
         */
        ModuleFinder builderModuleFinder = NativeImageClassLoaderSupport.finderFor("jdk.module.path");
        if (builderModuleFinder != null) {
            systemModuleFinder = ModuleFinder.compose(systemModuleFinder, builderModuleFinder);
        }

        if (upgradeModulePath != null) {
            systemModuleFinder = ModuleFinder.compose(upgradeModulePath, systemModuleFinder);
        }

        ModuleFinder finder;
        if (appModulePath != null) {
            finder = ModuleFinder.compose(systemModuleFinder, appModulePath);
        } else {
            finder = systemModuleFinder;
        }

        Set<String> roots = new HashSet<>();

        if (mainModule != null) {
            roots.add(mainModule);
        }

        boolean addAllDefaultModules = false;
        boolean addAllSystemModules = false;
        boolean addAllApplicationModules = false;
        for (String mod : addModules) {
            switch (mod) {
                case ModuleSupport.MODULE_SET_ALL_DEFAULT:
                    addAllDefaultModules = true;
                    break;
                case ModuleSupport.MODULE_SET_ALL_SYSTEM:
                    addAllSystemModules = true;
                    break;
                case ModuleSupport.MODULE_SET_ALL_MODULE_PATH:
                    addAllApplicationModules = true;
                    break;
                default:
                    roots.add(mod);
            }
        }

        if (!limitModules.isEmpty()) {
            finder = moduleLayerFeatureUtils.invokeModuleBootstrapLimitFinder(finder, limitModules, roots);
        }

        if (mainModule == null || addAllDefaultModules) {
            roots.addAll(moduleLayerFeatureUtils.invokeDefaultRootsComputeMethod(systemModuleFinder, finder));
        }

        if (addAllSystemModules) {
            ModuleFinder f = finder;
            systemModuleFinder.findAll()
                            .stream()
                            .map(ModuleReference::descriptor)
                            .map(ModuleDescriptor::name)
                            .filter(mn -> f.find(mn).isPresent())
                            .forEach(roots::add);
        }

        if (appModulePath != null && addAllApplicationModules) {
            ModuleFinder f = finder;
            appModulePath.findAll()
                            .stream()
                            .map(ModuleReference::descriptor)
                            .map(ModuleDescriptor::name)
                            .filter(mn -> f.find(mn).isPresent())
                            .forEach(roots::add);
        }

        return roots;
    }

    private static boolean typeIsReachable(AnalysisType t) {
        return t.isReachable() && !t.isArray();
    }

    /*
     * Creates a stream of module names that are reachable from a given module through "requires"
     */
    private static Stream<String> extractRequiredModuleNames(Module m) {
        Stream<String> requiredModules = m.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name);
        return Stream.concat(Stream.of(m.getName()), requiredModules);
    }

    private List<ModuleLayer> synthesizeRuntimeModuleLayers(AfterAnalysisAccessImpl accessImpl, List<ModuleLayer> hostedModuleLayers, Collection<Module> reachableNamedModules,
                    Collection<Module> reachableSyntheticModules, Collection<String> rootModuleNames) {
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
            if (hostedModuleLayer == accessImpl.imageClassLoader.classLoaderSupport.moduleLayerForImageBuild) {
                continue;
            }

            boolean isBootModuleLayer = hostedModuleLayer == ModuleLayer.boot();

            Set<String> moduleNames = hostedModuleLayer
                            .modules()
                            .stream()
                            .map(Module::getName)
                            .collect(Collectors.toSet());
            moduleNames.retainAll(allReachableAndRequiredModuleNames);
            if (isBootModuleLayer) {
                moduleNames.addAll(rootModuleNames);
            }

            Set<Module> syntheticModules = new HashSet<>();
            if (isBootModuleLayer) {
                syntheticModules.addAll(reachableSyntheticModules);
            }

            List<ModuleLayer> parents = hostedModuleLayer.parents().stream().map(moduleLayerPairs::get).collect(Collectors.toList());

            Configuration cf = isBootModuleLayer ? null : hostedModuleLayer.configuration();
            Function<String, ClassLoader> clf = name -> moduleLayerFeatureUtils.getClassLoaderForModuleInModuleLayer(hostedModuleLayer, name);

            ModuleLayer runtimeModuleLayer = synthesizeRuntimeModuleLayer(parents, accessImpl, accessImpl.imageClassLoader, moduleNames, syntheticModules, clf, cf);
            moduleLayerPairs.put(hostedModuleLayer, runtimeModuleLayer);
        }

        moduleLayerPairs.remove(ModuleLayer.empty());
        return new ArrayList<>(moduleLayerPairs.values());
    }

    private ModuleLayer synthesizeRuntimeModuleLayer(List<ModuleLayer> parentLayers, AnalysisAccessBase accessImpl, ImageClassLoader cl, Set<String> reachableModules,
                    Set<Module> syntheticModules, Function<String, ClassLoader> clf, Configuration cfOverride) {
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
            Map<String, Module> nameToModule = moduleLayerFeatureUtils.synthesizeNameToModule(accessImpl, runtimeModuleLayer, clf);
            for (Module syntheticModule : syntheticModules) {
                Module runtimeSyntheticModule = moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(syntheticModule);
                nameToModule.putIfAbsent(runtimeSyntheticModule.getName(), runtimeSyntheticModule);
                moduleLayerFeatureUtils.patchModuleLayerField(accessImpl, runtimeSyntheticModule, runtimeModuleLayer);
            }
            patchRuntimeModuleLayer(accessImpl, runtimeModuleLayer, nameToModule, parentLayers);
            accessImpl.rescanField(runtimeModuleLayer, moduleLayerFeatureUtils.moduleLayerModulesField);
            return runtimeModuleLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime module layer: " + runtimeModuleLayer, ex);
        }
    }

    private void replicateVisibilityModifications(ModuleLayer runtimeBootLayer, AfterAnalysisAccessImpl accessImpl, ImageClassLoader cl, Set<Module> analysisReachableNamedModules) {
        List<Module> applicationModules = findApplicationModules(runtimeBootLayer, cl.applicationModulePath());

        Map<Module, Module> modulePairs = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(m -> m, m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));
        modulePairs.put(moduleLayerFeatureUtils.allUnnamedModule, moduleLayerFeatureUtils.allUnnamedModule);
        modulePairs.put(moduleLayerFeatureUtils.everyoneModule, moduleLayerFeatureUtils.everyoneModule);

        Module builderModule = ModuleLayerFeatureUtils.getBuilderModule();
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
                        moduleLayerFeatureUtils.addReads(accessImpl, runtimeFrom, runtimeTo);
                        if (hostedFrom == builderModule) {
                            for (Module appModule : applicationModules) {
                                moduleLayerFeatureUtils.addReads(accessImpl, appModule, runtimeTo);
                            }
                        }
                    }
                    for (String pn : runtimeFrom.getPackages()) {
                        if (ModuleLayerFeatureUtils.isModuleSynthetic(hostedFrom) || hostedFrom.isOpen(pn, hostedTo)) {
                            moduleLayerFeatureUtils.addOpens(accessImpl, runtimeFrom, pn, runtimeTo);
                            if (hostedTo == builderModule) {
                                for (Module appModule : applicationModules) {
                                    moduleLayerFeatureUtils.addOpens(accessImpl, runtimeFrom, pn, appModule);
                                }
                            }
                        }
                        if (ModuleLayerFeatureUtils.isModuleSynthetic(hostedFrom) || hostedFrom.isExported(pn, hostedTo)) {
                            moduleLayerFeatureUtils.addExports(accessImpl, runtimeFrom, pn, runtimeTo);
                            if (hostedTo == builderModule) {
                                for (Module appModule : applicationModules) {
                                    moduleLayerFeatureUtils.addExports(accessImpl, runtimeFrom, pn, appModule);
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

    private void replicateNativeAccess(AfterAnalysisAccessImpl accessImpl, Set<Module> analysisReachableNamedModules) {
        if (JavaVersionUtil.JAVA_SPEC < 19) {
            return;
        }

        Map<Module, Module> modulePairs = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(m -> m, m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));

        Module builderModule = ModuleLayerFeatureUtils.getBuilderModule();
        assert builderModule != null;

        for (Map.Entry<Module, Module> modulesPair : modulePairs.entrySet()) {
            Module hosted = modulesPair.getKey();
            Module runtime = modulesPair.getValue();
            if (moduleLayerFeatureUtils.allowsNativeAccess(hosted)) {
                moduleLayerFeatureUtils.setNativeAccess(accessImpl, runtime, true);
            }
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
                            .toList();
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

    private void patchRuntimeModuleLayer(AnalysisAccessBase accessImpl, ModuleLayer runtimeModuleLayer, Map<String, Module> nameToModule, List<ModuleLayer> parents) {
        try {
            moduleLayerFeatureUtils.patchModuleLayerNameToModuleField(accessImpl, runtimeModuleLayer, nameToModule);
            moduleLayerFeatureUtils.patchModuleLayerParentsField(accessImpl, runtimeModuleLayer, parents);
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }

        // Ensure that the lazy modules field gets set
        runtimeModuleLayer.modules();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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
        private final Field moduleEnableNativeAccessField;
        private final Method moduleFindModuleMethod;
        private final Method systemModuleFindersAllSystemModulesMethod;
        private final Method systemModuleFindersOfMethod;
        private final Method systemModuleFindersSystemModulesMethod;
        private final Method moduleBootstrapLimitFinderMethod;
        private final Method defaultRootsComputeMethod;
        private final Constructor<ModuleLayer> moduleLayerConstructor;
        private final Field moduleLayerNameToModuleField;
        private final Field moduleLayerParentsField;
        private final Field moduleLayerModulesField;
        private final Field moduleReferenceLocationField;
        private final Field moduleReferenceImplLocationField;

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
                // Only present on JDK 19+
                moduleEnableNativeAccessField = findFieldByName(moduleClassFields, "enableNativeAccess", true);
                moduleDescriptorField.setAccessible(true);
                moduleLayerField.setAccessible(true);
                moduleLoaderField.setAccessible(true);
                moduleReadsField.setAccessible(true);
                moduleOpenPackagesField.setAccessible(true);
                moduleExportedPackagesField.setAccessible(true);
                if (moduleEnableNativeAccessField != null) {
                    moduleEnableNativeAccessField.setAccessible(true);
                }

                allUnnamedModuleSet = new HashSet<>(1);
                allUnnamedModuleSet.add(allUnnamedModule);
                patchModuleLoaderField(allUnnamedModule, imageClassLoader.getClassLoader());
                everyoneSet = new HashSet<>(1);
                everyoneSet.add(everyoneModule);

                moduleConstructor = ReflectionUtil.lookupConstructor(Module.class, ClassLoader.class, ModuleDescriptor.class);
                moduleFindModuleMethod = ReflectionUtil.lookupMethod(Module.class, "findModule", String.class, Map.class, Map.class, List.class);

                systemModuleFindersAllSystemModulesMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "allSystemModules");
                systemModuleFindersOfMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "of", Class.forName("jdk.internal.module.SystemModules"));
                systemModuleFindersSystemModulesMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "systemModules", String.class);

                moduleBootstrapLimitFinderMethod = ReflectionUtil.lookupMethod(ModuleBootstrap.class, "limitFinder", ModuleFinder.class, Set.class, Set.class);

                defaultRootsComputeMethod = ReflectionUtil.lookupMethod(DefaultRoots.class, "compute", ModuleFinder.class, ModuleFinder.class);

                moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
                moduleLayerNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
                moduleLayerParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
                moduleLayerModulesField = ReflectionUtil.lookupField(ModuleLayer.class, "modules");
                moduleReferenceLocationField = ReflectionUtil.lookupField(ModuleReference.class, "location");
                moduleReferenceImplLocationField = ReflectionUtil.lookupField(ModuleReferenceImpl.class, "location");
            } catch (ReflectiveOperationException | NoSuchElementException ex) {
                throw VMError.shouldNotReachHere("Failed to retrieve fields of the Module/ModuleLayer class.", ex);
            }
        }

        /**
         * A manual field lookup is necessary due to reflection filters present in newer JDK
         * versions. This method should be removed once {@link ReflectionUtil} becomes immune to
         * reflection filters.
         */
        private static Field findFieldByName(Field[] fields, String name, boolean optional) {
            var res = Arrays.stream(fields).filter(f -> f.getName().equals(name)).findAny();
            if (res.isPresent()) {
                return res.get();
            } else if (optional) {
                return null;
            } else {
                throw shouldNotReachHereAtRuntime();
            }
        }

        private static Field findFieldByName(Field[] fields, String name) {
            return findFieldByName(fields, name, false);
        }

        private static boolean isModuleSynthetic(Module m) {
            return m.getDescriptor() != null && m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC);
        }

        static String formatModule(Module module) {
            if (!module.isNamed()) {
                return module.toString();
            }
            Optional<ResolvedModule> optionalResolvedModule = module.getLayer().configuration().findModule(module.getName());
            assert optionalResolvedModule.isPresent();
            ResolvedModule resolvedModule = optionalResolvedModule.get();
            Optional<URI> location = resolvedModule.reference().location();
            if (location.isPresent()) {
                return module + ", location: " + location;
            } else {
                return module.toString();
            }
        }

        static Set<String> parseModuleSetModifierProperty(String prop) {
            Set<String> specifiedModules = new HashSet<>();
            String args = System.getProperty(prop, "");
            if (!args.isEmpty()) {
                specifiedModules.addAll(Arrays.asList(SubstrateUtil.split(args, ",")));
            }
            return specifiedModules;
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

        public static Module getBuilderModule() {
            return ModuleLayerFeature.class.getModule();
        }

        public static String getMainModuleName() {
            String mainModule = SubstrateOptions.Module.getValue();
            return mainModule.isEmpty() ? null : mainModule;
        }

        public ModuleFinder getAppModuleFinder() {
            List<Path> appModulePath = imageClassLoader.applicationModulePath();
            if (appModulePath.isEmpty()) {
                return null;
            } else {
                return ModuleFinder.of(appModulePath.toArray(new Path[0]));
            }
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
                    throw VMError.shouldNotReachHere(
                                    "Failed to find runtime module for hosted module " + hostedModuleName + ". No runtime modules have been registered for class loader: " + loader);
                }
            }
            Module runtimeModule = loaderRuntimeModules.get(hostedModuleName);
            if (runtimeModule == null) {
                if (optional) {
                    return null;
                } else {
                    throw VMError.shouldNotReachHere("Runtime module " + hostedModuleName + " is not registered for class loader: " + loader);
                }
            } else {
                return runtimeModule;
            }
        }

        public Module getOrCreateRuntimeModuleForHostedModule(Module hostedModule) {
            if (hostedModule.isNamed()) {
                return getOrCreateRuntimeModuleForHostedModule(hostedModule.getClassLoader(), hostedModule.getName(), hostedModule.getDescriptor());
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
        Map<String, Module> synthesizeNameToModule(AnalysisAccessBase access, ModuleLayer runtimeModuleLayer, Function<String, ClassLoader> clf)
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
                    access.rescanField(m, moduleDescriptorField);
                }
                patchModuleLayerField(access, m, runtimeModuleLayer);
                nameToModule.put(name, m);
            }

            /*
             * Setup readability and exports/opens. This part is unchanged, save for field setters
             * and VM update removals. Exports, reads, and opens collections are tightly packed as
             * they aren't likely to grow after the initial setup.
             */
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                ModuleDescriptor descriptor = mref.descriptor();

                String mn = descriptor.name();
                Module m = nameToModule.get(mn);
                assert m != null;

                Set<Module> reads = new HashSet<>(resolvedModule.reads().size());
                for (ResolvedModule other : resolvedModule.reads()) {
                    Module m2 = nameToModule.get(other.name());
                    reads.add(m2);
                }

                if (descriptor.isAutomatic()) {
                    reads.add(allUnnamedModule);
                }
                moduleReadsField.set(m, reads);
                access.rescanField(m, moduleReadsField);

                if (!descriptor.isOpen() && !descriptor.isAutomatic()) {
                    if (descriptor.opens().isEmpty()) {
                        Map<String, Set<Module>> exportedPackages = new HashMap<>(m.getDescriptor().exports().size());
                        for (ModuleDescriptor.Exports exports : m.getDescriptor().exports()) {
                            String source = exports.source();
                            if (exports.isQualified()) {
                                Set<Module> targets = new HashSet<>(exports.targets().size());
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
                        access.rescanField(m, moduleExportedPackagesField);
                    } else {
                        Map<String, Set<Module>> openPackages = new HashMap<>(descriptor.opens().size());
                        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
                            String source = opens.source();
                            if (opens.isQualified()) {
                                Set<Module> targets = new HashSet<>(opens.targets().size());
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

                        Map<String, Set<Module>> exportedPackages = new HashMap<>(descriptor.exports().size());
                        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
                            String source = exports.source();
                            Set<Module> openToTargets = openPackages.get(source);
                            if (openToTargets != null && openToTargets.contains(everyoneModule)) {
                                continue;
                            }

                            if (exports.isQualified()) {
                                Set<Module> targets = new HashSet<>(exports.targets().size());
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
                        access.rescanField(m, moduleOpenPackagesField);
                        moduleExportedPackagesField.set(m, exportedPackages);
                        access.rescanField(m, moduleExportedPackagesField);
                    }
                }
                access.rescanObject(m);
            }

            return nameToModule;
        }

        @SuppressWarnings("unchecked")
        void addReads(AfterAnalysisAccessImpl accessImpl, Module module, Module other) throws IllegalAccessException {
            Set<Module> reads = (Set<Module>) moduleReadsField.get(module);
            if (reads == null) {
                reads = new HashSet<>(1);
                moduleReadsField.set(module, reads);
            }
            reads.add(other == null ? allUnnamedModule : other);
            accessImpl.rescanField(module, moduleReadsField);
        }

        @SuppressWarnings("unchecked")
        void addExports(AfterAnalysisAccessImpl accessImpl, Module module, String pn, Module other) throws IllegalAccessException {
            if (other != null && module.isExported(pn, other)) {
                return;
            }

            Map<String, Set<Module>> exports = (Map<String, Set<Module>>) moduleExportedPackagesField.get(module);
            if (exports == null) {
                exports = new HashMap<>(1);
                moduleExportedPackagesField.set(module, exports);
            }

            Set<Module> prev;
            if (other == null) {
                prev = exports.putIfAbsent(pn, allUnnamedModuleSet);
            } else {
                HashSet<Module> targets = new HashSet<>(1);
                targets.add(other);
                prev = exports.putIfAbsent(pn, targets);
            }

            if (prev != null) {
                prev.add(other == null ? allUnnamedModule : other);
            }
            accessImpl.rescanField(module, moduleExportedPackagesField);
        }

        @SuppressWarnings("unchecked")
        void addOpens(AfterAnalysisAccessImpl accessImpl, Module module, String pn, Module other) throws IllegalAccessException {
            if (other != null && module.isOpen(pn, other)) {
                return;
            }

            Map<String, Set<Module>> opens = (Map<String, Set<Module>>) moduleOpenPackagesField.get(module);
            if (opens == null) {
                opens = new HashMap<>(1);
                moduleOpenPackagesField.set(module, opens);
            }

            Set<Module> prev;
            if (other == null) {
                prev = opens.putIfAbsent(pn, allUnnamedModuleSet);
            } else {
                HashSet<Module> targets = new HashSet<>(1);
                targets.add(other);
                prev = opens.putIfAbsent(pn, targets);
            }

            if (prev != null) {
                prev.add(other == null ? allUnnamedModule : other);
            }
            accessImpl.rescanField(module, moduleOpenPackagesField);
        }

        void patchModuleLayerField(AnalysisAccessBase accessImpl, Module module, ModuleLayer runtimeBootLayer) throws IllegalAccessException {
            moduleLayerField.set(module, runtimeBootLayer);
            accessImpl.rescanField(module, moduleLayerField);
        }

        void patchModuleLoaderField(Module module, ClassLoader loader) throws IllegalAccessException {
            moduleLoaderField.set(module, loader);
        }

        ModuleLayer createNewModuleLayerInstance(Configuration cf) throws InvocationTargetException, InstantiationException, IllegalAccessException {
            return moduleLayerConstructor.newInstance(cf, List.of(), null);
        }

        void patchModuleLayerNameToModuleField(AnalysisAccessBase accessImpl, ModuleLayer moduleLayer, Map<String, Module> nameToModule) throws IllegalAccessException {
            moduleLayerNameToModuleField.set(moduleLayer, nameToModule);
            accessImpl.rescanField(moduleLayer, moduleLayerNameToModuleField);
        }

        void patchModuleLayerParentsField(AnalysisAccessBase accessImpl, ModuleLayer moduleLayer, List<ModuleLayer> parents) throws IllegalAccessException {
            moduleLayerParentsField.set(moduleLayer, parents);
            accessImpl.rescanField(moduleLayer, moduleLayerParentsField);
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

        Object invokeSystemModuleFinderAllSystemModules() {
            try {
                return systemModuleFindersAllSystemModulesMethod.invoke(null);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively invoke SystemModuleFinders.allSystemModules().", e);
            }
        }

        ModuleFinder invokeSystemModuleFinderOf(Object systemModules) {
            try {
                return (ModuleFinder) systemModuleFindersOfMethod.invoke(null, systemModules);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively invoke SystemModuleFinders.of().", e);
            }
        }

        Object invokeSystemModuleFinderSystemModules(String mainModule) {
            try {
                return systemModuleFindersSystemModulesMethod.invoke(null, mainModule);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively invoke SystemModuleFinders.systemModules().", e);
            }
        }

        ModuleFinder invokeModuleBootstrapLimitFinder(ModuleFinder finder, Set<String> roots, Set<String> otherModules) {
            try {
                return (ModuleFinder) moduleBootstrapLimitFinderMethod.invoke(null, finder, roots, otherModules);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively invoke ModuleBootstrap.limitFinder().", e);
            }
        }

        @SuppressWarnings("unchecked")
        Set<String> invokeDefaultRootsComputeMethod(ModuleFinder finder1, ModuleFinder finder2) {
            try {
                return (Set<String>) defaultRootsComputeMethod.invoke(null, finder1, finder2);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively invoke DefaultRoots.compute().", e);
            }
        }

        /**
         * In the future, this can be replaced by calling Module#isNativeAccessEnabled(). We
         * currently do it this way to still be compatible with older JDKs.
         */
        boolean allowsNativeAccess(Module module) {
            assert moduleEnableNativeAccessField != null : "Only available on JDK19+";
            try {
                return (boolean) moduleEnableNativeAccessField.get(module);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively access Module.enableNativeAccess.", e);
            }

        }

        void setNativeAccess(AfterAnalysisAccessImpl accessImpl, Module module, boolean value) {
            assert moduleEnableNativeAccessField != null : "Only available on JDK19+";
            try {
                moduleEnableNativeAccessField.set(module, value);
                accessImpl.rescanField(module, moduleEnableNativeAccessField);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere("Failed to reflectively set Module.enableNativeAccess.", e);
            }
        }

        /**
         * Patch module references that contain URLs with a non-JRT protocol. Module references can
         * contain URLs that capture hosted directories, e.g.,
         * {@linkplain "file:///home/user/dir/foo.jar"}. See
         * {@link com.oracle.svm.hosted.image.DisallowedImageHeapObjectFeature} for more details on
         * what substrings are detected during the image build.
         */
        static final class ResetModuleReferenceLocation implements FieldValueTransformer {

            static final FieldValueTransformer INSTANCE = new ResetModuleReferenceLocation();

            private ResetModuleReferenceLocation() {
            }

            @Override
            public Object transform(Object receiver, Object originalValue) {
                if (originalValue == null || originalValue.toString().startsWith("jrt://")) {
                    return originalValue;
                } else {
                    return null;
                }
            }
        }
    }
}
