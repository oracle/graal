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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.dynamicaccess.JNIAccess;

public final class JNIAccessImpl implements JNIAccess {

    private final RuntimeJNIAccessSupport jniInstance;
    private static JNIAccessImpl instance;

    private JNIAccessImpl() {
        jniInstance = ImageSingletons.lookup(RuntimeJNIAccessSupport.class);
    }

    public static JNIAccessImpl singleton() {
        if (instance == null) {
            instance = new JNIAccessImpl();
        }
        return instance;
    }

    @Override
    public void register(AccessCondition condition, Class<?>... classes) {
        DynamicAccessSupport.printErrorIfSealedOrInvalidCondition(condition, "following classes for JNI access: " + Arrays.toString(classes));
        jniInstance.register(condition, classes);
    }

    @Override
    public void register(AccessCondition condition, Executable... methods) {
        DynamicAccessSupport.printErrorIfSealedOrInvalidCondition(condition, "following methods for JNI access: " + Arrays.toString(methods));
        jniInstance.register(condition, false, false, methods);
    }

    @Override
    public void register(AccessCondition condition, Field... fields) {
        DynamicAccessSupport.printErrorIfSealedOrInvalidCondition(condition, "following fields for JNI access: " + Arrays.toString(fields));
        jniInstance.register(condition, false, false, fields);
    }
}
