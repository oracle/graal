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

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.hosted.JNIAccessImpl;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.util.OriginalMethodProvider;
import com.oracle.svm.util.dynamicaccess.JVMCIJNIAccess;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class JVMCIJNIAccessImpl implements JVMCIJNIAccess {

    private final JNIAccessImpl jniInstance;
    private static JVMCIJNIAccess instance;

    private JVMCIJNIAccessImpl() {
        jniInstance = JNIAccessImpl.singleton();
    }

    public static JVMCIJNIAccess singleton() {
        if (instance == null) {
            instance = new JVMCIJNIAccessImpl();
        }
        return instance;
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            jniInstance.register(condition, OriginalClassProvider.getJavaClass(type));
        }
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaMethod... methods) {
        for (ResolvedJavaMethod method : methods) {
            jniInstance.register(condition, OriginalMethodProvider.getJavaMethod(method));
        }
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaField... fields) {
        for (ResolvedJavaField field : fields) {
            jniInstance.register(condition, OriginalFieldProvider.getJavaField(field));
        }
    }
}
