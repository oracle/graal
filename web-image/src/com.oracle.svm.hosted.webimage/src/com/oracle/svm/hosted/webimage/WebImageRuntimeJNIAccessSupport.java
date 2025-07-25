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

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;

/**
 * No-op implementation of {@link RuntimeJNIAccessSupport}.
 * <p>
 * Web Image does not support JNI and thus does not need any special support for handling
 * JNI-accessed types, fields, etc. The {@link RuntimeJNIAccessSupport} image singleton may be
 * accessed occasionally, so an implementation is still required.
 */
public class WebImageRuntimeJNIAccessSupport implements RuntimeJNIAccessSupport {
    @Override
    public void register(AccessCondition condition, boolean unsafeAllocated, boolean preserved, Class<?> clazz) {
        // Do nothing.
    }

    @Override
    public void register(AccessCondition condition, boolean queriedOnly, boolean preserved, Executable... methods) {
        // Do nothing.
    }

    @Override
    public void register(AccessCondition condition, boolean finalIsWritable, boolean preserved, Field... fields) {
        // Do nothing.
    }

    @Override
    public void registerClassLookup(AccessCondition condition, boolean preserved, String typeName) {
        // Do nothing.
    }

    @Override
    public void registerFieldLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, String fieldName) {
        // Do nothing.
    }

    @Override
    public void registerMethodLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        // Do nothing.
    }

    @Override
    public void registerConstructorLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, Class<?>... parameterTypes) {
        // Do nothing.
    }
}
