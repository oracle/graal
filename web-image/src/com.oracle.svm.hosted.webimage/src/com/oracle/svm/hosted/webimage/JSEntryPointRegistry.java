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
package com.oracle.svm.hosted.webimage;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.ReflectionRegistry;

/**
 * Collects entry points specified in entry point configuration files.
 *
 * It targets primarily the use case where a Java method is called from the JavaScript world. This
 * is different from reflection configuration because no reflection support is needed and the
 * underlying mechanism is different.
 *
 * The entry point configuration file is a subset of the reflect configuration in SVM. In
 * particular, conditional configuration is not supported and query specification is ignored.
 */
public class JSEntryPointRegistry implements ReflectionRegistry {
    public final Set<Executable> entryPoints = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void register(ConfigurationCondition condition, boolean unsafeAllocated, Class<?> clazz) {
        // Do nothing for types
    }

    @Override
    public void register(ConfigurationCondition condition, boolean queriedOnly, Executable... methods) {
        if (!ConfigurationCondition.alwaysTrue().equals(condition)) {
            System.err.println("Conditional specification in entry points configuration is not supported and is ignored");
        }

        if (queriedOnly) {
            System.err.println("Query specification in entry points configuration is not supported and is ignored");
        } else {
            Collections.addAll(entryPoints, methods);
        }
    }

    @Override
    public void register(ConfigurationCondition condition, boolean finalIsWritable, Field... fields) {
        System.err.println("The specification for fields in entry points configuration is not supported and is ignored.");
    }

    @Override
    public void registerClassLookup(ConfigurationCondition condition, String typeName) {

    }

    @Override
    public void registerFieldLookup(ConfigurationCondition condition, Class<?> declaringClass, String fieldName) {

    }

    @Override
    public void registerMethodLookup(ConfigurationCondition condition, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {

    }

    @Override
    public void registerConstructorLookup(ConfigurationCondition condition, Class<?> declaringClass, Class<?>... parameterTypes) {

    }
}
