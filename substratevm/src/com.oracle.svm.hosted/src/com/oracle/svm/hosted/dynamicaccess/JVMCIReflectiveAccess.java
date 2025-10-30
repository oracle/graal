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
package com.oracle.svm.hosted.dynamicaccess;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.dynamicaccess.ReflectiveAccess;

import com.oracle.svm.hosted.ReflectiveAccessImpl;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Mirror of {@link ReflectiveAccess} using JVMCI types.
 */
public final class JVMCIReflectiveAccess {
    private final ReflectiveAccessImpl rdaInstance;
    private static JVMCIReflectiveAccess instance;

    private JVMCIReflectiveAccess() {
        rdaInstance = ReflectiveAccessImpl.singleton();
    }

    public static JVMCIReflectiveAccess singleton() {
        if (instance == null) {
            instance = new JVMCIReflectiveAccess();
        }
        return instance;
    }

    /**
     * @see ReflectiveAccess#register(AccessCondition, Class...)
     */
    public void register(AccessCondition condition, ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            rdaInstance.register(condition, OriginalClassProvider.getJavaClass(type));
        }
    }

    /**
     * @see ReflectiveAccess#register(AccessCondition, Executable...)
     */
    public void register(AccessCondition condition, ResolvedJavaMethod... methods) {
        for (ResolvedJavaMethod method : methods) {
            rdaInstance.register(condition, OriginalMethodProvider.getJavaMethod(method));
        }
    }

    /**
     * @see ReflectiveAccess#register(AccessCondition, Field...)
     */
    public void register(AccessCondition condition, ResolvedJavaField... fields) {
        for (ResolvedJavaField field : fields) {
            rdaInstance.register(condition, OriginalFieldProvider.getJavaField(field));
        }
    }

    /**
     * @see ReflectiveAccess#registerForSerialization(AccessCondition, Class...)
     */
    public void registerForSerialization(AccessCondition condition, ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            rdaInstance.registerForSerialization(condition, OriginalClassProvider.getJavaClass(type));
        }
    }

    /**
     * @see ReflectiveAccess#registerProxy(AccessCondition, Class...)
     */
    public Class<?> registerProxy(AccessCondition condition, ResolvedJavaType... interfaces) {
        List<Class<?>> reflectionInterfaces = new ArrayList<>();
        for (ResolvedJavaType intf : interfaces) {
            reflectionInterfaces.add(OriginalClassProvider.getJavaClass(intf));
        }
        return rdaInstance.registerProxy(condition, reflectionInterfaces.toArray(Class[]::new));
    }

    /**
     * @see ReflectiveAccess#registerForUnsafeAllocation(AccessCondition, Class...)
     */
    public void registerForUnsafeAllocation(AccessCondition condition, ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            rdaInstance.registerForUnsafeAllocation(condition, OriginalClassProvider.getJavaClass(type));
        }
    }
}
