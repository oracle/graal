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
package com.oracle.svm.core.imagelayer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.LoadImageSingletonNode;

import jdk.vm.ci.meta.MetaAccessProvider;

public abstract class LoadImageSingletonFactory {

    public record SingletonAccessInfo(CGlobalDataInfo tableBase, int offset) {

    }

    /**
     * Provides compiler-relevant information about the value which will be loaded.
     */
    public interface LoadImageSingletonData {

        Class<?> getLoadType();

        SingletonAccessInfo getAccessInfo();
    }

    protected abstract LoadImageSingletonData getApplicationLayerOnlyImageSingletonInfo(Class<?> clazz);

    protected abstract LoadImageSingletonData getLayeredImageSingletonInfo(Class<?> clazz);

    public static LoadImageSingletonNode loadApplicationOnlyImageSingleton(Class<?> clazz, MetaAccessProvider metaAccess) {
        LoadImageSingletonData singletonInfo = ImageSingletons.lookup(LoadImageSingletonFactory.class).getApplicationLayerOnlyImageSingletonInfo(clazz);
        return LoadImageSingletonNode.createLoadImageSingleton(singletonInfo, metaAccess);
    }

    public static LoadImageSingletonNode loadLayeredImageSingleton(Class<?> clazz, MetaAccessProvider metaAccess) {
        LoadImageSingletonData singletonInfo = ImageSingletons.lookup(LoadImageSingletonFactory.class).getLayeredImageSingletonInfo(clazz);
        return LoadImageSingletonNode.createLoadImageSingleton(singletonInfo, metaAccess);
    }
}
