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
package com.oracle.svm.configure.filters;

import java.lang.module.ModuleDescriptor;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.configure.filters.RuleNode.Inclusion;

public class ModuleFilterTools {

    public static RuleNode generateFromModules(String[] moduleNames, Inclusion inclusion, boolean reduce) {
        Set<String> includedModuleNameSet = new HashSet<>();
        Collections.addAll(includedModuleNameSet, moduleNames);
        for (Module module : ModuleLayer.boot().modules()) {
            if (includedModuleNameSet.contains(module.getName())) {
                checkDependencies(module, includedModuleNameSet);
            }
        }
        RuleNode rootNode = RuleNode.createRoot();
        if (inclusion == Inclusion.Exclude) {
            rootNode.addOrGetChildren("**", Inclusion.Include);
        }
        for (Module module : ModuleLayer.boot().modules()) {
            String moduleName = module.getName();
            for (String qualifiedPkg : module.getPackages()) {
                rootNode.addOrGetChildren(qualifiedPkg + ".*", inclusion);
            }
        }
        if (reduce) {
            rootNode.reduceExhaustiveTree();
        }
        return rootNode;
    }

    private static void checkDependencies(Module module, Set<String> includedModuleNames) {
        for (ModuleDescriptor.Requires require : module.getDescriptor().requires()) {
            if (!includedModuleNames.contains(require.name())) {
                System.err.println("Warning: dependency missing from input set of modules: " + module.getName() + " -> " + require.name());
                checkDependencies(module.getLayer().findModule(require.name()).get(), includedModuleNames);
            }
        }
    }
}
