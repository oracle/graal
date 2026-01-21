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

import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;

import jdk.graal.compiler.vmaccess.ResolvedJavaModule;

final class EspressoExternalResolvedJavaModule implements ResolvedJavaModule {
    private final EspressoExternalVMAccess access;
    private final String name;
    final Value moduleValue;

    EspressoExternalResolvedJavaModule(EspressoExternalVMAccess access, Value moduleValue) {
        // j.l.Module?
        if (!"java.lang.Module".equals(moduleValue.getMetaObject().getMetaQualifiedName())) {
            throw new IllegalArgumentException("Constant has unexpected type " + moduleValue.getMetaObject().getMetaQualifiedName() + ": " + moduleValue);
        }
        this.access = access;
        this.moduleValue = moduleValue;
        this.name = access.java_lang_Module_getName.getMirror().execute(moduleValue).asString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isOpen(String packageName) {
        Value packageNameValue = access.invokeJVMCIHelper("toGuestString", packageName);
        return access.java_lang_Module_isOpen_String.getMirror().execute(moduleValue, packageNameValue).asBoolean();
    }

    @Override
    public boolean isOpen(String packageName, ResolvedJavaModule accessingModule) {
        if (!(accessingModule instanceof EspressoExternalResolvedJavaModule espressoModule)) {
            throw new IllegalArgumentException("Expected an EspressoExternalResolvedJavaModule, got " + safeGetClass(accessingModule));
        }
        Value packageNameValue = access.invokeJVMCIHelper("toGuestString", packageName);
        return access.java_lang_Module_isOpen_String_Module.getMirror().execute(moduleValue, packageNameValue, espressoModule.moduleValue).asBoolean();
    }

    @Override
    public boolean isExported(String packageName) {
        Value packageNameValue = access.invokeJVMCIHelper("toGuestString", packageName);
        return access.java_lang_Module_isExported_String.getMirror().execute(moduleValue, packageNameValue).asBoolean();
    }

    @Override
    public boolean isExported(String packageName, ResolvedJavaModule accessingModule) {
        if (!(accessingModule instanceof EspressoExternalResolvedJavaModule espressoModule)) {
            throw new IllegalArgumentException("Expected an EspressoExternalResolvedJavaModule, got " + safeGetClass(accessingModule));
        }
        Value packageNameValue = access.invokeJVMCIHelper("toGuestString", packageName);
        return access.java_lang_Module_isExported_String_Module.getMirror().execute(moduleValue, packageNameValue, espressoModule.moduleValue).asBoolean();
    }

    @Override
    public Set<String> getPackages() {
        Value packages = access.java_lang_Module_getPackages.getMirror().execute(moduleValue);
        return packages.as(new TypeLiteral<>() {
        });
    }

    @Override
    public boolean isNamed() {
        return name != null;
    }

    @Override
    public boolean isAutomatic() {
        if (!isNamed()) {
            throw new IllegalArgumentException("Must not call isAutomatic() on an unnamed module");
        }
        Value moduleDescriptor = access.java_lang_Module_getDescriptor.getMirror().execute(moduleValue);
        Value isAutomatic = access.java_lang_module_ModuleDescriptor_isAutomatic.getMirror().execute(moduleDescriptor);
        return isAutomatic.asBoolean();
    }
}
