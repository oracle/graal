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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.SubstrateUtil;
import org.graalvm.nativeimage.ImageSingletons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BootModuleLayerSupport {

    public static BootModuleLayerSupport instance() {
        return ImageSingletons.lookup(BootModuleLayerSupport.class);
    }

    private final ModuleLayer bootLayer;
    private final Set<Module> reachableModules;
    private final Map<String, Module> nameToModule;
    private boolean isAnalysisComplete;

    public BootModuleLayerSupport() {
        bootLayer = ModuleLayer.boot();
        reachableModules = new HashSet<>();
        nameToModule = new HashMap<>();
    }

    public void setReachableModules(Set<Object> modules) {
        isAnalysisComplete = true;
        reachableModules.addAll(modules
                .stream()
                .map(o -> (Module) o)
                .collect(Collectors.toSet()));
        nameToModule.putAll(reachableModules.stream().collect(Collectors.toMap(Module::getName, m -> m)));
    }

    public Object getBootLayer() {
        Target_java_lang_ModuleLayer originalLayer = SubstrateUtil.cast(bootLayer, Target_java_lang_ModuleLayer.class);
        if (isAnalysisComplete) {
            originalLayer.nameToModule = nameToModule;
            originalLayer.modules = reachableModules;
        }
        return originalLayer;
    }

}
