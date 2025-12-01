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

import java.util.Objects;
import java.util.Optional;

import com.oracle.graal.vmaccess.ResolvedJavaModule;
import com.oracle.graal.vmaccess.ResolvedJavaModuleLayer;

/**
 * Fallback implementation of {@link ResolvedJavaModuleLayer} based on {@link ModuleLayer}.
 */
final class ResolvedJavaModuleLayerImpl implements ResolvedJavaModuleLayer {

    private final ModuleLayer moduleLayer;

    ResolvedJavaModuleLayerImpl(ModuleLayer moduleLayer) {
        this.moduleLayer = Objects.requireNonNull(moduleLayer);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResolvedJavaModuleLayerImpl that = (ResolvedJavaModuleLayerImpl) o;
        return moduleLayer.equals(that.moduleLayer);
    }

    @Override
    public int hashCode() {
        return moduleLayer.hashCode();
    }

    @Override
    public Optional<ResolvedJavaModule> findModule(String moduleName) {
        return moduleLayer.findModule(moduleName).map(ResolvedJavaModuleImpl::new);
    }
}
