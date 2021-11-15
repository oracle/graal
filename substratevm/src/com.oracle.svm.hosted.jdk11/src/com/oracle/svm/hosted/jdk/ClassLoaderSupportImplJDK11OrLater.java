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

package com.oracle.svm.hosted.jdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;

import jdk.internal.module.Modules;

public final class ClassLoaderSupportImplJDK11OrLater extends ClassLoaderSupport {

    private final NativeImageClassLoaderSupportJDK11OrLater classLoaderSupport;
    private final Map<String, Set<Module>> packageToModules;

    ClassLoaderSupportImplJDK11OrLater(NativeImageClassLoaderSupportJDK11OrLater classLoaderSupport) {
        this.classLoaderSupport = classLoaderSupport;
        packageToModules = new HashMap<>();
        buildPackageToModulesMap(classLoaderSupport);
    }

    @Override
    protected boolean isNativeImageClassLoaderImpl(ClassLoader loader) {
        return loader == classLoaderSupport.getClassLoader() || loader instanceof NativeImageSystemClassLoader;
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
        String packageName = packageName(bundleName);
        Set<Module> modules;
        if (moduleName != null) {
            modules = classLoaderSupport.findModule(moduleName).stream().collect(Collectors.toSet());
        } else {
            modules = packageToModules.getOrDefault(packageName, Collections.emptySet());
        }
        if (modules.isEmpty()) {
            /* If bundle is not located in any module get it via classloader (from ALL_UNNAMED) */
            return Collections.singletonList(ResourceBundle.getBundle(bundleName, locale, classLoaderSupport.getClassLoader()));
        }
        ArrayList<ResourceBundle> resourceBundles = new ArrayList<>();
        for (Module module : modules) {
            Module exportTargetModule = ClassLoaderSupportImplJDK11OrLater.class.getModule();
            if (!module.isOpen(packageName, exportTargetModule)) {
                Modules.addOpens(module, packageName, exportTargetModule);
            }
            resourceBundles.add(ResourceBundle.getBundle(bundleName, locale, module));
        }
        return resourceBundles;
    }

    private static String packageName(String bundleName) {
        int classSep = bundleName.replace('/', '.').lastIndexOf('.');
        if (classSep == -1) {
            return ""; /* unnamed package */
        }
        return bundleName.substring(0, classSep);
    }

    private void buildPackageToModulesMap(NativeImageClassLoaderSupportJDK11OrLater cls) {
        for (ModuleLayer layer : NativeImageClassLoaderSupportJDK11OrLater.allLayers(cls.moduleLayerForImageBuild)) {
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

@AutomaticFeature
class ClassLoaderSupportFeatureJDK11OrLater implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        ImageSingletons.add(ClassLoaderSupport.class, new ClassLoaderSupportImplJDK11OrLater((NativeImageClassLoaderSupportJDK11OrLater) access.getImageClassLoader().classLoaderSupport));
    }
}
