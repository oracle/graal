/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.serviceprovider;

import java.lang.reflect.Method;
import java.util.List;

import jdk.graal.compiler.core.common.BootstrapMethodIntrospection;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BootstrapMethodIntrospectionImpl implements BootstrapMethodIntrospection {
    private final Object wrapped;

    /**
     * The interface jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation was introduced in JVMCI
     * 22.1.
     */
    private static final Class<?> bsmClass;
    private static final Method bsmGetMethod;
    private static final Method bsmIsInvokeDynamic;
    private static final Method bsmGetName;
    private static final Method bsmGetType;
    private static final Method bsmGetStaticArguments;

    static {
        Class<?> bootstrapMethodClass = null;
        try {
            bootstrapMethodClass = Class.forName("jdk.vm.ci.meta.ConstantPool$BootstrapMethodInvocation");
        } catch (ClassNotFoundException e) {
        }
        bsmClass = bootstrapMethodClass;

        Method bootstrapMethodGetMethod = null;
        Method bootstrapMethodIsInvokeDynamic = null;
        Method bootstrapMethodGetName = null;
        Method bootstrapMethodGetType = null;
        Method bootstrapMethodGetStaticArguments = null;

        try {
            bootstrapMethodGetMethod = bsmClass == null ? null : bsmClass.getMethod("getMethod");
        } catch (NoSuchMethodException e) {
        }

        try {
            bootstrapMethodIsInvokeDynamic = bsmClass == null ? null : bsmClass.getMethod("isInvokeDynamic");
        } catch (NoSuchMethodException e) {
        }

        try {
            bootstrapMethodGetName = bsmClass == null ? null : bsmClass.getMethod("getName");
        } catch (NoSuchMethodException e) {
        }

        try {
            bootstrapMethodGetType = bsmClass == null ? null : bsmClass.getMethod("getType");
        } catch (NoSuchMethodException e) {
        }

        try {
            bootstrapMethodGetStaticArguments = bsmClass == null ? null : bsmClass.getMethod("getStaticArguments");
        } catch (NoSuchMethodException e) {
        }

        bsmGetMethod = bootstrapMethodGetMethod;
        bsmIsInvokeDynamic = bootstrapMethodIsInvokeDynamic;
        bsmGetName = bootstrapMethodGetName;
        bsmGetType = bootstrapMethodGetType;
        bsmGetStaticArguments = bootstrapMethodGetStaticArguments;

    }

    public BootstrapMethodIntrospectionImpl(Object wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        try {
            return (ResolvedJavaMethod) bsmGetMethod.invoke(wrapped);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public boolean isInvokeDynamic() {
        try {
            return (boolean) bsmIsInvokeDynamic.invoke(wrapped);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public String getName() {
        try {
            return (String) bsmGetName.invoke(wrapped);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public JavaConstant getType() {
        try {
            return (JavaConstant) bsmGetType.invoke(wrapped);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<JavaConstant> getStaticArguments() {
        try {
            return (List<JavaConstant>) bsmGetStaticArguments.invoke(wrapped);
        } catch (Throwable t) {
            throw GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
        }
    }
}
