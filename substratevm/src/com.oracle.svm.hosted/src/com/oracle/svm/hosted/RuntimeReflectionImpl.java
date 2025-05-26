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
package com.oracle.svm.hosted;

import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

public final class RuntimeReflectionImpl implements RuntimeReflection {

    private final InternalRuntimeReflection rdaInstance;

    public RuntimeReflectionImpl() {
        rdaInstance = new InternalRuntimeReflection();
    }

    @Override
    public void register(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for reflection: " + Arrays.toString(classes));
        rdaInstance.register(condition, classes);
    }

    @Override
    public void registerClassLookup(RegistrationCondition condition, String className) {
        DynamicAccessSupport.printUserError("following classes for lookup: " + className);
        rdaInstance.registerClassLookup(condition, className);
    }

    @Override
    public void registerUnsafeAllocation(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for reflection and unsafe allocation: " + Arrays.toString(classes));
        rdaInstance.registerUnsafeAllocation(condition, classes);
    }

    @Override
    public void register(RegistrationCondition condition, Executable... methods) {
        DynamicAccessSupport.printUserError("following methods for reflection: " + Arrays.toString(methods));
        rdaInstance.register(condition, methods);
    }

    @Override
    public void register(RegistrationCondition condition, Field... fields) {
        DynamicAccessSupport.printUserError("following fields for reflection: " + Arrays.toString(fields));
        rdaInstance.register(condition, fields);
    }

    @Override
    public void registerForSerialization(RegistrationCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printUserError("following classes for serialization: " + Arrays.toString(classes));
        rdaInstance.registerForSerialization(condition, classes);
    }

    @Override
    public Class<?> registerProxy(RegistrationCondition condition, Class<?>... interfaces) {
        DynamicAccessSupport.printUserError("following interfaces that define a dynamic proxy class: " + Arrays.toString(interfaces));
        return rdaInstance.registerProxy(condition, interfaces);
    }
}
