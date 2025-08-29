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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.imagelayer.LoadImageSingletonFeature.getCrossLayerSingletonMappingInfo;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.imagelayer.LoadImageSingletonFactory;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CGlobalDataFeature;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.vm.ci.meta.MetaAccessProvider;

class LoadImageSingletonDataImpl implements LoadImageSingletonFactory.LoadImageSingletonData {

    private final Class<?> key;
    private final SlotRecordKind kind;
    private boolean applicationLayerConstant;

    LoadImageSingletonDataImpl(Class<?> key, SlotRecordKind kind) {
        this.key = key;
        this.kind = kind;
    }

    public Class<?> getKey() {
        return key;
    }

    public SlotRecordKind getKind() {
        return kind;
    }

    @Override
    public Class<?> getLoadType() {
        return kind == SlotRecordKind.APPLICATION_LAYER_SINGLETON ? key : key.arrayType();
    }

    @Override
    public LoadImageSingletonFactory.SingletonAccessInfo getAccessInfo() {
        VMError.guarantee(!applicationLayerConstant, "this node should instead be constant folded");
        CrossLayerSingletonMappingInfo singleton = getCrossLayerSingletonMappingInfo();
        assert singleton.singletonTableStart != null;
        CGlobalDataInfo cglobal = CGlobalDataFeature.singleton().registerAsAccessedOrGet(singleton.singletonTableStart);
        int slotNum = singleton.currentKeyToSlotInfoMap.get(key).slotNum();
        return new LoadImageSingletonFactory.SingletonAccessInfo(cglobal, slotNum * singleton.referenceSize);
    }

    void setApplicationLayerConstant() {
        applicationLayerConstant = true;
    }

    @Override
    public boolean isApplicationLayerConstant() {
        return applicationLayerConstant;
    }

    @Override
    public ConstantNode asApplicationLayerConstant(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflectionProvider) {
        VMError.guarantee(isApplicationLayerConstant());
        return switch (kind) {
            case APPLICATION_LAYER_SINGLETON -> {
                Object singleton = LayeredImageSingletonSupport.singleton().lookup(key, true, false);
                yield ConstantNode.forConstant(snippetReflectionProvider.forObject(singleton), metaAccess);
            }
            case MULTI_LAYERED_SINGLETON -> {
                var multiLayerArray = ImageSingletons.lookup(LoadImageSingletonFeature.class).getMultiLayerConstant(key, metaAccess, snippetReflectionProvider);
                yield ConstantNode.forConstant(multiLayerArray, 1, true, metaAccess);
            }
        };
    }
}
