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

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalConstantReflectionProvider.safeGetClass;

import java.util.Set;

import org.graalvm.polyglot.Value;

import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

final class EspressoExternalResolvedJavaModule implements ResolvedJavaModule {
    private final EspressoExternalVMAccess access;
    private final String name;
    private final Value value;

    EspressoExternalResolvedJavaModule(EspressoExternalVMAccess access, Value value) {
        // j.l.Module?
        if (!"java.lang.Module".equals(value.getMetaObject().getMetaQualifiedName())) {
            throw new IllegalArgumentException("Constant has unexpected type " + value.getMetaObject().getMetaQualifiedName() + ": " + value);
        }
        Providers providers = access.getProviders();

        this.access = access;
        this.value = value;

        Signature getNameSignature = providers.getMetaAccess().parseMethodDescriptor("()Ljava/lang/String;");
        ResolvedJavaMethod getName = access.javaLangModule.findMethod("getName", getNameSignature);
        if (!(getName instanceof EspressoExternalResolvedJavaMethod espressoMethod)) {
            throw new IllegalArgumentException("Expected an EspressoExternalResolvedJavaMethod, got " + safeGetClass(getName));
        }
        Value nameValue = espressoMethod.getMirror().execute(value);
        this.name = nameValue.asString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isOpen(String pn) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isOpen(String packageName, ResolvedJavaModule accessingModule) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isExported(String packageName) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isExported(String packageName, ResolvedJavaModule accessingModule) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public Set<String> getPackages() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isNamed() {
        return name != null;
    }

    @Override
    public boolean isAutomatic() {
        throw JVMCIError.unimplemented();
    }
}
