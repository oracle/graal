/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.vmaccess;

import java.util.Objects;
import java.util.Optional;

import org.graalvm.polyglot.Value;

import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.vm.ci.common.JVMCIError;

/**
 * Espresso-backed implementation of {@link ResolvedJavaModuleLayer}.
 */
final class EspressoExternalResolvedJavaModuleLayer implements ResolvedJavaModuleLayer {
    private final EspressoExternalVMAccess access;
    private final Value moduleLayerValue;

    EspressoExternalResolvedJavaModuleLayer(EspressoExternalVMAccess access, Value moduleLayerValue) {
        this.access = Objects.requireNonNull(access, "access");
        this.moduleLayerValue = Objects.requireNonNull(moduleLayerValue, "moduleLayerValue");
        // Validate we got a java.lang.ModuleLayer guest object
        String metaName = moduleLayerValue.getMetaObject().getMetaQualifiedName();
        JVMCIError.guarantee("java.lang.ModuleLayer".equals(metaName),
                        "Constant has unexpected type %s: %s", metaName, moduleLayerValue);
    }

    @Override
    public Optional<ResolvedJavaModule> findModule(String moduleName) {
        // java.lang.ModuleLayer#findModule(String) returns Optional<Module>
        Value optional = moduleLayerValue.invokeMember("findModule", moduleName);
        boolean present = optional.invokeMember("isPresent").asBoolean();
        if (!present) {
            return Optional.empty();
        }
        Value module = optional.invokeMember("get");
        return Optional.of(new EspressoExternalResolvedJavaModule(access, module));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoExternalResolvedJavaModuleLayer that = (EspressoExternalResolvedJavaModuleLayer) o;
        return moduleLayerValue.equals(that.moduleLayerValue);
    }

    @Override
    public int hashCode() {
        return moduleLayerValue.hashCode();
    }
}
