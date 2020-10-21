/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.hub.DynamicHub;

class ModuleAccess {
    public static void extractAndSetModule(DynamicHub hub, Class<?> clazz) {
        final Module module = clazz.getModule();
        hub.setModule(module);
    }

    /**
     * @param access currently used to access the application module path
     * @return map of all services and their providers registered using modules
     */
    public static Map<String, List<String>> lookupServiceProviders(Feature.BeforeAnalysisAccess access) {
        return loadAllModules(access)
                        .flatMap(module -> module.provides().stream())
                        .collect(Collectors.toMap(ModuleDescriptor.Provides::service, ModuleDescriptor.Provides::providers, (left, right) -> {
                            ArrayList<String> list = new ArrayList<>(left);
                            list.addAll(right);
                            return list;
                        }));
    }

    /**
     * Combines boot layer modules and application modules.
     * 
     * @return stream of all module descriptors that were discovered
     */
    private static Stream<ModuleDescriptor> loadAllModules(Feature.BeforeAnalysisAccess access) {
        List<Path> applicationModulePath = access.getApplicationModulePath();
        ModuleFinder finder = ModuleFinder.of(applicationModulePath.toArray(new Path[0]));
        Stream<ModuleDescriptor> applicationModules = finder.findAll().stream()
                        .map(ModuleReference::descriptor);
        Stream<ModuleDescriptor> bootLayerModules = ModuleLayer.boot()
                        .modules()
                        .stream()
                        .map(Module::getDescriptor);
        return Stream.concat(bootLayerModules, applicationModules)
                        .filter(Objects::nonNull);
    }
}
