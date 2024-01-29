/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.jdk.Resources.RESOURCES_INTERNAL_PATH_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

import jdk.internal.module.Modules;

public class ClassLoaderSupportImpl extends ClassLoaderSupport {

    private final NativeImageClassLoaderSupport classLoaderSupport;

    private final NativeImageClassLoader imageClassLoader;

    private final Map<String, Set<Module>> packageToModules;

    private record ConditionalResource(ConfigurationCondition condition, String resourceName) {
    }

    public ClassLoaderSupportImpl(NativeImageClassLoaderSupport classLoaderSupport) {
        this.classLoaderSupport = classLoaderSupport;
        imageClassLoader = classLoaderSupport.getClassLoader();
        packageToModules = new HashMap<>();
        buildPackageToModulesMap(classLoaderSupport);
    }

    @Override
    protected boolean isNativeImageClassLoaderImpl(ClassLoader loader) {
        if (loader == imageClassLoader) {
            return true;
        }
        if (loader instanceof NativeImageSystemClassLoader) {
            return true;
        }
        return false;
    }

    private record ResourceLookupInfo(ResolvedModule resolvedModule, Module module) {
    }

    private static Stream<ResourceLookupInfo> extractModuleLookupData(ModuleLayer layer) {
        List<ResourceLookupInfo> data = new ArrayList<>(layer.configuration().modules().size());
        for (ResolvedModule m : layer.configuration().modules()) {
            Module module = layer.findModule(m.name()).orElse(null);
            ResourceLookupInfo info = new ResourceLookupInfo(m, module);
            data.add(info);
        }
        return data.stream();
    }

    @Override
    public void collectResources(ResourceCollector resourceCollector) {
        /* Collect resources from modules */
        NativeImageClassLoaderSupport.allLayers(classLoaderSupport.moduleLayerForImageBuild).stream()
                        .flatMap(ClassLoaderSupportImpl::extractModuleLookupData)
                        .parallel()
                        .forEach(lookup -> collectResourceFromModule(resourceCollector, lookup));

        /* Collect remaining resources from classpath */
        classLoaderSupport.classpath().stream().parallel().forEach(classpathFile -> {
            boolean includeCurrent = classLoaderSupport.getJavaPathsToInclude().contains(classpathFile);
            try {
                if (Files.isDirectory(classpathFile)) {
                    scanDirectory(classpathFile, resourceCollector, includeCurrent);
                } else if (ClasspathUtils.isJar(classpathFile)) {
                    scanJar(classpathFile, resourceCollector, includeCurrent);
                }
            } catch (IOException ex) {
                throw UserError.abort("Unable to handle classpath element '%s'. Make sure that all classpath entries are either directories or valid jar files.", classpathFile);
            }
        });
    }

    private void collectResourceFromModule(ResourceCollector resourceCollector, ResourceLookupInfo info) {
        ModuleReference moduleReference = info.resolvedModule.reference();
        try (ModuleReader moduleReader = moduleReference.open()) {
            boolean includeCurrent = classLoaderSupport.getJavaModuleNamesToInclude().contains(info.resolvedModule().name());
            List<ConditionalResource> resourcesFound = new ArrayList<>();
            moduleReader.list().forEach(resourceName -> {
                List<ConfigurationCondition> conditions = shouldIncludeEntry(info.module, resourceCollector, resourceName, moduleReference.location().orElse(null), includeCurrent);
                for (ConfigurationCondition condition : conditions) {
                    resourcesFound.add(new ConditionalResource(condition, resourceName));
                }
            });

            for (ConditionalResource entry : resourcesFound) {
                ConfigurationCondition condition = entry.condition();
                String resName = entry.resourceName();
                if (resName.endsWith("/")) {
                    includeResource(resourceCollector, info.module, resName, condition);
                    continue;
                }

                Optional<InputStream> content = moduleReader.open(resName);
                if (content.isEmpty()) {
                    /* This is to be resilient, but the resources returned by list() should exist */
                    resourceCollector.registerNegativeQuery(info.module, resName);
                    continue;
                }

                includeResource(resourceCollector, info.module, resName, condition);
            }

        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static void scanDirectory(Path root, ResourceCollector collector, boolean includeCurrent) {
        ArrayDeque<Path> queue = new ArrayDeque<>();
        queue.push(root);
        while (!queue.isEmpty()) {
            Path entry = queue.pop();

            /* Resources always use / as the separator, as do our resource inclusion patterns */
            String relativeFilePath;
            if (entry != root) {
                relativeFilePath = root.relativize(entry).toString().replace(File.separatorChar, RESOURCES_INTERNAL_PATH_SEPARATOR);
            } else {
                relativeFilePath = String.valueOf(RESOURCES_INTERNAL_PATH_SEPARATOR);
            }

            List<ConfigurationCondition> conditions = shouldIncludeEntry(null, collector, relativeFilePath, Path.of(relativeFilePath).toUri(), includeCurrent);
            for (ConfigurationCondition condition : conditions) {
                includeResource(collector, null, relativeFilePath, condition);
            }

            if (Files.isDirectory(entry)) {
                if (conditions.isEmpty()) {
                    collector.registerNegativeQuery(null, relativeFilePath);
                }

                try (Stream<Path> pathStream = Files.list(entry)) {
                    Stream<Path> filtered = pathStream;
                    if (ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES_ROOT.equals(entry)) {
                        filtered = filtered.filter(Predicate.not(ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES::contains));
                    }
                    filtered.forEach(queue::push);
                } catch (IOException resourceException) {
                    collector.registerIOException(null, relativeFilePath, resourceException, LinkAtBuildTimeSupport.singleton().packageOrClassAtBuildTime(relativeFilePath));
                }
            }
        }
    }

    private static void scanJar(Path jarPath, ResourceCollector collector, boolean includeCurrent) throws IOException {
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory()) {
                    entryName = entryName.substring(0, entry.getName().length() - 1);
                }

                List<ConfigurationCondition> conditions = shouldIncludeEntry(null, collector, entryName, jarPath.toUri(), includeCurrent);
                for (ConfigurationCondition condition : conditions) {
                    includeResource(collector, null, entryName, condition);
                }
            }
        }
    }

    private static void includeResource(ResourceCollector collector, Module module, String name, ConfigurationCondition condition) {
        if (condition.isAlwaysTrue()) {
            collector.addResource(module, name);
        } else {
            collector.addResourceConditionally(module, name, condition);
        }
    }

    private static List<ConfigurationCondition> shouldIncludeEntry(Module module, ResourceCollector collector, String fileName, URI uri, boolean includeCurrent) {
        if (includeCurrent && !(fileName.endsWith(".class") || fileName.endsWith(".jar"))) {
            return Collections.singletonList(ConfigurationCondition.alwaysTrue());
        }

        return collector.isIncluded(module, fileName, uri);
    }

    @Override
    public List<ResourceBundle> getResourceBundle(String bundleSpec, Locale locale) {
        String[] specParts = bundleSpec.split(":", 2);
        String moduleName;
        String bundleName;
        if (specParts.length > 1) {
            moduleName = specParts[0];
            bundleName = specParts[1];
        } else {
            moduleName = null;
            bundleName = specParts[0];
        }
        bundleName = bundleName.replace("/", ".");
        String packageName = packageName(bundleName);
        Set<Module> modules;
        if (ResourcesFeature.MODULE_NAME_ALL_UNNAMED.equals(moduleName)) {
            modules = Collections.emptySet();
        } else if (moduleName != null) {
            modules = classLoaderSupport.findModule(moduleName).stream().collect(Collectors.toSet());
        } else {
            modules = packageToModules.getOrDefault(packageName, Collections.emptySet());
        }
        if (modules.isEmpty()) {
            /* If bundle is not located in any module get it via classloader (from ALL_UNNAMED) */
            return Collections.singletonList(ResourceBundle.getBundle(bundleName, locale, imageClassLoader));
        }
        ArrayList<ResourceBundle> resourceBundles = new ArrayList<>();
        Module builderModule = ClassLoaderSupportImpl.class.getModule();
        for (Module module : modules) {
            if (builderModule.isNamed()) {
                Modules.addOpens(module, packageName, builderModule);
            } else {
                Modules.addOpensToAllUnnamed(module, packageName);
            }
            resourceBundles.add(ResourceBundle.getBundle(bundleName, locale, module));
        }
        return resourceBundles;
    }

    @Override
    public Map<String, Set<Module>> getPackageToModules() {
        return packageToModules;
    }

    private static String packageName(String bundleName) {
        int classSep = bundleName.lastIndexOf('.');
        if (classSep == -1) {
            return ""; /* unnamed package */
        }
        return bundleName.substring(0, classSep);
    }

    private void buildPackageToModulesMap(NativeImageClassLoaderSupport cls) {
        for (ModuleLayer layer : NativeImageClassLoaderSupport.allLayers(cls.moduleLayerForImageBuild)) {
            for (Module module : layer.modules()) {
                for (String packageName : module.getDescriptor().packages()) {
                    addToPackageNameModules(module, packageName);
                }
            }
        }
    }

    private void addToPackageNameModules(Module moduleName, String packageName) {
        Set<Module> prevValue = packageToModules.get(packageName);
        if (prevValue == null) {
            /* Mostly packageName is only used in a single module */
            packageToModules.put(packageName, Collections.singleton(moduleName));
        } else if (prevValue.size() == 1) {
            /* Transition to HashSet - happens rarely */
            HashSet<Module> newValue = new HashSet<>();
            newValue.add(prevValue.iterator().next());
            newValue.add(moduleName);
            packageToModules.put(packageName, newValue);
        } else if (prevValue.size() > 1) {
            /* Add to exiting HashSet - happens rarely */
            prevValue.add(moduleName);
        }
    }
}
