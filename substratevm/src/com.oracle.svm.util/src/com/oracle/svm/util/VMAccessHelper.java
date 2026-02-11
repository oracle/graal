/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Helpers for using a {@link VMAccess} to work with guest context values, fields and methods.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class VMAccessHelper {

    private final VMAccess vmAccess;
    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final SnippetReflectionProvider snippetReflection;

    public VMAccessHelper(VMAccess vmAccess) {
        Providers providers = vmAccess.getProviders();
        this.vmAccess = vmAccess;
        this.metaAccess = providers.getMetaAccess();
        this.constantReflection = providers.getConstantReflection();
        this.snippetReflection = providers.getSnippetReflection();
    }

    /**
     * Gets the {@link VMAccess} underlying this helper.
     */
    public VMAccess getVMAccess() {
        return vmAccess;
    }

    /**
     * Instantiates an instance of {@code supplierType} in the guest and invokes
     * {@link BooleanSupplier#getAsBoolean()} on it.
     *
     * @param supplierType a concrete {@link java.util.function.BooleanSupplier} type
     */
    public boolean callBooleanSupplier(ResolvedJavaType supplierType) {
        ResolvedJavaMethod cons = JVMCIReflectionUtil.getDeclaredConstructor(false, supplierType);
        JavaConstant supplier = vmAccess.invoke(cons, null);
        ResolvedJavaMethod getAsBoolean = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, BooleanSupplier.class, "getAsBoolean");
        return vmAccess.invoke(getAsBoolean, supplier).asBoolean();
    }

    /**
     * Instantiates an instance of {@code functionType} in the guest and invokes
     * {@link Function#apply(Object)} on it.
     *
     * @param functionType a concrete {@link java.util.function.BooleanSupplier} type
     * @param arg the single function argument for {@code apply}
     */
    public JavaConstant callFunction(ResolvedJavaType functionType, JavaConstant arg) {
        ResolvedJavaMethod cons = JVMCIReflectionUtil.getDeclaredConstructor(false, functionType);
        JavaConstant function = vmAccess.invoke(cons, null);
        ResolvedJavaMethod apply = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, Function.class, "apply", Object.class);
        return vmAccess.invoke(apply, function, arg);
    }

    /**
     * Shortcut for {@code getVMAccess().lookupAppClassLoaderType(name)}.
     */
    public ResolvedJavaType lookupType(String name) {
        return vmAccess.lookupAppClassLoaderType(name);
    }

    /**
     * Looks up a method in the guest.
     *
     * @param declaringType the class declaring the method
     * @param name name of the method
     * @param parameterTypes types of the method's parameters
     */
    public ResolvedJavaMethod lookupMethod(ResolvedJavaType declaringType, String name, Class<?>... parameterTypes) {
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(false, metaAccess, declaringType, name, parameterTypes);
    }

    /**
     * Converts the host string {@code value} to a guest instance and returns a reference to it as a
     * {@link JavaConstant}.
     */
    public JavaConstant asGuestString(String value) {
        return constantReflection.forString(value);
    }

    /**
     * Converts the guest {@code val} to a host {@code type} object instance.
     *
     * @return {@code null} if {@code val.isNull()} otherwise a non-null {@code type} instance
     * @throws IllegalArgumentException if conversion is not supported for {@code type}
     */
    public <T> T asHostObject(Class<T> type, JavaConstant val) {
        if (val.isNull()) {
            return null;
        }
        T res = snippetReflection.asObject(type, val);
        if (res == null) {
            throw new IllegalArgumentException("Cannot convert guest constant to a %s: %s".formatted(type.getName(), val));
        }
        return res;
    }

    /**
     * Short-cut for {@code asHostObject(String.class, val)}.
     */
    public String asHostString(JavaConstant val) {
        return asHostObject(String.class, val);
    }

    public JavaConstant invokeStatic(ResolvedJavaMethod method, JavaConstant... args) {
        assert method.isStatic() : method;
        return vmAccess.invoke(method, null, args);
    }

    public JavaConstant invokeVirtual(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... args) {
        assert !method.isStatic() : method;
        return vmAccess.invoke(method, receiver, args);
    }
}
