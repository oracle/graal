/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.libjvm.LibJVMMainMethodWrappers;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

/// Registers the startup hook that augments the runtime boot layer for `libjvm` launches.
///
/// This feature is only enabled for the first image build. The actual augmentation work happens
/// later, in [RuntimeBootModuleLayerSupport#initialize], once the runtime launcher
/// options are available.
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class)
final class RuntimeBootModuleLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // GR-74805 will remove this guard
        if (ImageSingletons.contains(LibJVMMainMethodWrappers.class)) {
            RuntimeSupport.getRuntimeSupport().addStartupHook(new RuntimeBootModuleLayerStartupHook());
        }
    }
}

/// Startup hook that runs the boot-layer augmentation before user `libjvm` code starts loading
/// classes from runtime-resolved modules.
final class RuntimeBootModuleLayerStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        RuntimeBootModuleLayerSupport.initialize();
    }
}

/// Augments the existing runtime boot layer in-place with modules provided via `libjvm` launcher
/// options.
///
/// The key constraint is that [ModuleLayer#boot] must keep returning the original boot-layer
/// object. To satisfy that, this support:
///
/// - Resolves runtime module roots against the build-time boot configuration,
/// - Creates [Module] objects in a temporary layer,
/// - Grafts those modules back onto the real boot layer, and
/// - Rebuilds the boot-layer configuration and caches to match the augmented contents.
public final class RuntimeBootModuleLayerSupport {
    public static final String MODULE_PATH_OPTION = "--module-path";
    public static final String ADD_MODULES_OPTION = "--add-modules";
    public static final String ADD_EXPORTS_OPTION = "--add-exports";
    public static final String ADD_OPENS_OPTION = "--add-opens";
    public static final String MODULE_PATH_PROPERTY = "jdk.module.path";
    public static final String MAIN_MODULE_PROPERTY = "jdk.module.main";
    public static final String ADD_MODULES_PROPERTY_PREFIX = "jdk.module.addmods.";
    public static final String ADD_EXPORTS_PROPERTY_PREFIX = "jdk.module.addexports.";
    public static final String ADD_OPENS_PROPERTY_PREFIX = "jdk.module.addopens.";
    public static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";
    public static final String ALL_DEFAULT = "ALL-DEFAULT";
    public static final String ALL_SYSTEM = "ALL-SYSTEM";

    private RuntimeBootModuleLayerSupport() {
    }

    /// Resolves runtime module launcher options and folds any newly resolved modules into the
    /// existing boot layer.
    ///
    /// The flow is:
    ///
    /// 1. Read the preserved `jdk.module.*` properties for the launcher-selected main module,
    /// `--module-path`, `--add-modules`, `--add-exports`, and `--add-opens`.
    /// 2. Resolve only those roots that are not already part of the build-time boot layer.
    /// 3. Create the corresponding [Module] objects in a temporary layer using the application
    /// class loader.
    /// 4. Patch the real boot layer so [ModuleLayer#boot] exposes the resolved runtime
    /// modules without changing its identity, then replay any extra exports and opens on the
    /// resulting layer.
    static void initialize() {
        ModuleLayer bootLayer = ModuleLayer.boot();
        if (bootLayer == null) {
            return;
        }

        /*
         * Resolve the runtime launcher options against the existing boot configuration, so we only
         * add modules that were named at runtime and are not already present in the build-time boot
         * layer.
         */
        String modulePath = System.getProperty(MODULE_PATH_PROPERTY, "");
        ModuleFinder modulePathFinder = createModuleFinder(modulePath);
        ModuleFinder systemModuleFinder = ModuleFinder.ofSystem();
        ModuleFinder finder = createObservableModuleFinder(systemModuleFinder, modulePathFinder);
        Configuration bootConfiguration = bootLayer.configuration();
        Set<String> roots = getRootModules(systemModuleFinder, modulePathFinder);
        roots.removeIf(moduleName -> bootConfiguration.findModule(moduleName).isPresent());
        if (!roots.isEmpty()) {
            Configuration augmentationConfiguration = resolveAugmentationConfiguration(bootConfiguration, finder, roots);
            if (!augmentationConfiguration.modules().isEmpty()) {
                /*
                 * Let the application class loader learn about the newly resolved module
                 * references, then define a temporary layer so the JDK creates the corresponding
                 * Module objects.
                 */
                ClassLoader appLoader = ClassLoader.getSystemClassLoader();
                registerModules(appLoader, augmentationConfiguration);
                ModuleLayer augmentationLayer = ModuleLayer.defineModules(augmentationConfiguration, List.of(bootLayer), _ -> appLoader).layer();
                /*
                 * Fold the temporary layer back into the real boot layer so ModuleLayer.boot()
                 * keeps its original identity while reflecting the runtime-resolved modules.
                 */
                patchBootLayer(bootLayer, bootConfiguration, augmentationConfiguration, augmentationLayer);
            }
        }

        Target_jdk_internal_module_ModuleBootstrap.addExtraExportsAndOpens(bootLayer);
    }

    /// Resolves the runtime root modules against the existing boot configuration.
    ///
    /// The returned configuration contains only the newly resolved runtime modules; the boot-layer
    /// modules remain represented by `bootConfiguration`.
    private static Configuration resolveAugmentationConfiguration(Configuration bootConfiguration, ModuleFinder finder, Set<String> roots) {
        return Configuration.resolve(finder, List.of(bootConfiguration), ModuleFinder.of(), roots);
    }

    /// Patches the real boot layer so it exposes the modules created in the temporary augmentation
    /// layer.
    ///
    /// Each [Module] created by the temporary layer is rebound to `bootLayer`, then the boot
    /// layer's name-to-module map and configuration are replaced with merged versions that include
    /// both the build-time and runtime-resolved modules.
    private static void patchBootLayer(ModuleLayer bootLayer, Configuration bootConfiguration, Configuration augmentationConfiguration, ModuleLayer augmentationLayer) {
        Map<String, Module> mergedNameToModule = new LinkedHashMap<>(ModuleLayerSubstitutionsSupport.nameToModule(bootLayer));
        for (ResolvedModule resolvedModule : augmentationConfiguration.modules()) {
            String moduleName = resolvedModule.reference().descriptor().name();
            Module module = augmentationLayer.findModule(moduleName).orElseThrow();
            ModuleSubstitutionsSupport.patchLayer(module, bootLayer);
            mergedNameToModule.put(moduleName, module);
        }

        Configuration mergedConfiguration = createAugmentedBootConfiguration(bootConfiguration, augmentationConfiguration);
        ModuleLayerSubstitutionsSupport.patchBootLayer(mergedConfiguration, Map.copyOf(mergedNameToModule), augmentationLayer.modules());
        /* Recompute lazy caches against the augmented module map. */
        bootLayer.modules();
    }

    /// Rebuilds a single `Configuration` that describes the augmented boot layer.
    ///
    /// `ModuleLayer` caches and queries rely on one coherent configuration object. After runtime
    /// augmentation, we therefore synthesize a finder containing both the original boot-layer
    /// modules and the newly resolved runtime modules, and resolve all module names again into one
    /// merged configuration.
    private static Configuration createAugmentedBootConfiguration(Configuration bootConfiguration, Configuration augmentationConfiguration) {
        LinkedHashSet<String> roots = bootConfiguration.modules().stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).collect(
                        Collectors.toCollection(LinkedHashSet::new));
        augmentationConfiguration.modules().stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).forEach(roots::add);

        LinkedHashMap<String, ModuleReference> references = new LinkedHashMap<>();
        addModuleReferences(references, bootConfiguration.modules());
        addModuleReferences(references, augmentationConfiguration.modules());
        ModuleFinder finder = new MapBackedModuleFinder(references);
        try {
            List<Configuration> parents = bootConfiguration.parents();
            return Configuration.resolve(finder, parents, ModuleFinder.of(), roots);
        } catch (FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to rebuild the augmented runtime boot module layer configuration.", ex);
        }
    }

    private static void addModuleReferences(Map<String, ModuleReference> references, Collection<ResolvedModule> modules) {
        for (ResolvedModule resolvedModule : modules) {
            ModuleReference reference = resolvedModule.reference();
            references.put(reference.descriptor().name(), reference);
        }
    }

    /// Registers the resolved runtime modules with the built-in application loader so the loader
    /// can serve classes and resources from them once the boot layer is patched.
    private static void registerModules(ClassLoader appLoader, Configuration config) {
        if (!(appLoader instanceof jdk.internal.loader.BuiltinClassLoader)) {
            return;
        }

        Target_jdk_internal_loader_BuiltinClassLoader builtinLoader = SubstrateUtil.cast(appLoader, Target_jdk_internal_loader_BuiltinClassLoader.class);
        for (ResolvedModule resolvedModule : config.modules()) {
            builtinLoader.loadModule(resolvedModule.reference());
        }
    }

    private static ModuleFinder createModuleFinder(String modulePath) {
        Path[] paths = Arrays.stream(modulePath.split(File.pathSeparator)).filter(entry -> !entry.isEmpty()).map(Path::of).toArray(Path[]::new);
        if (paths.length == 0) {
            return ModuleFinder.of();
        }
        return ModuleFinder.of(paths);
    }

    /// Computes the runtime root modules requested by launcher options using the supplied system
    /// and application module finders.
    ///
    /// This mirrors the root-selection logic from `jdk.internal.module.ModuleBootstrap#boot2`,
    /// adapted to augment an existing boot layer at runtime.
    private static Set<String> getRootModules(ModuleFinder systemModuleFinder, ModuleFinder modulePathFinder) {
        ModuleFinder finder = createObservableModuleFinder(systemModuleFinder, modulePathFinder);
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        String mainModule = System.getProperty(MAIN_MODULE_PROPERTY);
        if (mainModule != null) {
            roots.add(mainModule);
        }

        boolean addAllDefaultModules = false;
        boolean addAllSystemModules = false;
        for (int index = 0;; index++) {
            String value = System.getProperty(ADD_MODULES_PROPERTY_PREFIX + index);
            if (value == null) {
                break;
            }
            for (String moduleName : value.split(",")) {
                switch (moduleName) {
                    case "" -> {
                    }
                    case ALL_MODULE_PATH -> modulePathFinder.findAll() //
                                    .stream() //
                                    .map(ModuleReference::descriptor) //
                                    .map(ModuleDescriptor::name) //
                                    .filter(name -> finder.find(name).isPresent()) //
                                    .forEach(roots::add);
                    case ALL_DEFAULT -> addAllDefaultModules = true;
                    case ALL_SYSTEM -> addAllSystemModules = true;
                    default -> roots.add(moduleName);
                }
            }
        }

        // If there is no initial module specified, then assume that the initial
        // module is the unnamed module of the application class loader. This
        // is implemented by resolving all observable modules that export an
        // API. Modules that have the DO_NOT_RESOLVE_BY_DEFAULT bit set in
        // their ModuleResolution attribute flags are excluded from the
        // default set of roots.
        if (mainModule == null || addAllDefaultModules) {
            roots.addAll(Target_jdk_internal_module_DefaultRoots.compute(systemModuleFinder, finder));
        }

        // If `--add-modules ALL-SYSTEM` is specified, then all observable system
        // modules will be resolved.
        if (addAllSystemModules) {
            systemModuleFinder.findAll() //
                            .stream() //
                            .map(ModuleReference::descriptor) //
                            .map(ModuleDescriptor::name) //
                            .filter(name -> finder.find(name).isPresent()) //
                            .forEach(roots::add);
        }
        return roots;
    }

    private static ModuleFinder createObservableModuleFinder(ModuleFinder systemModuleFinder, ModuleFinder modulePathFinder) {
        return ModuleFinder.compose(systemModuleFinder, modulePathFinder);
    }

    /// Minimal `ModuleFinder` backed by an already-collected map of module references.
    ///
    /// This is used when rebuilding the merged boot-layer configuration after runtime module
    /// augmentation.
    private static final class MapBackedModuleFinder implements ModuleFinder {
        private final Map<String, ModuleReference> references;
        private final Set<ModuleReference> modules;

        private MapBackedModuleFinder(Map<String, ModuleReference> references) {
            this.references = references;
            this.modules = Set.copyOf(references.values());
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            return Optional.ofNullable(references.get(name));
        }

        @Override
        public Set<ModuleReference> findAll() {
            return modules;
        }
    }
}
