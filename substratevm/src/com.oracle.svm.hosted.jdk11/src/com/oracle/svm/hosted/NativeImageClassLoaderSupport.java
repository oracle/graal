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
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.ModuleSupport;

public class NativeImageClassLoaderSupport extends AbstractNativeImageClassLoaderSupport {

    private final List<Path> imagemp;
    private final List<Path> buildmp;

    private final ClassLoader classLoader;
    private final Function<String, Optional<Module>> moduleFinder;
    private final ModuleLayer.Controller moduleController;

    NativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath, String[] modulePath) {
        super(defaultSystemClassLoader, classpath);

        imagemp = Arrays.stream(modulePath).map(Paths::get).collect(Collectors.toUnmodifiableList());
        buildmp = Arrays.stream(System.getProperty("jdk.module.path", "").split(File.pathSeparator)).map(Paths::get).collect(Collectors.toUnmodifiableList());

        moduleController = createModuleController(imagemp.toArray(Path[]::new), classPathClassLoader);
        ModuleLayer moduleLayer = moduleController.layer();
        moduleFinder = moduleLayer::findModule;
        if (moduleLayer.modules().isEmpty()) {
            classLoader = classPathClassLoader;
        } else {
            classLoader = moduleLayer.modules().iterator().next().getClassLoader();
        }
    }

    private static ModuleLayer.Controller createModuleController(Path[] modulePaths, ClassLoader parent) {
        ModuleFinder finder = ModuleFinder.of(modulePaths);
        List<Configuration> parents = List.of(ModuleLayer.boot().configuration());
        Set<String> moduleNames = finder.findAll().stream().map(moduleReference -> moduleReference.descriptor().name()).collect(Collectors.toSet());
        Configuration configuration = Configuration.resolve(finder, parents, finder, moduleNames);
        return ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), parent);
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
    public Optional<Object> findModule(String moduleName) {
        return Optional.ofNullable(moduleFinder).flatMap(f -> f.apply(moduleName));
    }

    @Override
    Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
        if (module == null) {
            return Class.forName(className, false, classPathClassLoader);
        }
        if (!(module instanceof Module)) {
            throw new IllegalArgumentException("Argument `module` is not an instance of java.lang.Module");
        }
        Module m = (Module) module;
        if (m.getClassLoader() != classLoader) {
            throw new IllegalArgumentException("Argument `module` is java.lang.Module from different ClassLoader");
        }
        String moduleClassName = className;
        if (moduleClassName.isEmpty()) {
            moduleClassName = m.getDescriptor().mainClass().orElseThrow(
                            () -> UserError.abort("module %s does not have a ModuleMainClass attribute, use -m <module>/<main-class>", m.getName()));
        }
        Class<?> clazz = Class.forName(m, moduleClassName);
        if (clazz == null) {
            throw new ClassNotFoundException(moduleClassName);
        }
        return clazz;
    }

    @Override
    ClassLoader getClassLoader() {
        return classLoader;
    }

    private static class ClassInitWithModules extends ClassInit {

        ClassInitWithModules(ForkJoinPool executor, ImageClassLoader imageClassLoader, AbstractNativeImageClassLoaderSupport nativeImageClassLoader) {
            super(executor, imageClassLoader, nativeImageClassLoader);
        }

        @Override
        protected void init() {
            Set<String> modules = new HashSet<>();
            modules.add("jdk.internal.vm.ci");

            addOptionalModule(modules, "org.graalvm.sdk");
            addOptionalModule(modules, "jdk.internal.vm.compiler");
            addOptionalModule(modules, "com.oracle.graal.graal_enterprise");

            String includeModulesStr = System.getProperty(PROPERTY_IMAGEINCLUDEBUILTINMODULES);
            if (includeModulesStr != null) {
                modules.addAll(Arrays.asList(includeModulesStr.split(",")));
            }

            for (String moduleResource : ModuleSupport.getSystemModuleResources(modules)) {
                handleClassInModuleResource(moduleResource);
            }

            for (String moduleResource : ModuleSupport.getModuleResources(nativeImageClassLoader.modulepath())) {
                handleClassInModuleResource(moduleResource);
            }

            super.init();
        }

        private void handleClassInModuleResource(String moduleResource) {
            if (moduleResource.endsWith(CLASS_EXTENSION)) {
                executor.execute(() -> handleClassFileName(classFileWithoutSuffix(moduleResource), '/'));
            }
        }

        private static void addOptionalModule(Set<String> modules, String name) {
            if (ModuleSupport.hasSystemModule(name)) {
                modules.add(name);
            }
        }
    }

    @Override
    public void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new ClassInitWithModules(executor, imageClassLoader, this).init();
    }
}
