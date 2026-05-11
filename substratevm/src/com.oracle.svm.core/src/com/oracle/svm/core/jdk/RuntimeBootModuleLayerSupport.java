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
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
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
import java.util.function.Function;
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
/// - Creates new runtime [Module] objects directly with the built-in loader mapping,
/// - Grafts those modules onto the real boot layer, and
/// - Rebuilds the boot-layer configuration and caches to match the augmented contents.
public final class RuntimeBootModuleLayerSupport {
    public static final String UPGRADE_MODULE_PATH_OPTION = "--upgrade-module-path";
    public static final String MODULE_PATH_OPTION = "--module-path";
    public static final String ADD_MODULES_OPTION = "--add-modules";
    public static final String ADD_READS_OPTION = "--add-reads";
    public static final String ADD_EXPORTS_OPTION = "--add-exports";
    public static final String ADD_OPENS_OPTION = "--add-opens";
    public static final String ENABLE_NATIVE_ACCESS_OPTION = "--enable-native-access";

    /// Used by `ModuleBootstrap#boot2` and `ModulePathValidator#scanAllModules`.
    public static final String UPGRADE_MODULE_PATH_PROPERTY = "jdk.module.upgrade.path";

    public static final String MODULE_PATH_PROPERTY = "jdk.module.path";

    /// Read by `jdk.internal.loader.ClassLoaders.<clinit>`.
    public static final String MAIN_MODULE_PROPERTY = "jdk.module.main";

    public static final String ADD_MODULES_PROPERTY_PREFIX = "jdk.module.addmods.";

    /// Used by `ModuleBootstrap#addExtraReads`.
    public static final String ADD_READS_PROPERTY_PREFIX = "jdk.module.addreads.";

    /// Used by `ModuleBootstrap#addExtraExportsAndOpens`.
    public static final String ADD_EXPORTS_PROPERTY_PREFIX = "jdk.module.addexports.";

    /// Used by `ModuleBootstrap#addExtraExportsAndOpens`.
    public static final String ADD_OPENS_PROPERTY_PREFIX = "jdk.module.addopens.";

    /// Used by `ModuleBootstrap#decodeEnableNativeAccess` and replayed by
    /// `ModuleBootstrap#addEnableNativeAccess`.
    public static final String ENABLE_NATIVE_ACCESS_PROPERTY_PREFIX = "jdk.module.enable.native.access.";

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
    /// 1. Read the runtime-initialized `jdk.module.*` properties for the launcher-selected main
    /// module,
    /// `--upgrade-module-path`, `--module-path`, `--add-modules`, `--add-reads`,
    /// `--add-exports`, `--add-opens`, and `--enable-native-access`.
    /// 2. Resolve only those roots that are not already part of the build-time boot layer. Modules
    /// from `--upgrade-module-path` are searched before system modules, so the option can provide
    /// new boot-layer modules. It cannot replace modules already built into the image boot layer.
    /// 3. Create the corresponding runtime [Module] objects directly using the same built-in
    /// loader mapping that HotSpot uses for boot-layer modules, then initialize their reads,
    /// exports, and opens against the existing boot layer.
    /// 4. Patch the real boot layer so [ModuleLayer#boot] exposes only the newly resolved runtime
    /// modules without changing its identity, then apply any extra reads, exports, opens, and
    /// native-access grants selected at launch time.
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
        String upgradeModulePath = System.getProperty(UPGRADE_MODULE_PATH_PROPERTY, "");
        ModuleFinder upgradeModulePathFinder = createModuleFinder(upgradeModulePath);
        Configuration originalBootConfiguration = bootLayer.configuration();
        rejectUpgradeModulePathReplacements(originalBootConfiguration, upgradeModulePathFinder);
        ModuleFinder observableSystemModuleFinder = createSystemModuleFinder(ModuleFinder.ofSystem(), upgradeModulePathFinder);
        String modulePath = System.getProperty(MODULE_PATH_PROPERTY, "");
        ModuleFinder modulePathFinder = createModuleFinder(modulePath);
        rejectModulePathReplacements(originalBootConfiguration, modulePathFinder);
        ModuleFinder observableModuleFinder = createObservableModuleFinder(observableSystemModuleFinder, modulePathFinder);
        Set<String> roots = getRootModules(originalBootConfiguration, observableSystemModuleFinder, modulePathFinder, observableModuleFinder);
        if (!roots.isEmpty()) {
            Configuration augmentationConfiguration = resolveAugmentationConfiguration(originalBootConfiguration, observableModuleFinder, roots);
            Set<ResolvedModule> runtimeModules = selectNewRuntimeModules(originalBootConfiguration, augmentationConfiguration, observableModuleFinder, roots);
            if (!runtimeModules.isEmpty()) {
                Configuration mergedConfiguration = createAugmentedBootConfiguration(originalBootConfiguration, runtimeModules);
                /*
                 * Let the appropriate built-in loaders learn about the newly resolved module
                 * references, then create the corresponding Module objects directly with the same
                 * loader assignment rules that apply to the real boot layer.
                 */
                Function<String, ClassLoader> clf = createRuntimeModuleClassLoaderFunction(mergedConfiguration);
                registerModules(runtimeModules, clf);
                Map<String, Module> runtimeModuleObjects = createRuntimeModules(bootLayer, runtimeModules, clf);
                /*
                 * Fold the new modules back into the real boot layer so ModuleLayer.boot() keeps
                 * its original identity while reflecting the runtime-resolved modules.
                 */
                patchBootLayer(bootLayer, mergedConfiguration, runtimeModuleObjects);
            }
        }

        Target_jdk_internal_module_ModuleBootstrap.addExtraReads(bootLayer);
        Target_jdk_internal_module_ModuleBootstrap.addExtraExportsAndOpens(bootLayer);
        ModuleBootstrapSubstitutionsSupport.mergeRuntimeEnableNativeAccessModules();
        Target_jdk_internal_module_ModuleBootstrap.addEnableNativeAccess(bootLayer);
    }

    /// Resolves the runtime root modules against the existing boot configuration.
    ///
    /// The existing boot configuration remains authoritative. Modules already present in
    /// `originalBootConfiguration` are therefore found via the parent configuration, while `finder`
    /// only contributes modules that are not already in the boot layer.
    private static Configuration resolveAugmentationConfiguration(Configuration originalBootConfiguration, ModuleFinder finder, Set<String> roots) {
        return Configuration.resolve(ModuleFinder.of(), List.of(originalBootConfiguration), finder, roots);
    }

    /// Selects only the modules from the augmentation configuration that are not already present in
    /// the boot configuration, then resolves any additional provider modules induced by service
    /// binding from those runtime-resolved modules.
    private static Set<ResolvedModule> selectNewRuntimeModules(Configuration originalBootConfiguration, Configuration augmentationConfiguration, ModuleFinder finder, Set<String> roots) {
        LinkedHashMap<String, ResolvedModule> runtimeModules = new LinkedHashMap<>();
        collectAbsentModules(runtimeModules, originalBootConfiguration, augmentationConfiguration.modules());

        if (!runtimeModules.isEmpty()) {
            Configuration bindingConfiguration = augmentationConfiguration.resolveAndBind(ModuleFinder.of(), finder, roots);
            collectAbsentModules(runtimeModules, augmentationConfiguration, bindingConfiguration.modules());
        }

        return new LinkedHashSet<>(runtimeModules.values());
    }

    private static void collectAbsentModules(Map<String, ResolvedModule> target, Configuration knownConfiguration, Collection<ResolvedModule> candidates) {
        for (ResolvedModule resolvedModule : candidates) {
            if (knownConfiguration.findModule(resolvedModule.name()).isEmpty()) {
                target.put(resolvedModule.name(), resolvedModule);
            }
        }
    }

    /// Rejects only the part of `--upgrade-module-path` that is impossible for SVM to implement:
    /// replacing a module already present in the prebuilt boot layer. Entries for modules not
    /// already in the boot layer remain supported and are resolved before system modules.
    private static void rejectUpgradeModulePathReplacements(Configuration originalBootConfiguration, ModuleFinder upgradeModulePathFinder) {
        rejectBootLayerReplacements(originalBootConfiguration, upgradeModulePathFinder, UPGRADE_MODULE_PATH_OPTION, "replace");
    }

    /// Rejects any `--module-path` entry whose name collides with a module already present in the
    /// prebuilt boot layer.
    private static void rejectModulePathReplacements(Configuration originalBootConfiguration, ModuleFinder modulePathFinder) {
        rejectBootLayerReplacements(originalBootConfiguration, modulePathFinder, MODULE_PATH_OPTION, "contain");
    }

    /// Rejects any path entry that collides by name with a module already present in the prebuilt
    /// boot layer.
    ///
    /// Runtime boot-layer augmentation can only add modules that were absent at image build time.
    /// Reintroducing an already-built boot-layer module from the application module path would
    /// create an unsupported ambiguity between the preserved boot layer and the runtime path entry.
    private static void rejectBootLayerReplacements(Configuration originalBootConfiguration, ModuleFinder finder, String optionName, String action) {
        for (ModuleReference moduleReference : finder.findAll()) {
            String moduleName = moduleReference.descriptor().name();
            if (originalBootConfiguration.findModule(moduleName).isPresent()) {
                throw new IllegalArgumentException("The option '" + optionName + "' cannot " + action + " module '" + moduleName + "' because it is already built into the image boot layer");
            }
        }
    }

    /// Returns the JDK's internal module-definition access used to create and wire runtime
    /// modules without reflective calls into `java.lang.Module`.
    private static Target_jdk_internal_access_JavaLangAccess javaLangAccess() {
        return Target_jdk_internal_access_SharedSecrets.getJavaLangAccess();
    }

    /// Creates the new runtime [Module] objects and initializes their relationship state.
    ///
    /// This performs the same module-definition and wiring steps that the JDK normally performs
    /// while defining modules. `ModuleLayer.defineModules` and related helpers drive the process in
    /// library code, while the low-level module-definition helpers ultimately delegate to the
    /// HotSpot native implementations in `hotspot/share/classfile/modules.cpp`. We do the wiring
    /// manually here so the existing boot-layer object can be preserved and patched in place
    /// afterwards.
    private static Map<String, Module> createRuntimeModules(ModuleLayer bootLayer, Set<ResolvedModule> runtimeModules, Function<String, ClassLoader> clf) {
        Target_jdk_internal_access_JavaLangAccess jla = javaLangAccess();
        LinkedHashMap<String, Module> nameToModule = new LinkedHashMap<>();

        // define each module in the configuration to the VM
        for (ResolvedModule resolvedModule : runtimeModules) {
            ModuleReference reference = resolvedModule.reference();
            Module module = jla.defineModule(clf.apply(resolvedModule.name()), reference.descriptor(), reference.location().orElse(null));
            SubstrateUtil.cast(module, Target_java_lang_Module.class).layer = bootLayer;
            nameToModule.put(resolvedModule.name(), module);
        }

        // setup readability and exports/opens
        for (ResolvedModule resolvedModule : runtimeModules) {
            Module module = nameToModule.get(resolvedModule.name());
            for (ResolvedModule dependency : resolvedModule.reads()) {
                Module dependencyModule = nameToModule.get(dependency.name());
                if (dependencyModule == null) {
                    dependencyModule = bootLayer.findModule(dependency.name()).orElse(null);
                }
                if (dependencyModule != null) {
                    jla.addReads(module, dependencyModule);
                }
            }

            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            if (descriptor.isAutomatic()) {
                jla.addReadsAllUnnamed(module);
            }
            if (!descriptor.isOpen() && !descriptor.isAutomatic()) {
                initializeExportsAndOpens(jla, bootLayer, module, descriptor, nameToModule);
            }
        }
        return nameToModule;
    }

    /// Initializes descriptor-defined exports and opens for a runtime-created module.
    private static void initializeExportsAndOpens(Target_jdk_internal_access_JavaLangAccess javaLangAccess, ModuleLayer bootLayer, Module module, ModuleDescriptor descriptor,
                    Map<String, Module> nameToModule) {
        Module everyoneModule = Target_java_lang_Module.EVERYONE_MODULE;
        for (Opens opens : descriptor.opens()) {
            if (opens.isQualified()) {
                for (String target : opens.targets()) {
                    Module targetModule = findRuntimeTargetModule(bootLayer, nameToModule, target);
                    if (targetModule != null) {
                        javaLangAccess.addOpens(module, opens.source(), targetModule);
                    }
                }
            } else {
                javaLangAccess.addOpens(module, opens.source(), everyoneModule);
            }
        }

        for (Exports exports : descriptor.exports()) {
            if (exports.isQualified()) {
                for (String target : exports.targets()) {
                    Module targetModule = findRuntimeTargetModule(bootLayer, nameToModule, target);
                    if (targetModule != null) {
                        javaLangAccess.addExports(module, exports.source(), targetModule);
                    }
                }
            } else {
                javaLangAccess.addExports(module, exports.source());
            }
        }
    }

    /// Finds a module referenced by a descriptor-defined target clause in either the new runtime
    /// modules or the existing boot layer.
    private static Module findRuntimeTargetModule(ModuleLayer bootLayer, Map<String, Module> nameToModule, String moduleName) {
        Module module = nameToModule.get(moduleName);
        if (module != null) {
            return module;
        }
        return bootLayer.findModule(moduleName).orElse(null);
    }

    /// Patches the real boot layer so it exposes the newly created runtime modules.
    ///
    /// The boot layer's name-to-module map and configuration are replaced with merged versions
    /// that include both the build-time and runtime-resolved modules.
    private static void patchBootLayer(ModuleLayer bootLayer, Configuration mergedConfiguration, Map<String, Module> runtimeModules) {
        Map<String, Module> mergedNameToModule = new LinkedHashMap<>(ModuleLayerSubstitutionsSupport.nameToModule(bootLayer));
        mergedNameToModule.putAll(runtimeModules);

        ModuleLayerSubstitutionsSupport.patchBootLayer(mergedConfiguration, Map.copyOf(mergedNameToModule));
        /* Recompute lazy caches against the augmented module map. */
        bootLayer.modules();
    }

    /// Rebuilds a single `Configuration` that describes the augmented boot layer.
    ///
    /// `ModuleLayer` caches and queries rely on one coherent configuration object. After runtime
    /// augmentation, we therefore synthesize a finder containing both the original boot-layer
    /// modules and the newly resolved runtime modules, and resolve all module names again into one
    /// merged configuration.
    private static Configuration createAugmentedBootConfiguration(Configuration originalBootConfiguration, Set<ResolvedModule> runtimeModules) {
        LinkedHashSet<String> roots = originalBootConfiguration.modules().stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).collect(
                        Collectors.toCollection(LinkedHashSet::new));
        runtimeModules.stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).forEach(roots::add);

        LinkedHashMap<String, ModuleReference> references = new LinkedHashMap<>();
        addModuleReferences(references, originalBootConfiguration.modules());
        addModuleReferences(references, runtimeModules);
        ModuleFinder finder = new MapBackedModuleFinder(references);
        try {
            List<Configuration> parents = originalBootConfiguration.parents();
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

    /// Creates the built-in loader mapping used when defining runtime-resolved modules.
    ///
    /// This must use the JDK's own `ModuleLoaderMap.Mapper` implementation rather than a plain
    /// lambda so runtime-added modules keep the same boot/platform/application loader assignment
    /// rules as the JDK boot layer.
    private static Function<String, ClassLoader> createRuntimeModuleClassLoaderFunction(Configuration configuration) {
        return Target_jdk_internal_module_ModuleLoaderMap.mappingFunction(configuration);
    }

    /// Registers the resolved runtime modules with the built-in loaders that will define them in
    /// the temporary augmentation layer.
    private static void registerModules(Set<ResolvedModule> runtimeModules, Function<String, ClassLoader> clf) {
        for (ResolvedModule resolvedModule : runtimeModules) {
            ClassLoader classLoader = clf.apply(resolvedModule.name());
            if (classLoader == null) {
                classLoader = SubstrateUtil.cast(Target_jdk_internal_loader_ClassLoaders.bootLoader(), ClassLoader.class);
            }
            if (!(classLoader instanceof jdk.internal.loader.BuiltinClassLoader)) {
                throw new IllegalArgumentException("Runtime boot-layer augmentation does not support module '" + resolvedModule.name() +
                                "' being mapped to unexpected class loader type '" + classLoader.getClass().getName() + "'");
            }
            Target_jdk_internal_loader_BuiltinClassLoader builtinLoader = SubstrateUtil.cast(classLoader, Target_jdk_internal_loader_BuiltinClassLoader.class);
            ModuleReference mref = builtinLoader.findModule(resolvedModule.name());
            if (mref != null) {
                if (!mref.equals(resolvedModule.reference())) {
                    throw new IllegalArgumentException("Runtime boot-layer resolved module has conflicting refs '" + resolvedModule.reference() + "' != '" + mref + "'");
                }
            } else {
                builtinLoader.loadModule(resolvedModule.reference());
            }
        }
    }

    private static ModuleFinder createModuleFinder(String modulePath) {
        Path[] paths = Arrays.stream(modulePath.split(File.pathSeparator)).filter(entry -> !entry.isEmpty()).map(Path::of).toArray(Path[]::new);
        if (paths.length == 0) {
            return ModuleFinder.of();
        }
        return ModuleFinder.of(paths);
    }

    /// Computes the runtime root modules requested by launcher options using the supplied boot
    /// configuration plus the observable system and application module finders.
    ///
    /// This mirrors the root-selection logic from `jdk.internal.module.ModuleBootstrap#boot2`,
    /// adapted to augment an existing boot layer at runtime. Modules already present in the
    /// build-time boot layer are filtered out from the returned roots because runtime augmentation
    /// can only add previously absent modules.
    private static Set<String> getRootModules(Configuration originalBootConfiguration, ModuleFinder systemModuleFinder, ModuleFinder modulePathFinder, ModuleFinder observableModuleFinder) {
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
                                    .filter(name -> observableModuleFinder.find(name).isPresent()) //
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
            roots.addAll(Target_jdk_internal_module_DefaultRoots.compute(systemModuleFinder, observableModuleFinder));
        }

        // If `--add-modules ALL-SYSTEM` is specified, then all observable system
        // modules will be resolved.
        if (addAllSystemModules) {
            systemModuleFinder.findAll() //
                            .stream() //
                            .map(ModuleReference::descriptor) //
                            .map(ModuleDescriptor::name) //
                            .filter(name -> observableModuleFinder.find(name).isPresent()) //
                            .forEach(roots::add);
        }

        // Filter out modules already present in the build-time boot layer
        roots.removeIf(moduleName -> originalBootConfiguration.findModule(moduleName).isPresent());
        return roots;
    }

    /// Creates the system-module finder used at runtime, with upgrade-path modules searched before
    /// system modules. This supports user modules on `--upgrade-module-path`; replacement of
    /// image-built boot modules is rejected before this finder is used.
    private static ModuleFinder createSystemModuleFinder(ModuleFinder systemModuleFinder, ModuleFinder upgradeModulePathFinder) {
        return ModuleFinder.compose(upgradeModulePathFinder, systemModuleFinder);
    }

    /// Creates the observable-module finder from the upgraded system modules and application
    /// module path.
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
