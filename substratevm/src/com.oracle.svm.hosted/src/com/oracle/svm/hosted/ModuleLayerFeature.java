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

import com.oracle.svm.core.encoder.SymbolEncoder;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.NativeImageClassLoaderOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.ObjectToConstantFieldValueTransformer;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.LayeredModuleSingleton;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.RuntimeClassLoaderValueSupport;
import com.oracle.svm.core.jdk.RuntimeModuleSupport;
import com.oracle.svm.core.util.HostedSubstrateUtil;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AnalysisAccessBase;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistryFeature;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.module.DefaultRoots;
import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.ModuleReferenceImpl;
import jdk.internal.module.ServicesCatalog;
import jdk.internal.module.SystemModuleFinders;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

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
public class ModuleLayerFeature implements InternalFeature {
    private ModuleLayerFeatureUtils moduleLayerFeatureUtils;
    private ModuleLayer bootLayer;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        moduleLayerFeatureUtils = new ModuleLayerFeatureUtils(accessImpl.imageClassLoader);
        Resources.currentLayer().setHostedToRuntimeModuleMapper(m -> moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(m, accessImpl));

        /*
         * Register an object replacer that will ensure all references to hosted module instances
         * are replaced with the appropriate runtime module instance.
         */
        access.registerObjectReplacer(source -> replaceHostedModules(source, accessImpl));
    }

    private Object replaceHostedModules(Object source, AnalysisAccessBase access) {
        if (source instanceof Module module) {
            return moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(module, access);
        } else if (source instanceof Class<?> clazz) {
            /*
             * If the field Class(=DynamicHub).module is not reachable, we do not see all Module
             * instances directly. So we also need to scan the module in Class/DynamicHub objects.
             */
            moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(clazz.getModule(), access);
        } else if (source instanceof DynamicHub hub) {
            moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(hub.getModule(), access);
        }
        return source;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        List<Module> bootLayerAutomaticModules = ModuleLayer.boot().modules()
                        .stream()
                        .filter(m -> m.isNamed() && m.getDescriptor().isAutomatic())
                        .toList();
        if (!bootLayerAutomaticModules.isEmpty()) {
            LogUtils.warning(
                            "Detected automatic module(s) on the module-path of the image builder:%n%s%nExtending the image builder with automatic modules is not supported and might result in failed build. " +
                                            "This is probably caused by specifying a jar-file that is not a proper module on the module-path. " +
                                            "Please ensure that only proper modules are found on the module-path.",
                            bootLayerAutomaticModules.stream().map(ModuleLayerFeatureUtils::formatModule).collect(Collectors.joining(System.lineSeparator())));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            AnalysisType futureType = access.getMetaAccess().lookupJavaType(HashMap.class);
            access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleOpenPackagesField, new LayerPackagesTransformer(PackageType.OPENED, futureType));
            access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleExportedPackagesField, new LayerPackagesTransformer(PackageType.EXPORTED, futureType));
        }

        scanRuntimeBootLayerPrototype(access);

        access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleReferenceLocationField, ModuleLayerFeatureUtils.ResetModuleReferenceLocation.INSTANCE);
        access.registerFieldValueTransformer(moduleLayerFeatureUtils.moduleReferenceImplLocationField, ModuleLayerFeatureUtils.ResetModuleReferenceLocation.INSTANCE);
    }

    /**
     * This transformer delays the Module#open/exportedPackages fields computation until the
     * application layer.
     */
    static class LayerPackagesTransformer implements ObjectToConstantFieldValueTransformer {
        final CrossLayerConstantRegistryFeature registry = CrossLayerConstantRegistryFeature.singleton();
        private final PackageType type;
        private final AnalysisType futureType;

        LayerPackagesTransformer(PackageType packageType, AnalysisType futureType) {
            this.type = packageType;
            this.futureType = futureType;
        }

        @Override
        public JavaConstant transformToConstant(ResolvedJavaField field, Object receiver, Object originalValue, Function<Object, JavaConstant> toConstant) {
            Module module = (Module) receiver;
            if (!LayeredModuleSingleton.singleton().getModules().contains(module)) {
                /*
                 * Modules that are not processed by the LayeredModuleSingleton don't need to be
                 * delayed until the application layer.
                 */
                return toConstant.apply(originalValue);
            }
            /*
             * This key is unique because layered images require all modules to have a different
             * name. This is ensured in LayeredModuleSingleton.setPackages.
             */
            String keyName = type.getModuleKeyName(module);
            if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                /*
                 * Once the constant is finalized, or if the field was not reachable in any previous
                 * layer, the final constant can be computed and returned.
                 */
                return toConstant.apply(originalValue);
            } else {
                if (registry.constantExists(keyName)) {
                    return registry.getConstant(keyName);
                } else {
                    if (ProxyRenamingSubstitutionProcessor.isModuleDynamic(module)) {
                        LogUtils.warning("Dynamic module %s was found in runtime module opens/exports, which might lead to missing relations from the shared layers at runtime", module);
                    }
                    return registry.registerFutureHeapConstant(keyName, futureType);
                }
            }
        }

        @Override
        public boolean isAvailable() {
            /*
             * This transformer needs to be computed after all module relations are finalized in the
             * application layer as transformer are computed only once and the value is then cached.
             */
            return ImageLayerBuildingSupport.buildingSharedLayer() || BuildPhaseProvider.isHostedUniverseBuilt();
        }
    }

    private enum PackageType {

        OPENED("opened"),
        EXPORTED("exported");

        private static final String PACKAGE_TYPE_SEPARATOR = ".";

        private final String packageType;

        PackageType(String packageType) {
            this.packageType = packageType;
        }

        public String getModuleKeyName(Module module) {
            return module.getName() + PACKAGE_TYPE_SEPARATOR + packageType;
        }
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

        Set<Module> runtimeImageModules = accessImpl.getUniverse().getTypes()
                        .stream()
                        .filter(t1 -> !t1.isInBaseLayer() && typeIsReachable(t1))
                        .map(t -> t.getJavaClass().getModule())
                        .collect(Collectors.toSet());

        Set<Module> runtimeImageNamedModules = runtimeImageModules.stream().filter(Module::isNamed).collect(Collectors.toSet());
        Set<Module> runtimeImageUnnamedModules = runtimeImageModules.stream().filter(Predicate.not(Module::isNamed)).collect(Collectors.toSet());

        /*
         * Parse explicitly added modules via --add-modules. This is done early as this information
         * is required when filtering the analysis reachable module set.
         */
        Set<String> extraModules = ModuleSupport.parseModuleSetModifierProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES);
        extraModules.addAll(Resources.getIncludedResourcesModules());
        extraModules.stream().filter(Predicate.not(ModuleSupport.nonExplicitModules::contains)).forEach(moduleName -> {
            Optional<?> module = accessImpl.imageClassLoader.findModule(moduleName);
            if (module.isEmpty()) {
                throw VMError.shouldNotReachHere("Explicitly required module " + moduleName + " is not available");
            }
            runtimeImageNamedModules.add((Module) module.get());
        });

        /*
         * We need to include synthetic modules in the runtime module system. Some modules, such as
         * jdk.proxy, are created for all class loaders, so we need to make sure to not include
         * those made for the builder class loader.
         */
        Set<Module> analysisReachableSyntheticModules = runtimeImageNamedModules
                        .stream()
                        .filter(ModuleLayerFeatureUtils::isModuleSynthetic)
                        .filter(m -> m.getClassLoader() != accessImpl.imageClassLoader.getClassLoader())
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
        ModuleLayer runtimeBootLayer = runtimeModuleLayers.getFirst();
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            SharedLayerBootLayerModulesSingleton.singleton().setBootLayer(runtimeBootLayer);
        }
        if (ImageLayerBuildingSupport.lastImageBuild()) {
            RuntimeModuleSupport.singleton().setBootLayer(runtimeBootLayer);
        }
        bootLayer = runtimeBootLayer;
        RuntimeClassLoaderValueSupport.instance().update(runtimeModuleLayers);

        /*
         * Ensure that runtime modules have the same relations (i.e., reads, opens and exports) as
         * the originals.
         */
        replicateVisibilityModifications(runtimeBootLayer, accessImpl, accessImpl.imageClassLoader, runtimeImageNamedModules, runtimeImageUnnamedModules);
        replicateNativeAccess(accessImpl, runtimeImageNamedModules);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * The packages fields need to be rescanned to ensure that all the future constants are
             * finalized, even if they were not modified in the application layer.
             */
            FeatureImpl.BeforeCompilationAccessImpl access = (FeatureImpl.BeforeCompilationAccessImpl) a;
            CrossLayerConstantRegistryFeature singleton = CrossLayerConstantRegistryFeature.singleton();
            for (var module : LayeredModuleSingleton.singleton().getModules()) {
                finalizeFutureHeapConstant(singleton, module, PackageType.OPENED, moduleLayerFeatureUtils.moduleOpenPackagesField);
                finalizeFutureHeapConstant(singleton, module, PackageType.EXPORTED, moduleLayerFeatureUtils.moduleExportedPackagesField);
            }
        }
    }

    private static void finalizeFutureHeapConstant(CrossLayerConstantRegistryFeature singleton, Module module, PackageType packageType, Field field) {
        String moduleKeyName = packageType.getModuleKeyName(module);
        if (singleton.constantExists(moduleKeyName)) {
            singleton.finalizeFutureHeapConstant(moduleKeyName, ReflectionUtil.readField(Module.class, field.getName(), module));
        }
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
        Set<String> limitModules = ModuleSupport.parseModuleSetModifierProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_LIMITED_MODULES);

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
                if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                    moduleNames.addAll(SharedLayerBootLayerModulesSingleton.singleton().getSharedBootLayerModules());
                }
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

        List<ModuleLayer> runtimeModuleLayers = hostedModuleLayers.stream().map(moduleLayerPairs::get).filter(Objects::nonNull).toList();
        return runtimeModuleLayers;
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
                Module runtimeSyntheticModule = moduleLayerFeatureUtils.getOrCreateRuntimeModuleForHostedModule(syntheticModule, accessImpl);
                nameToModule.putIfAbsent(runtimeSyntheticModule.getName(), runtimeSyntheticModule);
                moduleLayerFeatureUtils.patchModuleLayerField(accessImpl, runtimeSyntheticModule, runtimeModuleLayer);
            }
            ServicesCatalog servicesCatalog = synthesizeRuntimeModuleLayerServicesCatalog(nameToModule);
            patchRuntimeModuleLayer(accessImpl, runtimeModuleLayer, nameToModule, parentLayers, servicesCatalog);
            accessImpl.rescanField(runtimeModuleLayer, moduleLayerFeatureUtils.moduleLayerModulesField);
            return runtimeModuleLayer;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere("Failed to synthesize the runtime module layer: " + runtimeModuleLayer, ex);
        }
    }

    private static ServicesCatalog synthesizeRuntimeModuleLayerServicesCatalog(Map<String, Module> nameToModule) {
        ServicesCatalog servicesCatalog = ServicesCatalog.create();
        for (Module m : nameToModule.values()) {
            servicesCatalog.register(m);
        }
        return servicesCatalog;
    }

    private void replicateVisibilityModifications(ModuleLayer runtimeBootLayer, AfterAnalysisAccessImpl accessImpl, ImageClassLoader cl, Set<Module> analysisReachableNamedModules,
                    Set<Module> analysisReachableUnnamedModules) {
        List<Module> applicationModules = findApplicationModules(runtimeBootLayer, cl.applicationModulePath());
        Set<String> applicationModuleNames = applicationModules.stream().map(Module::getName).collect(Collectors.toUnmodifiableSet());
        ImageSingletons.add(ApplicationModules.class, () -> applicationModuleNames);

        Map<Module, Module> namedModulePairs = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));
        Map<Module, Module> unnamedModulePairs = analysisReachableUnnamedModules
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));
        unnamedModulePairs.put(moduleLayerFeatureUtils.allUnnamedModule, moduleLayerFeatureUtils.allUnnamedModule);
        unnamedModulePairs.put(moduleLayerFeatureUtils.everyoneModule, moduleLayerFeatureUtils.everyoneModule);

        try {
            for (Map.Entry<Module, Module> e1 : namedModulePairs.entrySet()) {
                Module hostedFrom = e1.getKey();
                Module runtimeFrom = e1.getValue();
                for (Map.Entry<Module, Module> e2 : namedModulePairs.entrySet()) {
                    replicateVisibilityModification(accessImpl, applicationModules, hostedFrom, e2.getKey(), runtimeFrom, e2.getValue());
                }
                for (Map.Entry<Module, Module> e2 : unnamedModulePairs.entrySet()) {
                    replicateVisibilityModification(accessImpl, applicationModules, hostedFrom, e2.getKey(), runtimeFrom, e2.getValue());
                }
                moduleLayerFeatureUtils.encodeFields(accessImpl, runtimeFrom);
            }
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to transfer hosted module relations to the runtime boot module layer.", ex);
        }
    }

    private void replicateVisibilityModification(AfterAnalysisAccessImpl accessImpl, List<Module> applicationModules, Module hostedFrom, Module hostedTo, Module runtimeFrom, Module runtimeTo)
                    throws IllegalAccessException {
        if (hostedTo == hostedFrom) {
            return;
        }

        Module builderModule = ModuleLayerFeatureUtils.getBuilderModule();
        assert builderModule != null;

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

    private void replicateNativeAccess(AfterAnalysisAccessImpl accessImpl, Set<Module> analysisReachableNamedModules) {
        Map<Module, Module> modulePairs = analysisReachableNamedModules
                        .stream()
                        .collect(Collectors.toMap(m -> m, m -> moduleLayerFeatureUtils.getRuntimeModuleForHostedModule(m, false)));

        for (Map.Entry<Module, Module> modulesPair : modulePairs.entrySet()) {
            Module hosted = modulesPair.getKey();
            Module runtime = modulesPair.getValue();
            if (moduleLayerFeatureUtils.allowsNativeAccess(hosted) || moduleLayerFeatureUtils.isNativeAccessEnabledForRuntimeModule(runtime)) {
                if (!moduleLayerFeatureUtils.allowsNativeAccess(runtime)) {
                    moduleLayerFeatureUtils.enableNativeAccess(accessImpl, runtime);
                }
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

    private void patchRuntimeModuleLayer(AnalysisAccessBase accessImpl, ModuleLayer runtimeModuleLayer, Map<String, Module> nameToModule, List<ModuleLayer> parents, ServicesCatalog servicesCatalog) {
        try {
            moduleLayerFeatureUtils.patchModuleLayerNameToModuleField(accessImpl, runtimeModuleLayer, nameToModule);
            moduleLayerFeatureUtils.patchModuleLayerParentsField(accessImpl, runtimeModuleLayer, parents);
            moduleLayerFeatureUtils.patchModuleLayerServicesCatalogField(accessImpl, runtimeModuleLayer, servicesCatalog);
        } catch (IllegalAccessException ex) {
            throw VMError.shouldNotReachHere("Failed to patch the runtime boot module layer.", ex);
        }

        // Ensure that the lazy modules field gets set
        runtimeModuleLayer.modules();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private final class ModuleLayerFeatureUtils {
        private final Map<ClassLoader, Map<String, Module>> runtimeModules;
        private final ImageClassLoader imageClassLoader;
        private final SymbolEncoder encoder = SymbolEncoder.singleton();

        private final Module allUnnamedModule;
        private final Set<Module> allUnnamedModuleSet;
        private final Module everyoneModule;
        private final Set<Module> everyoneSet;
        private final Constructor<Module> namedModuleConstructor;
        private final Constructor<Module> unnamedModuleConstructor;
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
        private final Field moduleLayerServicesCatalogField;
        private final Field moduleLayerModulesField;
        private final Field moduleReferenceLocationField;
        private final Field moduleReferenceImplLocationField;
        private final Set<String> nativeAccessEnabled;

        ModuleLayerFeatureUtils(ImageClassLoader cl) {
            runtimeModules = new HashMap<>();
            imageClassLoader = cl;
            nativeAccessEnabled = NativeImageClassLoaderOptions.EnableNativeAccess.getValue().values().stream()
                            .flatMap(m -> Arrays.stream(SubstrateUtil.split(m, ",")))
                            .collect(Collectors.toSet());

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

                if (ImageLayerBuildingSupport.buildingImageLayer()) {
                    LayeredModuleSingleton singleton = LayeredModuleSingleton.singleton();
                    singleton.setUnnamedModules(everyoneModule, allUnnamedModule);
                }

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

                namedModuleConstructor = ReflectionUtil.lookupConstructor(Module.class, ClassLoader.class, ModuleDescriptor.class);
                unnamedModuleConstructor = ReflectionUtil.lookupConstructor(Module.class, ClassLoader.class);
                moduleFindModuleMethod = ReflectionUtil.lookupMethod(Module.class, "findModule", String.class, Map.class, Map.class, List.class);

                systemModuleFindersAllSystemModulesMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "allSystemModules");
                systemModuleFindersOfMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "of", Class.forName("jdk.internal.module.SystemModules"));
                systemModuleFindersSystemModulesMethod = ReflectionUtil.lookupMethod(SystemModuleFinders.class, "systemModules", String.class);

                moduleBootstrapLimitFinderMethod = ReflectionUtil.lookupMethod(ModuleBootstrap.class, "limitFinder", ModuleFinder.class, Set.class, Set.class);

                defaultRootsComputeMethod = ReflectionUtil.lookupMethod(DefaultRoots.class, "compute", ModuleFinder.class, ModuleFinder.class);

                moduleLayerConstructor = ReflectionUtil.lookupConstructor(ModuleLayer.class, Configuration.class, List.class, Function.class);
                moduleLayerNameToModuleField = ReflectionUtil.lookupField(ModuleLayer.class, "nameToModule");
                moduleLayerParentsField = ReflectionUtil.lookupField(ModuleLayer.class, "parents");
                moduleLayerServicesCatalogField = ReflectionUtil.lookupField(ModuleLayer.class, "servicesCatalog");
                moduleLayerModulesField = ReflectionUtil.lookupField(ModuleLayer.class, "modules");
                moduleReferenceLocationField = ReflectionUtil.lookupField(ModuleReference.class, "location");
                moduleReferenceImplLocationField = ReflectionUtil.lookupField(ModuleReferenceImpl.class, "location");
            } catch (ReflectiveOperationException | NoSuchElementException ex) {
                throw VMError.shouldNotReachHere("Failed to retrieve fields of the Module/ModuleLayer class.", ex);
            }
        }

        private boolean isNativeAccessEnabledForRuntimeBootLayerModule(String runtimeModuleName) {
            return nativeAccessEnabled.contains(runtimeModuleName);
        }

        private boolean isNativeAccessEnabledForRuntimeModule(Module runtimeModule) {
            String runtimeModuleName = runtimeModule.getName();
            return bootLayer == runtimeModule.getLayer() && isNativeAccessEnabledForRuntimeBootLayerModule(runtimeModuleName);
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
            return getRuntimeModuleForHostedModule(hostedModule.getClassLoader(), hostedModule.getName(), optional);
        }

        public Module getRuntimeModuleForHostedModule(ClassLoader hostedLoader, String hostedModuleName, boolean optional) {
            ClassLoader loader = HostedSubstrateUtil.getRuntimeClassLoader(hostedLoader);
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

        public Module getOrCreateRuntimeModuleForHostedModule(Module hostedModule, AnalysisAccessBase access) {
            /*
             * Special module instances such as ALL_UNNAMED and EVERYONE_MODULE are not replicated
             * as they only serve as marker modules (all their fields are null, including the loader
             * field).
             */
            if (hostedModule == allUnnamedModule || hostedModule == everyoneModule) {
                return hostedModule;
            } else {
                boolean enableNativeAccess = allowsNativeAccess(hostedModule) || isNativeAccessEnabledForRuntimeBootLayerModule(hostedModule.getName());
                return getOrCreateRuntimeModuleForHostedModule(hostedModule.getClassLoader(), hostedModule.getName(), hostedModule.getDescriptor(), access, enableNativeAccess);
            }
        }

        public Module getOrCreateRuntimeModuleForHostedModule(ClassLoader hostedLoader, String hostedModuleName, ModuleDescriptor runtimeModuleDescriptor, AnalysisAccessBase access,
                        boolean enableNativeAccess) {
            ClassLoader loader = HostedSubstrateUtil.getRuntimeClassLoader(hostedLoader);
            synchronized (runtimeModules) {
                Module runtimeModule = getRuntimeModuleForHostedModule(loader, hostedModuleName, true);
                if (runtimeModule != null) {
                    return runtimeModule;
                }

                try {
                    if (hostedModuleName == null) {
                        runtimeModule = unnamedModuleConstructor.newInstance(loader);
                    } else {
                        runtimeModule = namedModuleConstructor.newInstance(loader, runtimeModuleDescriptor);
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                    throw VMError.shouldNotReachHere("Failed to reflectively construct a runtime Module object.", ex);
                }
                runtimeModules.putIfAbsent(loader, new HashMap<>());
                runtimeModules.get(loader).put(hostedModuleName, runtimeModule);
                if (enableNativeAccess) {
                    enableNativeAccess(access, runtimeModule);
                }
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
                boolean nativeAccess = false;
                Module m = getOrCreateRuntimeModuleForHostedModule(loader, name, descriptor, access, nativeAccess);
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
                        addPreviousLayerExportedPackages(m, exportedPackages, moduleName -> getModule(moduleName, nameToModule));
                        for (ModuleDescriptor.Exports exports : m.getDescriptor().exports()) {
                            String source = exports.source();
                            if (exports.isQualified()) {
                                Set<Module> targets = exportedPackages.getOrDefault(source, new HashSet<>(exports.targets().size()));
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
                        if (ImageLayerBuildingSupport.buildingImageLayer()) {
                            LayeredModuleSingleton.singleton().setExportedPackages(m, exportedPackages);
                        }
                        rescan(access, exportedPackages, m, moduleExportedPackagesField);
                    } else {
                        Map<String, Set<Module>> openPackages = new HashMap<>(descriptor.opens().size());
                        addPreviousLayerOpenPackages(m, openPackages, moduleName -> getModule(moduleName, nameToModule));
                        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
                            String source = opens.source();
                            if (opens.isQualified()) {
                                Set<Module> targets = openPackages.getOrDefault(source, new HashSet<>(opens.targets().size()));
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
                        addPreviousLayerExportedPackages(m, exportedPackages, moduleName -> getModule(moduleName, nameToModule));
                        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
                            String source = exports.source();
                            Set<Module> openToTargets = openPackages.get(source);
                            if (openToTargets != null && openToTargets.contains(everyoneModule)) {
                                continue;
                            }

                            if (exports.isQualified()) {
                                Set<Module> targets = exportedPackages.getOrDefault(source, new HashSet<>(exports.targets().size()));
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
                        if (ImageLayerBuildingSupport.buildingImageLayer()) {
                            LayeredModuleSingleton singleton = LayeredModuleSingleton.singleton();
                            singleton.setOpenPackages(m, openPackages);
                            singleton.setExportedPackages(m, exportedPackages);
                        }
                        rescan(access, openPackages, m, moduleOpenPackagesField);
                        rescan(access, exportedPackages, m, moduleExportedPackagesField);
                    }
                }
                access.rescanObject(m);
            }

            return nameToModule;
        }

        private void rescan(AnalysisAccessBase access, Map<String, Set<Module>> packages, Module m, Field modulePackagesField) {
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                access.rescanObject(packages);
            } else {
                access.rescanField(m, modulePackagesField);
            }
        }

        private Module getModule(String moduleName, Map<String, Module> nameToModule) {
            if (moduleName.equals(LayeredModuleSingleton.ALL_UNNAMED_MODULE_NAME)) {
                return allUnnamedModule;
            } else if (moduleName.equals(LayeredModuleSingleton.EVERYONE_MODULE_NAME)) {
                return everyoneModule;
            } else {
                return nameToModule.get(moduleName);
            }
        }

        private Module getModule(String moduleName, AnalysisAccessBase access) {
            if (moduleName.equals(LayeredModuleSingleton.ALL_UNNAMED_MODULE_NAME)) {
                return allUnnamedModule;
            } else if (moduleName.equals(LayeredModuleSingleton.EVERYONE_MODULE_NAME)) {
                return everyoneModule;
            } else {
                return imageClassLoader.findModule(moduleName).map(value -> getOrCreateRuntimeModuleForHostedModule(value, access)).orElse(null);
            }
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
                addPreviousLayerExportedPackages(module, exports, moduleName -> getModule(moduleName, accessImpl));
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
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                LayeredModuleSingleton.singleton().setExportedPackages(module, exports);
            }
            rescan(accessImpl, exports, module, moduleExportedPackagesField);
        }

        /**
         * Encodes the "opens" and "exports" package names.
         */
        void encodeFields(AfterAnalysisAccessImpl accessImpl, Module module) throws IllegalAccessException {
            encodeField(moduleExportedPackagesField, accessImpl, module);
            encodeField(moduleOpenPackagesField, accessImpl, module);
        }

        @SuppressWarnings("unchecked")
        private void encodeField(Field field, AfterAnalysisAccessImpl accessImpl, Module module) throws IllegalAccessException {
            Map<String, Set<Module>> fieldValue = (Map<String, Set<Module>>) field.get(module);
            if (fieldValue == null) {
                return;
            }
            Map<String, Set<Module>> encodedFieldValue = fieldValue.entrySet().stream()
                            .collect(Collectors.toMap(
                                            e -> encoder.encodePackage(e.getKey()),
                                            Map.Entry::getValue));
            field.set(module, encodedFieldValue);
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                accessImpl.rescanObject(encodedFieldValue);
            } else {
                accessImpl.rescanField(module, field);
            }
        }

        @SuppressWarnings("unchecked")
        void addOpens(AfterAnalysisAccessImpl accessImpl, Module module, String pn, Module other) throws IllegalAccessException {
            if (other != null && module.isOpen(pn, other)) {
                return;
            }

            Map<String, Set<Module>> opens = (Map<String, Set<Module>>) moduleOpenPackagesField.get(module);
            if (opens == null) {
                opens = new HashMap<>(1);
                addPreviousLayerOpenPackages(module, opens, moduleName -> getModule(moduleName, accessImpl));
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
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                LayeredModuleSingleton.singleton().setOpenPackages(module, opens);
            }
            rescan(accessImpl, opens, module, moduleOpenPackagesField);
        }

        private void addPreviousLayerOpenPackages(Module module, Map<String, Set<Module>> packages, Function<String, Module> nameToModule) {
            if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                addPreviousLayerPackages(packages, nameToModule, LayeredModuleSingleton.singleton().getOpenPackages(module));
            }
        }

        private void addPreviousLayerExportedPackages(Module module, Map<String, Set<Module>> packages, Function<String, Module> nameToModule) {
            if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                addPreviousLayerPackages(packages, nameToModule, LayeredModuleSingleton.singleton().getExportedPackages(module));
            }
        }

        private static void addPreviousLayerPackages(Map<String, Set<Module>> packages, Function<String, Module> nameToModule, Map<String, Set<String>> previousOpens) {
            if (previousOpens != null) {
                for (var entry : previousOpens.entrySet()) {
                    packages.put(entry.getKey(), entry.getValue().stream().map(nameToModule).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new)));
                }
            }
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

        void patchModuleLayerServicesCatalogField(AnalysisAccessBase accessImpl, ModuleLayer moduleLayer, ServicesCatalog servicesCatalog) throws IllegalAccessException {
            moduleLayerServicesCatalogField.set(moduleLayer, servicesCatalog);
            accessImpl.rescanField(moduleLayer, moduleLayerServicesCatalogField);
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

        /**
         * Allows the given module to perform native access.
         */
        void enableNativeAccess(AnalysisAccessBase access, Module module) {
            VMError.guarantee(!allowsNativeAccess(module), "Cannot reset native access");
            assert moduleEnableNativeAccessField != null : "Only available on JDK19+";
            try {
                moduleEnableNativeAccessField.set(module, true);
                access.rescanField(module, moduleEnableNativeAccessField);
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
