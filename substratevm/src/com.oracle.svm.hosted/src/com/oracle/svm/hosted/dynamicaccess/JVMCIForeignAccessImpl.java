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
package com.oracle.svm.hosted.dynamicaccess;

import java.lang.invoke.MethodHandle;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.hosted.ForeignAccessImpl;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestInvoked;
import com.oracle.svm.util.dynamicaccess.JVMCIAccessCondition;
import com.oracle.svm.util.dynamicaccess.JVMCIForeignAccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * JVMCI-backed implementation of {@link JVMCIForeignAccess}.
 */
public final class JVMCIForeignAccessImpl implements JVMCIForeignAccess {

    private ForeignAccessImpl foreignInstance;
    private static JVMCIForeignAccess instance;

    private JVMCIForeignAccessImpl() {
    }

    public static JVMCIForeignAccess singleton() {
        if (instance == null) {
            instance = new JVMCIForeignAccessImpl();
        }
        return instance;
    }

    private ForeignAccessImpl foreignInstance() {
        if (foreignInstance == null) {
            foreignInstance = ForeignAccessImpl.singleton();
        }
        return foreignInstance;
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.ForeignAccess#registerForDowncall(AccessCondition, Object, Object...)}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param desc a {@link JavaConstant} representing the guest
     *            {@link java.lang.foreign.FunctionDescriptor}
     * @param options a {@link JavaConstant} representing the guest {@code Object[]} of
     *            {@link java.lang.foreign.Linker.Option}s
     */
    @GuestInvoked
    public void registerForDowncall(JavaConstant condition, JavaConstant desc, JavaConstant options) {
        foreignInstance().registerForDowncall(JVMCIAccessCondition.guestAccessCondition(condition), asObject(desc), asObjects(options));
    }

    @Override
    public void registerForDowncall(AccessCondition condition, JavaConstant desc, JavaConstant... options) {
        foreignInstance().registerForDowncall(condition, asObject(desc), asObjects(options));
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.ForeignAccess#registerForUpcall(AccessCondition, Object, Object...)}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param desc a {@link JavaConstant} representing the guest
     *            {@link java.lang.foreign.FunctionDescriptor}
     * @param options a {@link JavaConstant} representing the guest {@code Object[]} of
     *            {@link java.lang.foreign.Linker.Option}s
     */
    @GuestInvoked
    public void registerForUpcall(JavaConstant condition, JavaConstant desc, JavaConstant options) {
        foreignInstance().registerForUpcall(JVMCIAccessCondition.guestAccessCondition(condition), asObject(desc), asObjects(options));
    }

    @Override
    public void registerForUpcall(AccessCondition condition, JavaConstant desc, JavaConstant... options) {
        foreignInstance().registerForUpcall(condition, asObject(desc), asObjects(options));
    }

    /**
     * Guest-invoked method for
     * {@link org.graalvm.nativeimage.dynamicaccess.ForeignAccess#registerForDirectUpcall(AccessCondition, MethodHandle, Object, Object...)}.
     *
     * @param condition a {@link JavaConstant} representing the guest {@link AccessCondition}
     * @param target a {@link JavaConstant} representing the guest {@link MethodHandle}
     * @param desc a {@link JavaConstant} representing the guest
     *            {@link java.lang.foreign.FunctionDescriptor}
     * @param options a {@link JavaConstant} representing the guest {@code Object[]} of
     *            {@link java.lang.foreign.Linker.Option}s
     */
    @GuestInvoked
    public void registerForDirectUpcall(JavaConstant condition, JavaConstant target, JavaConstant desc, JavaConstant options) {
        foreignInstance().registerForDirectUpcall(JVMCIAccessCondition.guestAccessCondition(condition), asObject(MethodHandle.class, target), asObject(desc), asObjects(options));
    }

    @Override
    public void registerForDirectUpcall(AccessCondition condition, JavaConstant target, JavaConstant desc, JavaConstant... options) {
        foreignInstance().registerForDirectUpcall(condition, asObject(MethodHandle.class, target), asObject(desc), asObjects(options));
    }

    private static Object asObject(JavaConstant constant) {
        return asObject(Object.class, constant);
    }

    private static <T> T asObject(Class<T> type, JavaConstant constant) {
        // This operation is not implemented yet (GR-78760).
        return constant == null || constant.isNull() ? null : GuestAccess.get().getSnippetReflection().asObject(type, constant);
    }

    private static Object[] asObjects(JavaConstant array) {
        return asObject(Object[].class, array);
    }

    private static Object[] asObjects(JavaConstant[] constants) {
        if (constants == null) {
            return null;
        }
        Object[] objects = new Object[constants.length];
        for (int i = 0; i < constants.length; i++) {
            objects[i] = asObject(constants[i]);
        }
        return objects;
    }
}
