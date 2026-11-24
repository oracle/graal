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

import org.graalvm.nativeimage.ImageSingletons;

import jdk.vm.ci.meta.ResolvedJavaType;

/** Builder-context class-initialization policy using guest JVMCI type identity. */
public interface JVMCIRuntimeClassInitializationSupport {

    /** Returns the builder-context class-initialization policy. */
    static JVMCIRuntimeClassInitializationSupport singleton() {
        return ImageSingletons.lookup(JVMCIRuntimeClassInitializationSupport.class);
    }

    /** Registers a class or package name for run-time initialization. */
    default void initializeAtRunTime(String name) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        initializeAtRunTime(name, classReason(stacktrace, name));
    }

    /** Registers a class or package name for run-time initialization with an explicit reason. */
    void initializeAtRunTime(String name, String reason);

    /** Registers a guest type for run-time initialization. */
    default void initializeAtRunTime(ResolvedJavaType type) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        initializeAtRunTime(type, classReason(stacktrace, getUnqualifiedName(type) + ".class"));
    }

    /** Registers a guest type for run-time initialization with an explicit reason. */
    void initializeAtRunTime(ResolvedJavaType type, String reason);

    /** Registers a class or package name for build-time initialization. */
    default void initializeAtBuildTime(String name) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        initializeAtBuildTime(name, classReason(stacktrace, name));
    }

    /** Registers a class or package name for build-time initialization with an explicit reason. */
    void initializeAtBuildTime(String name, String reason);

    /** Registers a guest type for build-time initialization. */
    default void initializeAtBuildTime(ResolvedJavaType type) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        initializeAtBuildTime(type, classReason(stacktrace, getUnqualifiedName(type) + ".class"));
    }

    /** Registers a guest type for build-time initialization with an explicit reason. */
    void initializeAtBuildTime(ResolvedJavaType type, String reason);

    private static String classReason(StackTraceElement[] stacktrace, String simpleName) {
        StackTraceElement caller = stacktrace[2];
        return "from feature " + caller.getClassName() + "." + caller.getMethodName() + " with '" + simpleName + "'";
    }

    private static String getUnqualifiedName(ResolvedJavaType type) {
        String name = JVMCIReflectionUtil.getTypeName(type);
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
