/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import org.graalvm.polyglot.Value;

import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Espresso-backed implementation of {@link ResolvedJavaPackage}.
 */
final class EspressoExternalResolvedJavaPackage implements ResolvedJavaPackage {
    private final EspressoExternalVMAccess access;
    private final Value packageValue;
    private final String name;

    EspressoExternalResolvedJavaPackage(EspressoExternalVMAccess access, Value packageValue) {
        this.access = Objects.requireNonNull(access, "access");
        this.packageValue = Objects.requireNonNull(packageValue, "packageValue");

        String metaName = packageValue.getMetaObject().getMetaQualifiedName();
        this.name = packageValue.invokeMember("getName").asString();
        // Validate we got a java.lang.Package guest object
        JVMCIError.guarantee("java.lang.Package".equals(metaName), "Constant has unexpected type %s: %s", metaName, packageValue);
    }

    @Override
    public String getImplementationVersion() {
        Value ver = packageValue.invokeMember("getImplementationVersion");
        return ver.isNull() ? null : ver.asString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public EspressoExternalResolvedJavaModule module() {
        Value moduleValue = access.java_lang_NamedPackage_module.readValue(packageValue);
        return new EspressoExternalResolvedJavaModule(access, moduleValue);
    }

    @Override
    public <T> T getDeclaredAnnotationInfo(Function<AnnotationsInfo, T> parser) {
        EspressoExternalResolvedInstanceType packageInfo = getAnnotatedPackageInfo();
        if (packageInfo != null) {
            return packageInfo.getDeclaredAnnotationInfo(parser);
        }
        return parser.apply(null);
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        EspressoExternalResolvedInstanceType packageInfo = getAnnotatedPackageInfo();
        if (packageInfo != null) {
            return packageInfo.getTypeAnnotationInfo();
        }
        return null;
    }

    private EspressoExternalResolvedInstanceType getAnnotatedPackageInfo() {
        var packageInfoConstant = ((EspressoExternalObjectConstant) access.invoke(access.java_lang_Package_getPackageInfo,
                        EspressoExternalConstantReflectionProvider.asObjectConstant(packageValue, access))).getValue();
        if (packageInfoConstant.isNull()) {
            return null;
        }
        return (EspressoExternalResolvedInstanceType) access.getProviders().getConstantReflection().asJavaType(
                        EspressoExternalConstantReflectionProvider.asObjectConstant(packageInfoConstant, access));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoExternalResolvedJavaPackage that = (EspressoExternalResolvedJavaPackage) o;
        return packageValue.equals(that.packageValue);
    }

    @Override
    public int hashCode() {
        return packageValue.hashCode();
    }
}
