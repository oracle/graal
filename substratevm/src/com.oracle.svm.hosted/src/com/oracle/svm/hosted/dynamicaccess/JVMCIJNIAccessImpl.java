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
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestInvoked;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.util.OriginalMethodProvider;
import com.oracle.svm.util.dynamicaccess.JVMCIAccessCondition;
import com.oracle.svm.util.dynamicaccess.JVMCIJNIAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class JVMCIJNIAccessImpl implements JVMCIJNIAccess {

    private JNIAccessImpl jniInstance;
    private static JVMCIJNIAccess instance;

    private JVMCIJNIAccessImpl() {
    }

    public static JVMCIJNIAccess singleton() {
        if (instance == null) {
            instance = new JVMCIJNIAccessImpl();
        }
        return instance;
    }

    private JNIAccessImpl jniInstance() {
        if (jniInstance == null) {
            jniInstance = JNIAccessImpl.singleton();
        }
        return jniInstance;
    }

    public void register(JavaConstant condition, ResolvedJavaType... types) {
        register(JVMCIAccessCondition.guestAccessCondition(condition), types);
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            jniInstance().register(condition, OriginalClassProvider.getJavaClass(type));
        }
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaMethod... methods) {
        for (ResolvedJavaMethod method : methods) {
            jniInstance().register(condition, OriginalMethodProvider.getJavaMethod(method));
        }
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaField... fields) {
        for (ResolvedJavaField field : fields) {
            jniInstance().register(condition, OriginalFieldProvider.getJavaField(field));
        }
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.JNIAccess#register(AccessCondition, Class[])}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param classes a {@link JavaConstant} representing the guest {@code Class<?>[]} to register
     */
    @GuestInvoked
    public void registerClasses(JavaConstant condition, JavaConstant classes) {
        register(condition, GuestAccess.get().asResolvedJavaTypes(classes));
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.JNIAccess#register(AccessCondition, java.lang.reflect.Executable[])}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param executables a {@link JavaConstant} representing the guest {@code Executable[]} to register
     */
    @GuestInvoked
    public void registerExecutables(JavaConstant condition, JavaConstant executables) {
        register(JVMCIAccessCondition.guestAccessCondition(condition), GuestAccess.get().asResolvedJavaMethods(executables));
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.JNIAccess#register(AccessCondition, java.lang.reflect.Field[])}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param fields a {@link JavaConstant} representing the guest {@code Field[]} to register
     */
    @GuestInvoked
    public void registerFields(JavaConstant condition, JavaConstant fields) {
        register(JVMCIAccessCondition.guestAccessCondition(condition), GuestAccess.get().asResolvedJavaFields(fields));
    }
}
