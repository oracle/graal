/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.AbstractNativeImageClassLoaderSupport;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageClassLoaderOptions;
import com.oracle.svm.util.ModuleSupport;

import jdk.internal.module.Modules;

public class NativeImageClassLoaderSupportJDK11OrLater extends AbstractNativeImageClassLoaderSupport {

    private final List<Path> imagemp;
    private final List<Path> buildmp;

    private final ClassLoader classLoader;

    public final ModuleFinder upgradeAndSystemModuleFinder;
    public final ModuleLayer moduleLayerForImageBuild;

    public final ModuleFinder modulepathModuleFinder;

    public NativeImageClassLoaderSupportJDK11OrLater(ClassLoader defaultSystemClassLoader, String[] classpath, String[] modulePath) {
        super(defaultSystemClassLoader, classpath);

        imagemp = Arrays.stream(modulePath).map(Paths::get).collect(Collectors.toUnmodifiableList());
        buildmp = Optional.ofNullable(System.getProperty("jdk.module.path")).stream()
                        .flatMap(s -> Arrays.stream(s.split(File.pathSeparator))).map(Paths::get).collect(Collectors.toUnmodifiableList());

        upgradeAndSystemModuleFinder = createUpgradeAndSystemModuleFinder();
        ModuleLayer moduleLayer = createModuleLayer(imagemp.toArray(Path[]::new), classPathClassLoader);
        adjustBootLayerQualifiedExports(moduleLayer);
        moduleLayerForImageBuild = moduleLayer;

        classLoader = getSingleClassloader(moduleLayer);

        modulepathModuleFinder = ModuleFinder.of(modulepath().toArray(Path[]::new));
    }

    private ModuleLayer createModuleLayer(Path[] modulePaths, ClassLoader parent) {
        ModuleFinder modulePathsFinder = ModuleFinder.of(modulePaths);
        Set<String> moduleNames = modulePathsFinder.findAll().stream().map(moduleReference -> moduleReference.descriptor().name()).collect(Collectors.toSet());

        /**
         * When building a moduleLayer for the module-path passed to native-image we need to be able
         * to resolve against system modules that are not used by the moduleLayer in which the
         * image-builder got loaded into. To do so we use {@link upgradeAndSystemModuleFinder} as
         * {@code ModuleFinder after} in
         * {@link Configuration#resolve(ModuleFinder, ModuleFinder, Collection)}.
         */
        Configuration configuration = ModuleLayer.boot().configuration().resolve(modulePathsFinder, upgradeAndSystemModuleFinder, moduleNames);
        /**
         * For the modules we want to build an image for, a ModuleLayer is needed that can be
         * accessed with a single classloader so we can use it for {@link ImageClassLoader}.
         */
        return ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), parent).layer();
    }

    /**
     * Gets a finder that locates the upgrade modules and the system modules, in that order. Upgrade
     * modules are used when mx environment variable {@code MX_BUILD_EXPLODED=true} is used.
     */
    private static ModuleFinder createUpgradeAndSystemModuleFinder() {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        if (upgradeModulePath != null) {
            finder = ModuleFinder.compose(upgradeModulePath, finder);
        }
        return finder;
    }

    /**
     * Creates a finder from a module path specified by the {@code prop} system property.
     */
    private static ModuleFinder finderFor(String prop) {
        String s = System.getProperty(prop);
        if (s == null || s.isEmpty()) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir : dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModuleFinder.of(paths);
        }
    }

    private static void adjustBootLayerQualifiedExports(ModuleLayer layer) {
        /*
         * For all qualified exports packages of modules in the the boot layer we check if layer
         * contains modules that satisfy such qualified exports. If we find a match we perform a
         * addExports.
         */
        for (Module module : ModuleLayer.boot().modules()) {
            for (ModuleDescriptor.Exports export : module.getDescriptor().exports()) {
                for (String target : export.targets()) {
                    Optional<Module> optExportTargetModule = layer.findModule(target);
                    if (optExportTargetModule.isEmpty()) {
                        continue;
                    }
                    Module exportTargetModule = optExportTargetModule.get();
                    if (module.isExported(export.source(), exportTargetModule)) {
                        continue;
                    }
                    Modules.addExports(module, export.source(), exportTargetModule);
                }
            }
        }
    }

    private ClassLoader getSingleClassloader(ModuleLayer moduleLayer) {
        ClassLoader singleClassloader = classPathClassLoader;
        for (Module module : moduleLayer.modules()) {
            ClassLoader moduleClassLoader = module.getClassLoader();
            if (singleClassloader == classPathClassLoader) {
                singleClassloader = moduleClassLoader;
            } else {
                VMError.guarantee(singleClassloader == moduleClassLoader);
            }
        }
        return singleClassloader;
    }

    private static void implAddReadsAllUnnamed(Module module) {
        try {
            Method implAddReadsAllUnnamed = Module.class.getDeclaredMethod("implAddReadsAllUnnamed");
            ModuleSupport.openModuleByClass(Module.class, NativeImageClassLoaderSupportJDK11OrLater.class);
            implAddReadsAllUnnamed.setAccessible(true);
            implAddReadsAllUnnamed.invoke(module);
        } catch (ReflectiveOperationException | NoSuchElementException e) {
            VMError.shouldNotReachHere("Could reflectively call Module.implAddReadsAllUnnamed", e);
        }
    }

    @Override
    protected List<Path> modulepath() {
        return Stream.concat(imagemp.stream(), buildmp.stream()).collect(Collectors.toList());
    }

    @Override
    protected List<Path> applicationModulePath() {
        return imagemp;
    }

    @Override
    protected Optional<Module> findModule(String moduleName) {
        return moduleLayerForImageBuild.findModule(moduleName);
    }

    @Override
    protected void processClassLoaderOptions() {
        OptionValues optionValues = getParsedHostedOptions();

        processOption(optionValues, NativeImageClassLoaderOptions.AddExports).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addExportsToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addExports(val.module, val.packageName, targetModule);
                }
            }
        });
        processOption(optionValues, NativeImageClassLoaderOptions.AddOpens).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addOpensToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addOpens(val.module, val.packageName, targetModule);
                }
            }
        });
        processOption(optionValues, NativeImageClassLoaderOptions.AddReads).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                implAddReadsAllUnnamed(val.module);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addReads(val.module, targetModule);
                }
            }
        });
    }

    @Override
    public void propagateQualifiedExports(String fromTargetModule, String toTargetModule) {
        Optional<Module> optFromTarget = moduleLayerForImageBuild.findModule(fromTargetModule);
        Optional<Module> optToTarget = moduleLayerForImageBuild.findModule(toTargetModule);
        VMError.guarantee(!optFromTarget.isEmpty() && !optToTarget.isEmpty());
        Module toTarget = optToTarget.get();
        Module fromTarget = optFromTarget.get();

        allLayers(moduleLayerForImageBuild).stream().flatMap(layer -> layer.modules().stream()).forEach(m -> {
            if (!m.equals(toTarget)) {
                for (String p : m.getPackages()) {
                    if (m.isExported(p, fromTarget)) {
                        Modules.addExports(m, p, toTarget);
                    }
                    if (m.isOpen(p, fromTarget)) {
                        Modules.addOpens(m, p, toTarget);
                    }
                }
            }
        });
    }

    static List<ModuleLayer> allLayers(ModuleLayer moduleLayer) {
        /** Implementation taken from {@link ModuleLayer#layers()} */
        List<ModuleLayer> allLayers = new ArrayList<>();
        Set<ModuleLayer> visited = new HashSet<>();
        Deque<ModuleLayer> stack = new ArrayDeque<>();
        visited.add(moduleLayer);
        stack.push(moduleLayer);

        while (!stack.isEmpty()) {
            ModuleLayer layer = stack.pop();
            allLayers.add(layer);

            // push in reverse order
            for (int i = layer.parents().size() - 1; i >= 0; i--) {
                ModuleLayer parent = layer.parents().get(i);
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    stack.push(parent);
                }
            }
        }
        return allLayers;
    }

    private Stream<AddExportsAndOpensAndReadsFormatValue> processOption(OptionValues parsedHostedOptions, OptionKey<LocatableMultiOptionValue.Strings> specificOption) {
        Stream<Pair<String, String>> valuesWithOrigins = specificOption.getValue(parsedHostedOptions).getValuesWithOrigins();
        Stream<AddExportsAndOpensAndReadsFormatValue> parsedOptions = valuesWithOrigins.flatMap(valWithOrig -> {
            try {
                return Stream.of(asAddExportsAndOpensAndReadsFormatValue(specificOption, valWithOrig));
            } catch (UserError.UserException e) {
                if (ModuleSupport.modulePathBuild) {
                    throw e;
                } else {
                    /*
                     * Until we switch to always running the image-builder on module-path we have to
                     * be tolerant if invalid --add-exports -add-opens or --add-reads options are
                     * used. GR-30433
                     */
                    System.out.println("Warning: " + e.getMessage());
                    return Stream.empty();
                }
            }
        });
        return parsedOptions;
    }

    private static final class AddExportsAndOpensAndReadsFormatValue {
        private final Module module;
        private final String packageName;
        private final List<Module> targetModules;

        private AddExportsAndOpensAndReadsFormatValue(Module module, String packageName, List<Module> targetModules) {
            this.module = module;
            this.packageName = packageName;
            this.targetModules = targetModules;
        }
    }

    private AddExportsAndOpensAndReadsFormatValue asAddExportsAndOpensAndReadsFormatValue(OptionKey<?> option, Pair<String, String> valueOrigin) {
        String optionOrigin = valueOrigin.getRight();
        String optionValue = valueOrigin.getLeft();

        boolean reads = option.equals(NativeImageClassLoaderOptions.AddReads);
        String format = reads ? NativeImageClassLoaderOptions.AddReadsFormat : NativeImageClassLoaderOptions.AddExportsAndOpensFormat;
        String syntaxErrorMessage = " Allowed value format: " + format;

        String[] modulePackageAndTargetModules = optionValue.split("=", 2);
        if (modulePackageAndTargetModules.length != 2) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }
        String modulePackage = modulePackageAndTargetModules[0];
        String targetModuleNames = modulePackageAndTargetModules[1];

        String[] moduleAndPackage = modulePackage.split("/");
        if (moduleAndPackage.length > 1 + (reads ? 0 : 1)) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }
        String moduleName = moduleAndPackage[0];
        String packageName = moduleAndPackage.length > 1 ? moduleAndPackage[1] : null;

        List<String> targetModuleNamesList = Arrays.asList(targetModuleNames.split(","));
        if (targetModuleNamesList.isEmpty()) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }

        Module module = findModule(moduleName).orElseThrow(() -> {
            return userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, " Specified module '" + moduleName + "' is unknown.");
        });
        List<Module> targetModules;
        if (targetModuleNamesList.contains("ALL-UNNAMED")) {
            targetModules = Collections.emptyList();
        } else {
            targetModules = targetModuleNamesList.stream().map(mn -> {
                return findModule(mn).orElseThrow(() -> {
                    throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, " Specified target-module '" + mn + "' is unknown.");
                });
            }).collect(Collectors.toList());
        }
        return new AddExportsAndOpensAndReadsFormatValue(module, packageName, targetModules);
    }

    private static UserError.UserException userErrorAddExportsAndOpensAndReads(OptionKey<?> option, String origin, String value, String detailMessage) {
        Objects.requireNonNull(detailMessage, "missing detailMessage");
        return UserError.abort("Invalid option %s provided by %s." + detailMessage, SubstrateOptionsParser.commandArgument(option, value), origin);
    }

    @Override
    protected Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        Module m = (Module) module;
        assert isModuleClassLoader(classLoader, m.getClassLoader()) : "Argument `module` is java.lang.Module from unknown ClassLoader";
        return Class.forName(m, className);
    }

    private static boolean isModuleClassLoader(ClassLoader loader, ClassLoader moduleClassLoader) {
        if (moduleClassLoader == loader) {
            return true;
        } else {
            if (loader == null) {
                return false;
            }
            return isModuleClassLoader(loader.getParent(), moduleClassLoader);
        }
    }

    @Override
    protected Optional<String> getMainClassFromModule(Object module) {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        return ((Module) module).getDescriptor().mainClass();
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private class ClassInitWithModules extends ClassInit {

        ClassInitWithModules(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
            super(executor, imageClassLoader);
        }

        @Override
        protected void init() {
            List<String> requiresInit = Arrays.asList(
                            "jdk.internal.vm.ci", "jdk.internal.vm.compiler", "com.oracle.graal.graal_enterprise",
                            "org.graalvm.sdk", "org.graalvm.truffle");

            for (ModuleReference moduleReference : upgradeAndSystemModuleFinder.findAll()) {
                if (requiresInit.contains(moduleReference.descriptor().name())) {
                    initModule(moduleReference);
                }
            }
            for (ModuleReference moduleReference : modulepathModuleFinder.findAll()) {
                initModule(moduleReference);
            }

            super.init();
        }

        private void initModule(ModuleReference moduleReference) {
            Optional<Module> optionalModule = findModule(moduleReference.descriptor().name());
            if (optionalModule.isEmpty()) {
                return;
            }
            try (ModuleReader moduleReader = moduleReference.open()) {
                Module module = optionalModule.get();
                moduleReader.list().forEach(moduleResource -> {
                    if (moduleResource.endsWith(CLASS_EXTENSION)) {
                        executor.execute(() -> handleClassFileName(module, moduleResource, '/'));
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Unable get list of resources in module" + moduleReference.descriptor().name(), e);
            }
        }
    }

    @Override
    public void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new ClassInitWithModules(executor, imageClassLoader).init();
    }
}
