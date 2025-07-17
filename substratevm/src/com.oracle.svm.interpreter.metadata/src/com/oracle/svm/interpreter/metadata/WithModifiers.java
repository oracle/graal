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
package com.oracle.svm.interpreter.metadata;

/**
 * Resolves conflict between jvmci and shared ModifiersProvider.
 */
public interface WithModifiers extends com.oracle.svm.espresso.shared.meta.ModifiersProvider, jdk.vm.ci.meta.ModifiersProvider {
    @Override
    int getModifiers();

    @Override
    default boolean isVarargs() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isVarargs();
    }

    @Override
    default boolean isInterface() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isInterface();
    }

    @Override
    default boolean isSynchronized() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isSynchronized();
    }

    @Override
    default boolean isStatic() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isStatic();
    }

    @Override
    default boolean isFinalFlagSet() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isFinalFlagSet();
    }

    @Override
    default boolean isPublic() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isPublic();
    }

    @Override
    default boolean isPackagePrivate() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isPackagePrivate();
    }

    @Override
    default boolean isPrivate() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isPrivate();
    }

    @Override
    default boolean isProtected() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isProtected();
    }

    @Override
    default boolean isTransient() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isTransient();
    }

    @Override
    default boolean isStrict() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isStrict();
    }

    @Override
    default boolean isVolatile() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isVolatile();
    }

    @Override
    default boolean isNative() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isNative();
    }

    @Override
    default boolean isAbstract() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isAbstract();
    }

    @Override
    default boolean isConcrete() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isConcrete();
    }

    @Override
    default boolean isEnum() {
        return com.oracle.svm.espresso.shared.meta.ModifiersProvider.super.isEnum();
    }
}
