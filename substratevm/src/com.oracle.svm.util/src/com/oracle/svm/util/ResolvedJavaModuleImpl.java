/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.lang.module.ModuleDescriptor;
import java.util.Objects;
import java.util.Set;

/**
 * Fallback implementation of {@link ResolvedJavaModule} based on {@link Module}.
 */
final class ResolvedJavaModuleImpl implements ResolvedJavaModule {

    private final Module module;

    ResolvedJavaModuleImpl(Module module) {
        this.module = Objects.requireNonNull(module);
    }

    @Override
    public String getName() {
        return module.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResolvedJavaModuleImpl that = (ResolvedJavaModuleImpl) o;
        return module.equals(that.module);
    }

    @Override
    public int hashCode() {
        return module.hashCode();
    }

    @Override
    public boolean isOpen(String pn) {
        return module.isOpen(pn);
    }

    @Override
    public boolean isOpen(String packageName, ResolvedJavaModule accessingModule) {
        return module.isOpen(packageName, toImpl(accessingModule).module);
    }

    @Override
    public boolean isExported(String packageName) {
        return module.isExported(packageName);
    }

    @Override
    public boolean isExported(String packageName, ResolvedJavaModule accessingModule) {
        return module.isExported(packageName, toImpl(accessingModule).module);
    }

    @Override
    public Set<String> getPackages() {
        return module.getPackages();
    }

    @Override
    public boolean isNamed() {
        return module.isNamed();
    }

    @Override
    public ModuleDescriptor getDescriptor() {
        return module.getDescriptor();
    }

    private static ResolvedJavaModuleImpl toImpl(ResolvedJavaModule module) {
        if (module instanceof ResolvedJavaModuleImpl moduleImpl) {
            return moduleImpl;
        }
        throw new IllegalArgumentException("Unsupported ResolvedJavaModule implementation: " + module.getClass().getName());
    }

    static void addReads(Module accessingModule, ResolvedJavaModule declaringModule) {
        ModuleSupport.accessModule(ModuleSupport.Access.OPEN, accessingModule, toImpl(declaringModule).module);
    }

    static Module getJavaModule(ResolvedJavaModule m) {
        return toImpl(m).module;
    }
}
