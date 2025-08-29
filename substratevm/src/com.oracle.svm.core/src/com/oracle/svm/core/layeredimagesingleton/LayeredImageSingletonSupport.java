/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimagesingleton;

import java.util.Collection;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;

import jdk.vm.ci.meta.JavaConstant;

@Platforms(Platform.HOSTED_ONLY.class)
public interface LayeredImageSingletonSupport {

    static LayeredImageSingletonSupport singleton() {
        return ImageSingletons.lookup(LayeredImageSingletonSupport.class);
    }

    /**
     * This method is intended to be used in special situations during the building process to
     * access singletons which (1) are only allowed to be accessed at runtime
     * ({@link LayeredImageSingletonBuilderFlags#RUNTIME_ACCESS}) and/or (2) implement
     * {@link MultiLayeredImageSingleton}.
     */
    <T> T lookup(Class<T> key, boolean accessRuntimeOnly, boolean accessMultiLayer);

    Collection<Class<?>> getMultiLayeredImageSingletonKeys();

    Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind kind);

    void forbidNewTraitInstallations(SingletonLayeredInstallationKind.InstallationKind kind);

    void freezeLayeredImageSingletonMetadata();

    JavaConstant getInitialLayerOnlyImageSingleton(Class<?> key);

    /**
     * @return trait associated with this key if it exists, or else {@code null}.
     */
    SingletonTrait getTraitForUninstalledSingleton(Class<?> key, SingletonTraitKind kind);
}
