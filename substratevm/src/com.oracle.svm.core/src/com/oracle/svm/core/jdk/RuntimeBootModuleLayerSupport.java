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

import static com.oracle.svm.guest.staging.option.RuntimeBootModuleLayerOptions.MAIN_MODULE_PROPERTY;
import static com.oracle.svm.guest.staging.option.RuntimeBootModuleLayerOptions.MODULE_PATH_PROPERTY;
import static com.oracle.svm.guest.staging.option.RuntimeBootModuleLayerOptions.UPGRADE_MODULE_PATH_OPTION;
import static com.oracle.svm.guest.staging.option.RuntimeBootModuleLayerOptions.UPGRADE_MODULE_PATH_PROPERTY;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.internal.module.ModulePatcher;
import jdk.internal.module.ModuleReferenceImpl;

/// Registers the startup hook that augments the runtime boot layer when standard
/// runtime Java option parsing can preserve module options.
///
/// This feature is only enabled for the first image build. The actual augmentation work happens
/// later, in [RuntimeBootModuleLayerSupport#boot2], once the preserved runtime
/// module options are available.
@AutomaticallyRegisteredFeature
final class RuntimeBootModuleLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        // Runtime boot layer augmentation is only included when class loading is supported.
        return RuntimeClassLoading.isSupported() && ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (SubstrateOptions.StrictRuntimeJavaOptions.getValue()) {
            RuntimeSupport.getRuntimeSupport().addStartupHook(new RuntimeBootModuleLayerStartupHook());
        }
    }
}

/// Startup hook that runs the boot-layer augmentation before user code starts loading classes
/// from runtime-resolved modules.
final class RuntimeBootModuleLayerStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        RuntimeBootModuleLayerSupport.boot2();
    }
}

/// Augments the existing runtime boot layer in-place with modules requested via runtime
/// module options.
///
/// The key constraint is that [ModuleLayer#boot] must keep returning the build-time boot-layer
/// object. To satisfy that, this support:
///
/// - Resolves runtime module roots against the build-time boot configuration,
/// - Creates new runtime [Module] objects directly with the built-in loader mapping,
/// - Grafts those modules onto the real boot layer, and
/// - Rebuilds the boot-layer configuration and caches to match the augmented contents.
public final class RuntimeBootModuleLayerSupport {

    public static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";
    public static final String ALL_DEFAULT = "ALL-DEFAULT";
    public static final String ALL_SYSTEM = "ALL-SYSTEM";

    /// Cache for [#patchedIfNeeded].
    private static Map<ModuleReference, ModuleReference> mrefsPatcher;

    private RuntimeBootModuleLayerSupport() {
    }

    /// Resolves runtime module options and folds any newly resolved modules into the
    /// existing boot layer. The method also applies runtime `--patch-module` changes
    /// to the module finders and built-in loader metadata before applying additional
    /// reads, exports, opens, and native-access grants.
    ///
    /// The flow is:
    ///
    /// 1. Read the runtime-initialized `jdk.module.*` properties for the launcher-selected main
    /// module and the supported module options.
    /// 2. Update system module references and the build-time boot configuration when
    /// `--patch-module` is present.
    /// 3. Resolve only roots that are not already part of the build-time boot layer. Modules from
    /// `--upgrade-module-path` are searched before system modules, but cannot replace modules
    /// already built into the image boot layer.
    /// 4. Create the corresponding runtime [Module] objects directly using the same built-in
    /// loader mapping that HotSpot uses for boot-layer modules, then fold them into the real boot
    /// layer without changing [ModuleLayer#boot] identity.
    /// 5. Update built-in loader metadata for patched modules and apply extra reads, exports,
    /// opens, and native-access grants selected at launch time.
    ///
    /// If [ModuleLayer#boot] is unavailable, the method returns without making changes.
    ///
    /// This method follows the layout as much as possible of `ModuleBootstrap.boot2()`
    /// and adopts the same name to highlight this connection.
    static void boot2() {
        ModuleLayer bootLayer = ModuleLayer.boot();
        if (bootLayer == null) {
            return;
        }

        Target_jdk_internal_module_ModuleBootstrap.patcher = Target_jdk_internal_module_ModuleBootstrap.initModulePatcher();
        ModulePatcher patcher = SubstrateUtil.cast(Target_jdk_internal_module_ModuleBootstrap.patcher, ModulePatcher.class);
        mrefsPatcher = patcher.hasPatches() ? new IdentityHashMap<>() : null;

        ModuleFinder upgradeModulePath = Target_jdk_internal_module_ModuleBootstrap.finderFor(UPGRADE_MODULE_PATH_PROPERTY);
        ModuleFinder appModulePath = Target_jdk_internal_module_ModuleBootstrap.finderFor(MODULE_PATH_PROPERTY);
        String mainModule = System.getProperty(MAIN_MODULE_PROPERTY);
        Set<String> addModules = Target_jdk_internal_module_ModuleBootstrap.addModules();

        ModuleFinder systemModuleFinder = ModuleFinder.ofSystem();
        Configuration buildTimeBootConfiguration = bootLayer.configuration();
        if (patcher.hasPatches()) {
            // Fixup system modules for --patch-modules
            Set<ModuleReference> all = systemModuleFinder.findAll();
            Map<String, ModuleReference> nameToModule = new HashMap<>(all.size());
            for (var mref : all) {
                nameToModule.put(mref.descriptor().name(), patchedIfNeeded(mref));
            }
            systemModuleFinder = new MapBackedModuleFinder(nameToModule);
            Target_jdk_internal_module_SystemModuleFinders.cachedSystemModuleFinder = systemModuleFinder;

            // Fixup ResolvedModules in boot configuration for --patch-modules
            buildTimeBootConfiguration.modules().forEach(module -> {
                Target_java_lang_module_ResolvedModule rm = SubstrateUtil.cast(module, Target_java_lang_module_ResolvedModule.class);
                ModuleReference patchedMref = patchedIfNeeded(rm.mref);
                if (rm.mref != patchedMref) {
                    rm.mref = patchedMref;
                    // Keep the image-resident Module object consistent with its patched reference.
                    Module bootModule = bootLayer.findModule(module.name()).orElseThrow();
                    Target_java_lang_Module targetBootModule = SubstrateUtil.cast(bootModule, Target_java_lang_Module.class);
                    targetBootModule.descriptor = rm.mref.descriptor();
                }
            });
        }

        ModuleFinder finder;

        // upgraded modules override the modules in the run-time image
        if (upgradeModulePath != null) {
            rejectUpgradeModulePathReplacements(buildTimeBootConfiguration, upgradeModulePath);
            systemModuleFinder = ModuleFinder.compose(upgradeModulePath, systemModuleFinder);
        }

        // The module finder: [--upgrade-module-path] system [--module-path]
        if (appModulePath != null) {
            validateAppModulePath(appModulePath);
            finder = ModuleFinder.compose(systemModuleFinder, appModulePath);
        } else {
            finder = systemModuleFinder;
        }

        boolean hasRuntimeObservablePath = upgradeModulePath != null || appModulePath != null;

        // The root modules to resolve
        Set<String> roots = new HashSet<>();
        Set<String> explicitRoots = new LinkedHashSet<>();

        // launcher -m option to specify the main/initial module
        if (mainModule != null) {
            roots.add(mainModule);
            explicitRoots.add(mainModule);
        }

        // additional module(s) specified by --add-modules
        boolean addAllDefaultModules = false;
        boolean addAllSystemModules = false;
        boolean addAllApplicationModules = false;
        for (String mod : addModules) {
            switch (mod) {
                case ALL_DEFAULT:
                    addAllDefaultModules = true;
                    break;
                case ALL_SYSTEM:
                    addAllSystemModules = true;
                    break;
                case ALL_MODULE_PATH:
                    addAllApplicationModules = true;
                    break;
                default:
                    roots.add(mod);
                    explicitRoots.add(mod);
            }
        }

        // --limit-modules (TBD)

        // If there is no initial module specified then assume that the initial
        // module is the unnamed module of the application class loader. This
        // is implemented by resolving all observable modules that export an
        // API. Modules that have the DO_NOT_RESOLVE_BY_DEFAULT bit set in
        // their ModuleResolution attribute flags are excluded from the
        // default set of roots.
        //
        // Runtime path options make an unnamed-module launch observe the HotSpot default roots.
        // Without those options, the image's existing boot layer remains authoritative.
        if ((mainModule == null && hasRuntimeObservablePath) || addAllDefaultModules) {
            for (String name : Target_jdk_internal_module_DefaultRoots.compute(systemModuleFinder, finder)) {
                if (!hasIncompatibleBuiltinLoaderModule(name, finder)) {
                    roots.add(name);
                }
            }
        }

        // If `--add-modules ALL-SYSTEM` is specified, then all observable system
        // modules will be resolved.
        if (addAllSystemModules) {
            ModuleFinder f = finder;  // observable modules
            systemModuleFinder.findAll() //
                            .stream() //
                            .map(ModuleReference::descriptor) //
                            .map(ModuleDescriptor::name) //
                            .filter(name -> f.find(name).isPresent()) // observable
                            .forEach(roots::add);
        }

        // If `--add-modules ALL-MODULE-PATH` is specified, then all observable
        // modules on the application module path will be resolved.
        if (appModulePath != null && addAllApplicationModules) {
            ModuleFinder f = finder;  // observable modules
            appModulePath.findAll().stream() //
                            .map(ModuleReference::descriptor) //
                            .map(ModuleDescriptor::name) //
                            .filter(mn -> f.find(mn).isPresent())  // observable
                            .forEach(mn -> {
                                roots.add(mn);
                                explicitRoots.add(mn);
                            });
        }

        /*
         * Explicitly requested roots need a diagnostic before pruning. Default roots are only
         * candidates discovered from the observable module set, so incompatible defaults can be
         * filtered silently. A module already in the build-time boot configuration is not a
         * runtime augmentation candidate at all: the preserved boot-layer module remains
         * authoritative, and replacement attempts from --upgrade-module-path are rejected earlier.
         * The loader-compatibility rejection is only for modules absent from that configuration,
         * because adding one would require registering a runtime ModuleReference in a built-in
         * loader that already preserves a different reference for the same name.
         */
        rejectUnrepresentableExplicitRoots(buildTimeBootConfiguration, finder, explicitRoots);
        roots.removeIf(moduleName -> !isRuntimeAugmentationCandidate(buildTimeBootConfiguration, finder, moduleName));
        if (!roots.isEmpty()) {
            Configuration augmentationConfiguration = resolveAugmentationConfiguration(buildTimeBootConfiguration, finder, roots);
            Set<ResolvedModule> runtimeModules = selectNewRuntimeModules(buildTimeBootConfiguration, augmentationConfiguration, finder, roots);
            rejectUnrepresentedExplicitRoots(buildTimeBootConfiguration, explicitRoots, runtimeModules);
            if (!runtimeModules.isEmpty()) {
                Configuration mergedConfiguration = createAugmentedBootConfiguration(buildTimeBootConfiguration, runtimeModules);
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

        patchBuiltinLoaderModuleReferences(bootLayer);

        Target_jdk_internal_module_ModuleBootstrap.addExtraReads(bootLayer);
        Target_jdk_internal_module_ModuleBootstrap.addExtraExportsAndOpens(bootLayer);
        ModuleBootstrapSubstitutionsSupport.addRuntimeEnableNativeAccessModules(bootLayer);
    }

    /// Finds all [ModuleReference] values referenced from `BuiltinClassLoader.nameToModule`
    /// and `BuiltinClassLoader.packageToModule` and replaces them with the result of
    /// [#patchedIfNeeded] if it returns a different (i.e., patched) value.
    ///
    /// This method also warns for `--patch-module` targets that are not in the boot layer
    /// or overlap with AOT classes in the image (which are unpatchable).
    /// The latter check requires scanning the paths specified to `--patch-module`
    /// which can be expensive. However, silently failing to patch a class can be
    /// very confusing and given that `--patch-module` is typically used as
    /// a development or testing tool, this is the right trade off.
    private static void patchBuiltinLoaderModuleReferences(ModuleLayer bootLayer) {
        for (Map.Entry<String, List<Path>> entry : Target_jdk_internal_module_ModuleBootstrap.patcher.map.entrySet()) {
            String moduleName = entry.getKey();
            Optional<Module> module = bootLayer.findModule(moduleName);
            if (module.isEmpty()) {
                LogUtils.warning("Unknown module: " + moduleName + " specified to --patch-module");
                continue;
            }

            ClassLoader classLoader = module.get().getClassLoader();
            Target_jdk_internal_loader_BuiltinClassLoader builtinLoader;
            if (classLoader == null) {
                builtinLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
            } else if (classLoader instanceof jdk.internal.loader.BuiltinClassLoader) {
                builtinLoader = SubstrateUtil.cast(classLoader, Target_jdk_internal_loader_BuiltinClassLoader.class);
            } else {
                throw VMError.shouldNotReachHere("Patched boot-layer module is not assigned to a built-in class loader: " + moduleName);
            }
            ModuleReference moduleReference = builtinLoader.findModule(moduleName);
            if (moduleReference == null) {
                throw VMError.shouldNotReachHere("Missing built-in loader module reference for patched module " + moduleName);
            }
            ModuleReference patchedReference = patchedIfNeeded(moduleReference);
            if (patchedReference != moduleReference) {
                BuiltinClassLoaderSubstitutionsSupport.patchModuleReference(builtinLoader, moduleReference, patchedReference);
            }
            for (Path path : entry.getValue()) {
                checkModulePatchForAOTClasses(moduleName, classLoader, path);
            }
        }
    }

    /// Resolves the runtime root modules against the existing boot configuration.
    ///
    /// The existing boot configuration remains authoritative. Modules already present in
    /// `buildTimeBootConfiguration` are therefore found via the parent configuration, while `finder`
    /// only contributes modules that are not already in the boot layer.
    private static Configuration resolveAugmentationConfiguration(Configuration buildTimeBootConfiguration, ModuleFinder finder, Set<String> roots) {
        return Configuration.resolve(ModuleFinder.of(), List.of(buildTimeBootConfiguration), finder, roots);
    }

    /// Selects only the modules from the augmentation configuration that are not already present in
    /// the boot configuration, then resolves any additional provider modules induced by service
    /// binding from those runtime-resolved modules.
    private static Set<ResolvedModule> selectNewRuntimeModules(Configuration buildTimeBootConfiguration, Configuration augmentationConfiguration, ModuleFinder finder, Set<String> roots) {
        LinkedHashMap<String, ResolvedModule> runtimeModules = new LinkedHashMap<>();
        collectRuntimeModules(runtimeModules, buildTimeBootConfiguration, finder, augmentationConfiguration.modules());

        if (!runtimeModules.isEmpty()) {
            Configuration bindingConfiguration = augmentationConfiguration.resolveAndBind(ModuleFinder.of(), finder, roots);
            collectRuntimeModules(runtimeModules, augmentationConfiguration, finder, bindingConfiguration.modules());
        }

        pruneRuntimeModules(buildTimeBootConfiguration, runtimeModules);
        return new LinkedHashSet<>(runtimeModules.values());
    }

    /// Collects runtime-resolved modules that are not already known and can be represented by the
    /// built-in loader module maps.
    private static void collectRuntimeModules(Map<String, ResolvedModule> target, Configuration knownConfiguration, ModuleFinder observableModuleFinder, Collection<ResolvedModule> candidates) {
        for (ResolvedModule resolvedModule : candidates) {
            String moduleName = resolvedModule.name();
            if (isRuntimeAugmentationCandidate(knownConfiguration, observableModuleFinder, moduleName)) {
                target.put(moduleName, resolvedModule);
            }
        }
    }

    /// Removes runtime modules whose required dependencies were filtered from the augmentation set.
    private static void pruneRuntimeModules(Configuration buildTimeBootConfiguration, Map<String, ResolvedModule> runtimeModules) {
        boolean changed;
        do {
            changed = false;
            Iterator<ResolvedModule> iterator = runtimeModules.values().iterator();
            while (iterator.hasNext()) {
                ResolvedModule resolvedModule = iterator.next();
                if (!hasRepresentableDependencies(buildTimeBootConfiguration, runtimeModules, resolvedModule)) {
                    iterator.remove();
                    changed = true;
                }
            }
        } while (changed);
    }

    /// Tests whether every mandatory dependency is present in the build-time boot layer or runtime set.
    private static boolean hasRepresentableDependencies(Configuration buildTimeBootConfiguration, Map<String, ResolvedModule> runtimeModules, ResolvedModule resolvedModule) {
        for (Requires dependency : resolvedModule.reference().descriptor().requires()) {
            if (dependency.modifiers().contains(Requires.Modifier.STATIC)) {
                continue;
            }
            String dependencyName = dependency.name();
            if (buildTimeBootConfiguration.findModule(dependencyName).isEmpty() && !runtimeModules.containsKey(dependencyName)) {
                return false;
            }
        }
        return true;
    }

    /// Rejects only the part of `--upgrade-module-path` that is impossible for SVM to implement:
    /// replacing a module already present in the prebuilt boot layer. Entries for modules not
    /// already in the boot layer remain supported and are resolved before system modules.
    private static void rejectUpgradeModulePathReplacements(Configuration buildTimeBootConfiguration, ModuleFinder upgradeModulePath) {
        rejectBootLayerReplacements(buildTimeBootConfiguration, upgradeModulePath, UPGRADE_MODULE_PATH_OPTION, "replace");
    }

    /// Rejects any upgrade path entry that collides by name with a module already present in the
    /// prebuilt boot layer.
    ///
    /// Runtime boot-layer augmentation can only add modules that were absent at image build time.
    /// Replacing an already-built boot-layer module from the upgrade module path would create an
    /// unsupported ambiguity between the preserved boot layer and the runtime path entry.
    private static void rejectBootLayerReplacements(Configuration buildTimeBootConfiguration, ModuleFinder finder, String optionName, String action) {
        for (ModuleReference moduleReference : finder.findAll()) {
            String moduleName = moduleReference.descriptor().name();
            if (buildTimeBootConfiguration.findModule(moduleName).isPresent()) {
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
    /// augmentation, we therefore synthesize a finder containing both the build-time boot-layer
    /// modules and the newly resolved runtime modules, and resolve all module names again into one
    /// merged configuration.
    private static Configuration createAugmentedBootConfiguration(Configuration buildTimeBootConfiguration, Set<ResolvedModule> runtimeModules) {
        LinkedHashSet<String> roots = buildTimeBootConfiguration.modules().stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).collect(
                        Collectors.toCollection(LinkedHashSet::new));
        runtimeModules.stream().map(ResolvedModule::reference).map(ModuleReference::descriptor).map(ModuleDescriptor::name).forEach(roots::add);

        LinkedHashMap<String, ModuleReference> references = new LinkedHashMap<>();
        addModuleReferences(references, buildTimeBootConfiguration.modules());
        addModuleReferences(references, runtimeModules);
        ModuleFinder finder = new MapBackedModuleFinder(references);
        try {
            List<Configuration> parents = buildTimeBootConfiguration.parents();
            return Configuration.resolve(finder, parents, ModuleFinder.of(), roots);
        } catch (FindException | ResolutionException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Failed to rebuild the augmented runtime boot module layer configuration.", ex);
        }
    }

    private static void addModuleReferences(Map<String, ModuleReference> references, Collection<ResolvedModule> modules) {
        for (ResolvedModule resolvedModule : modules) {
            ModuleReference mref = resolvedModule.reference();
            if (patchedIfNeeded(mref) != mref) {
                VMError.shouldNotReachHere("Module reference " + mref + " was patched by --patch-module");
            }
            references.put(mref.descriptor().name(), mref);
        }
    }

    /// Carries bytecode loaded from `--patch-module` together with the patch entry that supplied it.
    public record PatchedModuleClass(byte[] bytes, URL codeSourceURL) {
    }

    /// Creates class definition metadata that matches the `CodeSource` HotSpot assigns to classes loaded from `--patch-module`.
    public static RuntimeClassLoading.ClassDefinitionInfo createPatchedClassDefinitionInfo(ClassLoader loader, URL codeSourceURL) {
        CodeSource codeSource = new CodeSource(codeSourceURL, (CodeSigner[]) null);
        ProtectionDomain protectionDomain = new ProtectionDomain(codeSource, new Permissions(), loader, null);
        return new RuntimeClassLoading.ClassDefinitionInfo(protectionDomain);
    }

    /// Loads `resourceName` from the patch paths for `moduleName` if that module is resolved
    /// by the boot loader.
    ///
    /// A `--patch-module` target is effective only if the target module is in the runtime root
    /// module graph. The boot layer is augmented before application code runs, so membership in
    /// `ModuleLayer.boot()` is the runtime check for that rule.
    public static PatchedModuleClass loadPatchedModuleBootLoaderClass(String moduleName, String resourceName) throws IOException {
        if (moduleName == null || ModuleLayer.boot().findModule(moduleName).isEmpty()) {
            return null;
        }
        Target_jdk_internal_loader_BuiltinClassLoader loader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        ModuleReference moduleReference = loader.findModule(moduleName);
        if (moduleReference == null) {
            return null;
        }
        ModuleReader moduleReader = loader.moduleReaderFor(moduleReference);
        if (!(moduleReader instanceof jdk.internal.module.ModulePatcher.PatchedModuleReader)) {
            return null;
        }
        jdk.internal.loader.Resource resource = SubstrateUtil.cast(moduleReader, Target_jdk_internal_module_ModulePatcher_PatchedModuleReader.class).findResource(resourceName);
        if (resource == null) {
            return null;
        }
        // Reuse the JDK reader so patched jar files remain shared across class lookups.
        return new PatchedModuleClass(resource.getBytes(), resource.getCodeSourceURL());
    }

    /// Warns for each class file found under `path` that overlaps with an AOT class
    /// associated with `loader`. Such an AOT class cannot be patched.
    ///
    /// @param moduleName the name of a module in a `--patch-module` argument
    /// @param path the patch path for `moduleName` specified by `--patch-module`
    private static void checkModulePatchForAOTClasses(String moduleName, ClassLoader loader, Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.walk(path)) {
                    entries.filter(Files::isRegularFile).forEach(classFile -> warnPatchModuleAOTClassOverlap(moduleName, loader, path, path.relativize(classFile).toString()));
                }
            } else if (Files.isRegularFile(path)) {
                try (JarFile jarFile = new JarFile(path.toFile())) {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();
                    while (jarEntries.hasMoreElements()) {
                        JarEntry jarEntry = jarEntries.nextElement();
                        if (!jarEntry.isDirectory()) {
                            warnPatchModuleAOTClassOverlap(moduleName, loader, path, jarEntry.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LogUtils.warning("Could not scan " + path + " specified to --patch-module for module " + moduleName + ": " + e);
        }
    }

    /// Warns when `resourceName` denotes an AOT-loaded class in `moduleName`.
    ///
    /// @param moduleName the name of a module in a `--patch-module` argument
    /// @param path the patch path for `moduleName` specified by `--patch-module`
    /// @param resourceName a resource (i.e., file or zip entry) available under `path`
    private static void warnPatchModuleAOTClassOverlap(String moduleName, ClassLoader loader, Path path, String resourceName) {
        String normalizedResourceName = resourceName.replace(File.separatorChar, '/');
        if (!normalizedResourceName.endsWith(".class") || normalizedResourceName.equals("module-info.class")) {
            return;
        }
        String className = normalizedResourceName.substring(0, normalizedResourceName.length() - ".class".length()).replace('/', '.');
        if (ClassRegistries.hasAOTLoadedClass(className, loader)) {
            String sep = Files.isDirectory(path) ? File.separator : "!";
            LogUtils.warning("Class " + className + " from --patch-module=" + moduleName + " (" + path + sep + resourceName + ")" +
                            " overlaps with a class already loaded from the image; the patch class will not replace the AOT class");
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
                if (!isCompatibleModuleReference(mref, resolvedModule.reference())) {
                    throw new IllegalArgumentException("Runtime boot-layer resolved module has conflicting refs '" + resolvedModule.reference() + "' != '" + mref + "'");
                }
            } else {
                builtinLoader.loadModule(resolvedModule.reference());
            }
        }
    }

    /// Forces launcher-compatible validation of every runtime `--module-path` entry.
    ///
    /// Calling [ModuleFinder#findAll] eagerly scans the whole module path, parses explicit module
    /// descriptors or derives automatic-module descriptors, and reports malformed or unreadable
    /// entries, as well as duplicate module names within one module-path directory, through the JDK
    /// module finder before root selection can ignore an unused path entry.
    private static void validateAppModulePath(ModuleFinder appModulePath) {
        if (appModulePath != null) {
            appModulePath.findAll();
        }
    }

    /// Fails startup when a user-named root resolves to a module that conflicts with a built-in
    /// loader entry outside the build-time boot configuration.
    private static void rejectUnrepresentableExplicitRoots(Configuration buildTimeBootConfiguration, ModuleFinder observableModuleFinder, Set<String> explicitRoots) {
        for (String moduleName : explicitRoots) {
            /*
             * A module can be absent from the boot configuration but still have a preserved
             * BuiltinClassLoader.nameToModule entry. Such a module cannot be re-added from a
             * different runtime path entry because the loader can only represent one reference for
             * that name.
             */
            if (buildTimeBootConfiguration.findModule(moduleName).isEmpty() && hasIncompatibleBuiltinLoaderModule(moduleName, observableModuleFinder)) {
                throw new IllegalArgumentException("The explicitly requested module '" + moduleName +
                                "' cannot be added to the runtime boot layer because a different module reference is already registered in a built-in class loader");
            }
        }
    }

    /// Fails startup when a user-named root was pruned because one of its dependencies was filtered.
    private static void rejectUnrepresentedExplicitRoots(Configuration buildTimeBootConfiguration, Set<String> explicitRoots, Set<ResolvedModule> runtimeModules) {
        Set<String> runtimeModuleNames = runtimeModules.stream().map(ResolvedModule::name).collect(Collectors.toSet());
        for (String moduleName : explicitRoots) {
            if (buildTimeBootConfiguration.findModule(moduleName).isEmpty() && !runtimeModuleNames.contains(moduleName)) {
                throw new IllegalArgumentException("The explicitly requested module '" + moduleName +
                                "' cannot be added to the runtime boot layer because one or more of its mandatory dependencies cannot be represented");
            }
        }
    }

    /// Tests whether `moduleName` can be added as a newly resolved runtime boot-layer module.
    private static boolean isRuntimeAugmentationCandidate(Configuration knownConfiguration, ModuleFinder observableModuleFinder, String moduleName) {
        if (knownConfiguration.findModule(moduleName).isPresent()) {
            return false;
        }
        return !hasIncompatibleBuiltinLoaderModule(moduleName, observableModuleFinder);
    }

    /// Tests whether a built-in loader already has an incompatible reference for `moduleName`.
    private static boolean hasIncompatibleBuiltinLoaderModule(String moduleName, ModuleFinder observableModuleFinder) {
        Optional<ModuleReference> moduleReference = observableModuleFinder.find(moduleName);
        if (moduleReference.isEmpty()) {
            return false;
        }
        ModuleReference buildTimeReference = findBuildTimeBuiltinLoaderModule(moduleName);
        return buildTimeReference != null && !isCompatibleModuleReference(buildTimeReference, moduleReference.get());
    }

    /// Tests whether two module references describe the same runtime-representable module.
    ///
    /// For example, a module loaded at build-time from `file:///app/modules/foo.jar` remains compatible
    /// with a runtime reference to that same jar. A module preserved in the image with a redacted
    /// location such as `file:///REDACTED/foo` is also compatible when its
    /// [restored path][ResourceBasedModuleReaderSupport#getRuntimeModuleLocation(String)] is on
    /// `--module-path` (e.g. `--module-path=/app/modules/foo.jar`).
    /// In contrast, a redacted build-time reference for `foo` is not compatible with a runtime
    /// reference coming only from `--upgrade-module-path`, because redacted path restoration ignores
    /// `--upgrade-module-path`.
    private static boolean isCompatibleModuleReference(ModuleReference buildTimeReference, ModuleReference runtimeReference) {
        if (!buildTimeReference.descriptor().equals(runtimeReference.descriptor())) {
            return false;
        }
        Optional<URI> buildTimeLocation = buildTimeReference.location();
        Optional<URI> runtimeLocation = runtimeReference.location();
        if (buildTimeLocation.equals(runtimeLocation)) {
            return true;
        }
        if (buildTimeLocation.isEmpty()) {
            return false;
        }
        String moduleName = buildTimeReference.descriptor().name();
        String redactedModuleName = ResourceBasedModuleReaderSupport.getRedactedModuleName(buildTimeLocation.get());
        if (redactedModuleName == null) {
            // Build-time module location was not redacted
            return false;
        }
        assert moduleName.equals(redactedModuleName);
        return ResourceBasedModuleReaderSupport.getRuntimeModuleLocation(moduleName).equals(runtimeLocation);
    }

    /// Finds a build-time module reference preserved in any built-in loader.
    private static ModuleReference findBuildTimeBuiltinLoaderModule(String moduleName) {
        ModuleReference bootModuleReference = Target_jdk_internal_loader_ClassLoaders.bootLoader().findModule(moduleName);
        if (bootModuleReference != null) {
            return bootModuleReference;
        }
        ModuleReference platformModuleReference = findBuildTimeBuiltinLoaderModule(Target_jdk_internal_loader_ClassLoaders.platformClassLoader(), moduleName);
        if (platformModuleReference != null) {
            return platformModuleReference;
        }
        return findBuildTimeBuiltinLoaderModule(ClassLoader.getSystemClassLoader(), moduleName);
    }

    private static ModuleReference findBuildTimeBuiltinLoaderModule(ClassLoader classLoader, String moduleName) {
        if (classLoader instanceof jdk.internal.loader.BuiltinClassLoader) {
            return SubstrateUtil.cast(classLoader, Target_jdk_internal_loader_BuiltinClassLoader.class).findModule(moduleName);
        }
        return null;
    }

    /// Gets the runtime-patched reference for `mref` when `--patch-module` is active.
    ///
    /// Already patched references and references from an image without patches are returned
    /// unchanged. Other references are patched once per input module reference and the cached
    /// result is reused for subsequent lookups.
    ///
    /// @param mref the module reference to patch when necessary
    /// @return the patched module reference, or `mref` when no patch is needed
    static ModuleReference patchedIfNeeded(ModuleReference mref) {
        if (mrefsPatcher == null || mref instanceof ModuleReferenceImpl impl && impl.isPatched()) {
            return mref;
        }
        return mrefsPatcher.computeIfAbsent(mref, Target_jdk_internal_module_ModuleBootstrap.patcher::patchIfNeeded);
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
            Collection<ModuleReference> values = references.values();
            this.modules = Set.copyOf(values);
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
