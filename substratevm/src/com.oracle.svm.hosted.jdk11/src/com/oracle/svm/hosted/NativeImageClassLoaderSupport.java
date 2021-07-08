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
package com.oracle.svm.hosted;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.internal.module.Modules;

public class NativeImageClassLoaderSupport extends AbstractNativeImageClassLoaderSupport {

    private final List<Path> imagemp;
    private final List<Path> buildmp;

    private final ClassLoader classLoader;
    public final ModuleLayer moduleLayerForImageBuild;

    NativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath, String[] modulePath) {
        super(defaultSystemClassLoader, classpath);

        imagemp = Arrays.stream(modulePath).map(Paths::get).collect(Collectors.toUnmodifiableList());
        buildmp = Arrays.stream(System.getProperty("jdk.module.path", "").split(File.pathSeparator)).map(Paths::get).collect(Collectors.toUnmodifiableList());

        ModuleLayer moduleLayer = createModuleLayer(imagemp.toArray(Path[]::new), classPathClassLoader);
        adjustBootLayerQualifiedExports(moduleLayer);
        moduleLayerForImageBuild = moduleLayer;
        classLoader = getSingleClassloader(moduleLayer);
    }

    private static ModuleLayer createModuleLayer(Path[] modulePaths, ClassLoader parent) {
        ModuleFinder finder = ModuleFinder.of(modulePaths);
        List<Configuration> parents = List.of(ModuleLayer.boot().configuration());
        Set<String> moduleNames = finder.findAll().stream().map(moduleReference -> moduleReference.descriptor().name()).collect(Collectors.toSet());
        Configuration configuration = Configuration.resolve(finder, parents, finder, moduleNames);
        /**
         * For the modules we want to build an image for, a ModuleLayer is needed that can be
         * accessed with a single classloader so we can use it for {@link ImageClassLoader}.
         */
        return ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), parent).layer();
    }

    private void adjustBootLayerQualifiedExports(ModuleLayer layer) {
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

    @Override
    public List<Path> modulepath() {
        return Stream.concat(buildmp.stream(), imagemp.stream()).collect(Collectors.toList());
    }

    @Override
    List<Path> applicationModulePath() {
        return imagemp;
    }

    @Override
    public Optional<Module> findModule(String moduleName) {
        return moduleLayerForImageBuild.findModule(moduleName);
    }

    @Override
    void processAddExportsAndAddOpens(OptionValues parsedHostedOptions) {
        LocatableMultiOptionValue.Strings addExports = NativeImageClassLoaderOptions.AddExports.getValue(parsedHostedOptions);
        addExports.getValuesWithOrigins().map(this::asAddExportsAndOpensFormatValue).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addExportsToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addExports(val.module, val.packageName, targetModule);
                }
            }
        });
        LocatableMultiOptionValue.Strings addOpens = NativeImageClassLoaderOptions.AddOpens.getValue(parsedHostedOptions);
        addOpens.getValuesWithOrigins().map(this::asAddExportsAndOpensFormatValue).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                Modules.addOpensToAllUnnamed(val.module, val.packageName);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addOpens(val.module, val.packageName, targetModule);
                }
            }
        });
    }

    private static final class AddExportsAndOpensFormatValue {
        private final Module module;
        private final String packageName;
        private final List<Module> targetModules;

        private AddExportsAndOpensFormatValue(Module module, String packageName, List<Module> targetModules) {
            this.module = module;
            this.packageName = packageName;
            this.targetModules = targetModules;
        }
    }

    private AddExportsAndOpensFormatValue asAddExportsAndOpensFormatValue(Pair<String, String> valueOrigin) {
        String optionOrigin = valueOrigin.getRight();
        String optionValue = valueOrigin.getLeft();

        String syntaxErrorMessage = " Allowed value format: " + NativeImageClassLoaderOptions.AddExportsAndOpensFormat;

        String[] modulePackageAndTargetModules = optionValue.split("=", 2);
        if (modulePackageAndTargetModules.length != 2) {
            throw userErrorAddExportsAndOpens(optionOrigin, optionValue, syntaxErrorMessage);
        }
        String modulePackage = modulePackageAndTargetModules[0];
        String targetModuleNames = modulePackageAndTargetModules[1];

        String[] moduleAndPackage = modulePackage.split("/");
        if (moduleAndPackage.length != 2) {
            throw userErrorAddExportsAndOpens(optionOrigin, optionValue, syntaxErrorMessage);
        }
        String moduleName = moduleAndPackage[0];
        String packageName = moduleAndPackage[1];

        List<String> targetModuleNamesList = Arrays.asList(targetModuleNames.split(","));
        if (targetModuleNamesList.isEmpty()) {
            throw userErrorAddExportsAndOpens(optionOrigin, optionValue, syntaxErrorMessage);
        }

        Module module = findModule(moduleName).orElseThrow(() -> {
            return userErrorAddExportsAndOpens(optionOrigin, optionValue, " Specified module '" + moduleName + "' is unknown.");
        });
        List<Module> targetModules;
        if (targetModuleNamesList.contains("ALL-UNNAMED")) {
            targetModules = Collections.emptyList();
        } else {
            targetModules = targetModuleNamesList.stream().map(mn -> {
                return findModule(mn).orElseThrow(() -> {
                    throw userErrorAddExportsAndOpens(optionOrigin, optionValue, " Specified target-module '" + mn + "' is unknown.");
                });
            }).collect(Collectors.toList());
        }
        return new AddExportsAndOpensFormatValue(module, packageName, targetModules);
    }

    private static UserError.UserException userErrorAddExportsAndOpens(String origin, String value, String detailMessage) {
        Objects.requireNonNull(detailMessage, "missing detailMessage");
        return UserError.abort("Invalid option %s provided by %s." + detailMessage, SubstrateOptionsParser.commandArgument(NativeImageClassLoaderOptions.AddExports, value), origin);
    }

    @Override
    Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
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
    Optional<String> getMainClassFromModule(Object module) {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        return ((Module) module).getDescriptor().mainClass();
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private static ModuleFinder upgradeAndSystemModuleFinder;

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

    /**
     * Gets a finder that locates the upgrade modules and the system modules, in that order.
     */
    private static ModuleFinder getUpgradeAndSystemModuleFinder() {
        if (upgradeAndSystemModuleFinder == null) {
            ModuleFinder finder = ModuleFinder.ofSystem();
            ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
            if (upgradeModulePath != null) {
                finder = ModuleFinder.compose(upgradeModulePath, finder);
            }
            upgradeAndSystemModuleFinder = finder;
        }
        return upgradeAndSystemModuleFinder;
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

            for (ModuleReference moduleReference : getUpgradeAndSystemModuleFinder().findAll()) {
                if (requiresInit.contains(moduleReference.descriptor().name())) {
                    initModule(moduleReference);
                }
            }
            for (ModuleReference moduleReference : ModuleFinder.of(modulepath().toArray(Path[]::new)).findAll()) {
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
